/**
 * Covers setting validation, legacy field normalization, authorization, and transactional audit behavior.
 */

const test =
  require("node:test");

const assert =
  require("node:assert/strict");

const {
  ApplicationHttpError,
} = require(
    "../lib/applicationAuthorization",
);

const {
  createInventorySettingsAdminService,
} = require(
    "../lib/inventorySettingsAdminService",
);

const {
  AUDIT_ACTIONS,
} = require(
    "../lib/audit",
);


const SERVER_TIMESTAMP =
  "TEST_SERVER_TIMESTAMP";



function createFakeFirestore(
    seed = {},
) {
  const documents =
    new Map(
        Object.entries(
            seed,
        ),
    );


  let generatedId =
    0;


  let transactionCalls =
    0;


  let lastTransaction =
    null;


  const committedWrites =
    [];


  const db = {
    collection(
        name,
    ) {
      return {
        doc(
            suppliedId,
        ) {
          const id =
            suppliedId ||
            `generated-${++generatedId}`;


          return {
            id,

            path:
              `${name}/${id}`,
          };
        },
      };
    },


    async runTransaction(
        operation,
    ) {
      transactionCalls++;


      const pendingWrites =
        [];


      const transaction = {
        async get(
            reference,
        ) {
          const exists =
            documents.has(
                reference.path,
            );


          return {
            exists,

            id:
              reference.id,

            data() {
              return exists
                ? documents.get(
                    reference.path,
                )
                : undefined;
            },
          };
        },


        create(
            reference,
            data,
        ) {
          pendingWrites.push({
            type:
              "create",

            reference,

            data,
          });
        },


        update(
            reference,
            data,
        ) {
          pendingWrites.push({
            type:
              "update",

            reference,

            data,
          });
        },


        set(
            reference,
            data,
        ) {
          pendingWrites.push({
            type:
              "set",

            reference,

            data,
          });
        },
      };


      lastTransaction =
        transaction;


      const result =
        await operation(
            transaction,
        );


      for (const write of
        pendingWrites) {
        const path =
          write.reference.path;


        if (write.type ===
            "create") {
          if (documents.has(
              path,
          )) {
            throw new Error(
                "Document already exists.",
            );
          }


          documents.set(
              path,
              {
                ...write.data,
              },
          );
        } else if (
          write.type ===
          "update"
        ) {
          if (!documents.has(
              path,
          )) {
            throw new Error(
                "Document does not exist.",
            );
          }


          documents.set(
              path,
              {
                ...documents.get(
                    path,
                ),
                ...write.data,
              },
          );
        } else {
          documents.set(
              path,
              {
                ...write.data,
              },
          );
        }
      }


      committedWrites.push(
          ...pendingWrites,
      );


      return result;
    },
  };


  return {
    db,

    documents,

    committedWrites,


    getTransactionCalls() {
      return transactionCalls;
    },


    getLastTransaction() {
      return lastTransaction;
    },
  };
}



