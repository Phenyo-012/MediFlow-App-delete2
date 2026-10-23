/**
 * Covers classification validation, permission checks, transaction behavior, and audit outcomes.
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
  createMedicineClassificationAdminService,
} = require(
    "../lib/medicineClassificationAdminService",
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
    createMedicineClassificationAdminService({
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
    "requires MANAGE_ABC_VED before mutation",
    async () => {
      const firestore =
        createFakeFirestore();


      const authorizationCalls =
        [];


      const service =
        createMedicineClassificationAdminService({
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
            service
              .setMedicineClassification(
                  "user-1",
                  "medicine-1",
                  "A",
                  "V",
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
                "MANAGE_ABC_VED",
            },
          ],
      );


      assert.equal(
          firestore
            .getTransactionCalls(),
          0,
      );
    },
);


test(
    "creates a new authoritative classification",
    async () => {
      const harness =
        createHarness({
          "medicines/medicine-1": {
            medicineId:
              "medicine-1",

            isActive:
              true,
          },
        });


      const result =
        await harness.service
          .setMedicineClassification(
              "user-1",
              "medicine-1",
              "a",
              "v",
          );


      assert.deepEqual(
          result,
          {
            changed:
              true,

            created:
              true,

            medicineId:
              "medicine-1",

            abcClassification:
              "A",

            vedClassification:
              "V",
          },
      );


      assert.deepEqual(
          harness.documents.get(
              "medicineClassifications/medicine-1",
          ),
          {
            medicineId:
              "medicine-1",

            abcClassification:
              "A",

            vedClassification:
              "V",

            updatedAt:
              SERVER_TIMESTAMP,
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
          AUDIT_ACTIONS.ABC_VED_CREATED,
      );


      assert.equal(
          audit.input.entityType,
          "MEDICINE_CLASSIFICATION",
      );


      assert.equal(
          audit.input.entityId,
          "medicine-1",
      );


      assert.equal(
          audit.input.performedBy,
          "user-1",
      );


      assert.equal(
          audit.input.details
            .previousAbcClassification,
          null,
      );


      assert.equal(
          audit.input.details
            .previousVedClassification,
          null,
      );
    },
);


test(
    "updates an existing classification",
    async () => {
      const harness =
        createHarness({
          "medicines/medicine-1": {
            medicineId:
              "medicine-1",

            isActive:
              true,
          },

          "medicineClassifications/medicine-1": {
            medicineId:
              "medicine-1",

            abcClassification:
              "B",

            vedClassification:
              "E",

            updatedAt:
              "OLD_TIMESTAMP",
          },
        });


      const result =
        await harness.service
          .setMedicineClassification(
              "user-1",
              "medicine-1",
              "A",
              "D",
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
            "medicineClassifications/medicine-1",
        );


      assert.equal(
          stored.abcClassification,
          "A",
      );


      assert.equal(
          stored.vedClassification,
          "D",
      );


      assert.equal(
          stored.updatedAt,
          SERVER_TIMESTAMP,
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
          audit.input.actionType,
          AUDIT_ACTIONS.ABC_VED_UPDATED,
      );


      assert.equal(
          audit.input.details
            .previousAbcClassification,
          "B",
      );


      assert.equal(
          audit.input.details
            .previousVedClassification,
          "E",
      );


      assert.equal(
          audit.input.details
            .abcClassification,
          "A",
      );


      assert.equal(
          audit.input.details
            .vedClassification,
          "D",
      );
    },
);


test(
    "does not write or audit an idempotent mutation",
    async () => {
      const harness =
        createHarness({
          "medicines/medicine-1": {
            medicineId:
              "medicine-1",

            isActive:
              true,
          },

          "medicineClassifications/medicine-1": {
            medicineId:
              "medicine-1",

            abcClassification:
              "A",

            vedClassification:
              "V",

            updatedAt:
              "EXISTING_TIMESTAMP",
          },
        });


      const result =
        await harness.service
          .setMedicineClassification(
              "user-1",
              "medicine-1",
              "A",
              "V",
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


      assert.equal(
          harness.documents
            .get(
                "medicineClassifications/medicine-1",
            )
            .updatedAt,
          "EXISTING_TIMESTAMP",
      );
    },
);


test(
    "rejects classification changes for inactive medicine",
    async () => {
      const harness =
        createHarness({
          "medicines/medicine-1": {
            medicineId:
              "medicine-1",

            isActive:
              false,
          },
        });


      await expectApplicationError(
          () =>
            harness.service
              .setMedicineClassification(
                  "user-1",
                  "medicine-1",
                  "A",
                  "V",
              ),
          "MEDICINE_INACTIVE",
          409,
      );


      assert.equal(
          harness.committedWrites.length,
          0,
      );


      assert.equal(
          harness.auditCalls.length,
          0,
      );
    },
);


test(
    "rejects an invalid ABC classification",
    async () => {
      const harness =
        createHarness();


      await expectApplicationError(
          () =>
            harness.service
              .setMedicineClassification(
                  "user-1",
                  "medicine-1",
                  "X",
                  "V",
              ),
          "INVALID_ABC_CLASSIFICATION",
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
    "rejects an invalid VED classification",
    async () => {
      const harness =
        createHarness();


      await expectApplicationError(
          () =>
            harness.service
              .setMedicineClassification(
                  "user-1",
                  "medicine-1",
                  "A",
                  "X",
              ),
          "INVALID_VED_CLASSIFICATION",
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
    "rejects a medicine that does not exist",
    async () => {
      const harness =
        createHarness();


      await expectApplicationError(
          () =>
            harness.service
              .setMedicineClassification(
                  "user-1",
                  "medicine-1",
                  "A",
                  "V",
              ),
          "MEDICINE_NOT_FOUND",
          404,
      );


      assert.equal(
          harness.committedWrites.length,
          0,
      );


      assert.equal(
          harness.auditCalls.length,
          0,
      );
    },
);