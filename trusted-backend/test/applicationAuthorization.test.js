/**
 * Covers active-profile and explicit-permission authorization, including inactive and malformed profiles.
 */

const test =
  require("node:test");

const assert =
  require("node:assert/strict");

const {
  ApplicationHttpError,
  createApplicationAuthorization,
} = require(
    "../lib/applicationAuthorization",
);



function createFakeFirestore(
    options,
) {
  const exists =
    options.exists !== false;

  const profile =
    options.profile || {};


  return {
    collection(
        collectionName,
    ) {
      if (collectionName !== "users") {
        throw new Error(
            "Unexpected collection.",
        );
      }


      return {
        doc(
            uid,
        ) {
          return {
            async get() {
              return {
                exists,

                data() {
                  return profile;
                },

                id:
                  uid,
              };
            },
          };
        },
      };
    },
  };
}



async function expectApplicationError(
    operation,
    expectedCode,
    expectedStatus,
) {
  await assert.rejects(
      operation,
      (error) => {
        assert.ok(
            error instanceof
              ApplicationHttpError,
        );


        assert.equal(
            error.code,
            expectedCode,
        );


        assert.equal(
            error.statusCode,
            expectedStatus,
        );


        return true;
      },
  );
}


test(
    "allows an active user with the required permission",
    async () => {
      const db =
        createFakeFirestore({
          profile: {
            uid:
              "user-1",

            isActive:
              true,

            permissions: [
              " VIEW_ABC_VED ",
              "MANAGE_ABC_VED",
              "",
              null,
            ],
          },
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      const context =
        await authorization
          .requireApplicationPermission(
              "user-1",
              "MANAGE_ABC_VED",
          );


      assert.equal(
          context.uid,
          "user-1",
      );


      assert.equal(
          context.db,
          db,
      );


      assert.deepEqual(
          context.permissions,
          [
            "VIEW_ABC_VED",
            "MANAGE_ABC_VED",
          ],
      );
    },
);


test(
    "rejects a caller whose profile does not exist",
    async () => {
      const db =
        createFakeFirestore({
          exists:
            false,
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "user-1",
                  "MANAGE_ABC_VED",
              ),
          "USER_PROFILE_NOT_FOUND",
          403,
      );
    },
);


test(
    "rejects an inactive caller",
    async () => {
      const db =
        createFakeFirestore({
          profile: {
            uid:
              "user-1",

            isActive:
              false,

            permissions: [
              "MANAGE_ABC_VED",
            ],
          },
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "user-1",
                  "MANAGE_ABC_VED",
              ),
          "ACCOUNT_INACTIVE",
          403,
      );
    },
);


test(
    "rejects a caller without the required permission",
    async () => {
      const db =
        createFakeFirestore({
          profile: {
            uid:
              "user-1",

            isActive:
              true,

            permissions: [
              "VIEW_ABC_VED",
            ],
          },
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "user-1",
                  "MANAGE_ABC_VED",
              ),
          "PERMISSION_DENIED",
          403,
      );
    },
);


test(
    "rejects a profile whose stored UID does not match",
    async () => {
      const db =
        createFakeFirestore({
          profile: {
            uid:
              "different-user",

            isActive:
              true,

            permissions: [
              "MANAGE_ABC_VED",
            ],
          },
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "user-1",
                  "MANAGE_ABC_VED",
              ),
          "USER_PROFILE_INVALID",
          403,
      );
    },
);


test(
    "rejects an empty authenticated UID",
    async () => {
      const db =
        createFakeFirestore({
          profile: {},
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "   ",
                  "MANAGE_ABC_VED",
              ),
          "INVALID_REQUEST",
          400,
      );
    },
);


test(
    "rejects an empty required permission",
    async () => {
      const db =
        createFakeFirestore({
          profile: {},
        });


      const authorization =
        createApplicationAuthorization(
            () => db,
        );


      await expectApplicationError(
          () =>
            authorization
              .requireApplicationPermission(
                  "user-1",
                  "   ",
              ),
          "INVALID_REQUEST",
          400,
      );
    },
);

test("active account check allows users without a specific permission", async () => {
  const db = createFakeFirestore({
    profile: {uid: "user-1", isActive: true, permissions: []},
  });
  const authorization = createApplicationAuthorization(() => db);
  const context = await authorization.requireActiveUser("user-1");
  assert.equal(context.uid, "user-1");
  assert.deepEqual(context.permissions, []);
});

test("active account check rejects missing, inactive and mismatched profiles", async () => {
  for (const [options, code] of [
    [{exists: false}, "USER_PROFILE_NOT_FOUND"],
    [{profile: {uid: "user-1", isActive: false}}, "ACCOUNT_INACTIVE"],
    [{profile: {uid: "someone-else", isActive: true}}, "USER_PROFILE_INVALID"],
  ]) {
    const authorization = createApplicationAuthorization(
        () => createFakeFirestore(options));
    await expectApplicationError(() => authorization.requireActiveUser("user-1"),
        code, 403);
  }
});