function createHarness(
    seed = {},
) {
  const firestore =
    createFakeFirestore(
        seed,
    );


  const authorizationCalls =
    [];


  const auditCalls =
    [];


  const service =
    createInventorySettingsAdminService({
      requireApplicationPermission:
        async (
            uid,
            permission,
        ) => {
          authorizationCalls.push({
            uid,
            permission,
          });


          return {
            db:
              firestore.db,

            uid,

            profile: {
              uid,

              isActive:
                true,

              permissions: [
                permission,
              ],
            },

            permissions: [
              permission,
            ],
          };
        },


      writeAuditRecord(
          transaction,
          db,
          input,
      ) {
        auditCalls.push({
          transaction,
          db,
          input,
        });
      },


      serverTimestamp:
        () => SERVER_TIMESTAMP,
    });


  return {
    ...firestore,

    service,

    authorizationCalls,

    auditCalls,
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
    "requires MANAGE_SETTINGS before mutation",
    async () => {
      const firestore =
        createFakeFirestore();


      const authorizationCalls =
        [];


      const service =
        createInventorySettingsAdminService({
          requireApplicationPermission:
            async (
                uid,
                permission,
            ) => {
              authorizationCalls.push({
                uid,
                permission,
              });


              throw new ApplicationHttpError(
                  403,
                  "PERMISSION_DENIED",
                  "Denied.",
              );
            },

          writeAuditRecord() {
            throw new Error(
                "Audit must not run.",
            );
          },

          serverTimestamp:
            () => SERVER_TIMESTAMP,
        });


      await expectApplicationError(
          () =>
            service.setInventorySettings(
                "user-1",
                30,
            ),
          "PERMISSION_DENIED",
          403,
      );


      assert.deepEqual(
          authorizationCalls,
          [
            {
              uid:
                "user-1",

              permission:
                "MANAGE_SETTINGS",
            },
          ],
      );


      assert.equal(
          firestore.getTransactionCalls(),
          0,
      );
    },
);

test(
    "creates authoritative inventory settings",
    async () => {
      const harness =
        createHarness();


      const result =
        await harness.service
          .setInventorySettings(
              "user-1",
              30,
          );


      assert.deepEqual(
          result,
          {
            changed:
              true,

            created:
              true,

            settingId:
              "inventory",

            nearExpiryWarningDays:
              30,
          },
      );


      assert.deepEqual(
          harness.documents.get(
              "settings/inventory",
          ),
          {
            nearExpiryWarningDays:
              30,

            updatedAt:
              SERVER_TIMESTAMP,

            updatedBy:
              "user-1",
          },
      );


      assert.equal(
          harness.auditCalls.length,
          1,
      );


      const audit =
        harness.auditCalls[0];


      assert.equal(
          audit.transaction,
          harness.getLastTransaction(),
      );


      assert.equal(
          audit.db,
          harness.db,
      );


      assert.equal(
          audit.input.actionType,
          AUDIT_ACTIONS.SETTING_CHANGED,
      );


      assert.equal(
          audit.input.entityType,
          "SYSTEM_SETTING",
      );


      assert.equal(
          audit.input.entityId,
          "inventory",
      );


      assert.equal(
          audit.input.performedBy,
          "user-1",
      );


      assert.equal(
          audit.input.details.previousValue,
          null,
      );


      assert.equal(
          audit.input.details.newValue,
          30,
      );
    },
);

test(
    "updates existing inventory settings",
    async () => {
      const harness =
        createHarness({
          "settings/inventory": {
            nearExpiryWarningDays:
              90,

            updatedAt:
              "OLD_TIMESTAMP",

            updatedBy:
              "old-user",
          },
        });


      const result =
        await harness.service
          .setInventorySettings(
              "user-1",
              30,
          );


      assert.equal(
          result.changed,
          true,
      );


      assert.equal(
          result.created,
          false,
      );


      const stored =
        harness.documents.get(
            "settings/inventory",
        );


      assert.equal(
          stored.nearExpiryWarningDays,
          30,
      );


      assert.equal(
          stored.updatedAt,
          SERVER_TIMESTAMP,
      );


      assert.equal(
          stored.updatedBy,
          "user-1",
      );


      assert.equal(
          harness.auditCalls.length,
          1,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .previousValue,
          90,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .newValue,
          30,
      );
    },
);

test(
    "does not write or audit an idempotent mutation",
    async () => {
      const harness =
        createHarness({
          "settings/inventory": {
            nearExpiryWarningDays:
              90,

            updatedAt:
              "EXISTING_TIMESTAMP",

            updatedBy:
              "old-user",
          },
        });


      const result =
        await harness.service
          .setInventorySettings(
              "user-1",
              90,
          );


      assert.equal(
          result.changed,
          false,
      );


      assert.equal(
          result.created,
          false,
      );


      assert.equal(
          harness.committedWrites.length,
          0,
      );


      assert.equal(
          harness.auditCalls.length,
          0,
      );


      const stored =
        harness.documents.get(
            "settings/inventory",
        );


      assert.equal(
          stored.updatedAt,
          "EXISTING_TIMESTAMP",
      );


      assert.equal(
          stored.updatedBy,
          "old-user",
      );
    },
);

test(
    "rejects a negative warning period",
    async () => {
      const harness =
        createHarness();


      await expectApplicationError(
          () =>
            harness.service
              .setInventorySettings(
                  "user-1",
                  -1,
              ),
          "INVALID_NEAR_EXPIRY_WARNING_DAYS",
          400,
      );


      assert.equal(
          harness.getTransactionCalls(),
          0,
      );


      assert.equal(
          harness.auditCalls.length,
          0,
      );
    },
);

test(
    "rejects a non-integer warning period",
    async () => {
      const harness =
        createHarness();


      await expectApplicationError(
          () =>
            harness.service
              .setInventorySettings(
                  "user-1",
                  30.5,
              ),
          "INVALID_NEAR_EXPIRY_WARNING_DAYS",
          400,
      );


      assert.equal(
          harness.getTransactionCalls(),
          0,
      );


      assert.equal(
          harness.auditCalls.length,
          0,
      );
    },
);

test(
    "repairs an inconsistent stored setting",
    async () => {
      const harness =
        createHarness({
          "settings/inventory": {
            nearExpiryWarningDays:
              "45",

            updatedAt:
              "OLD_TIMESTAMP",

            updatedBy:
              "old-user",
          },
        });


      const result =
        await harness.service
          .setInventorySettings(
              "user-1",
              45,
          );


      assert.equal(
          result.changed,
          true,
      );


      assert.equal(
          result.created,
          false,
      );


      const stored =
        harness.documents.get(
            "settings/inventory",
        );


      assert.equal(
          stored.nearExpiryWarningDays,
          45,
      );


      assert.equal(
          stored.updatedAt,
          SERVER_TIMESTAMP,
      );


      assert.equal(
          stored.updatedBy,
          "user-1",
      );


      assert.equal(
          harness.auditCalls.length,
          1,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .actionType,
          AUDIT_ACTIONS.SETTING_CHANGED,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .previousValue,
          null,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .previousValueValid,
          false,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .newValue,
          45,
      );
    },
);

test(
    "repairs legacy misspelled field even when canonical value matches",
    async () => {
      const harness =
        createHarness({
          "settings/inventory": {
            nearExpiryWarningDays:
              40,

            nearexpiryWarningDays:
              90,

            updatedAt:
              "OLD_TIMESTAMP",

            updatedBy:
              "old-user",
          },
        });


      const result =
        await harness.service
          .setInventorySettings(
              "user-1",
              40,
          );


      assert.equal(
          result.changed,
          true,
      );


      assert.equal(
          result.created,
          false,
      );



      assert.equal(
          harness.committedWrites.length,
          1,
      );


      assert.equal(
          harness.committedWrites[0].type,
          "update",
      );


      assert.equal(
          Object.prototype.hasOwnProperty.call(
              harness.committedWrites[0].data,
              "nearexpiryWarningDays",
          ),
          true,
      );


      assert.equal(
          harness.auditCalls.length,
          1,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .previousValue,
          40,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .newValue,
          40,
      );


      assert.equal(
          harness.auditCalls[0]
            .input
            .details
            .legacyFieldRemoved,
          true,
      );
    },
);