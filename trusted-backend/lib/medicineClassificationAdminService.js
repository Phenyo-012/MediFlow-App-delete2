/**
 * Trusted backend service for permission-checked ABC/VED classification updates and audit logging.
 */

const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  requireApplicationPermission,
  ApplicationHttpError,
} = require("./applicationAuthorization");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");


const MANAGE_ABC_VED =
  "MANAGE_ABC_VED";


const VALID_ABC =
  new Set([
    "A",
    "B",
    "C",
  ]);


const VALID_VED =
  new Set([
    "V",
    "E",
    "D",
  ]);


/**
 * Creates an ABC/VED mutation service with injectable authorization, audit, and timestamp
 * dependencies. Production callers use server authorization and Firestore audit writing; the
 * injected providers let tests verify those boundaries and transaction ordering.
 *
 * @param {object} overrides optional trusted dependency providers
 * @return {{setClassification: Function}} classification mutation API
 */
function createMedicineClassificationAdminService(
    overrides = {},
) {
  const authorize =
    overrides.requireApplicationPermission ||
    requireApplicationPermission;


  const auditWriter =
    overrides.writeAuditRecord ||
    writeAuditRecord;


  const serverTimestamp =
    overrides.serverTimestamp ||
    (() => FieldValue.serverTimestamp());



  /**
   * Updates one active medicine's ABC/VED pair after requiring MANAGE_ABC_VED and validating both
   * codes. A Firestore transaction reads the medicine and any existing classification before
   * writing; unchanged pairs return without an audit write, while changes and their audit record
   * commit together.
   *
   * @param {string} authenticatedUid verified actor UID
   * @param {string} medicineId target shared medicine ID
   * @param {string} abcClassification requested A, B, or C class
   * @param {string} vedClassification requested V, E, or D class
   * @return {Promise<object>} whether the pair changed and its resulting record metadata
   */
  async function setClassification(
      authenticatedUid,
      medicineId,
      abcClassification,
      vedClassification,
  ) {
    const context =
      await authorize(
          authenticatedUid,
          MANAGE_ABC_VED,
      );


    const cleanMedicineId =
      requireMedicineId(
          medicineId,
      );


    const cleanAbc =
      requireAbcClassification(
          abcClassification,
      );


    const cleanVed =
      requireVedClassification(
          vedClassification,
      );


    const medicineRef =
      context.db
        .collection(
            "medicines",
        )
        .doc(
            cleanMedicineId,
        );


    const classificationRef =
      context.db
        .collection(
            "medicineClassifications",
        )
        .doc(
            cleanMedicineId,
        );


    const auditId =
      context.db
        .collection(
            "auditLogs",
        )
        .doc()
        .id;


    return context.db.runTransaction(
        async (transaction) => {

          const medicineSnapshot =
            await transaction.get(
                medicineRef,
            );


          const classificationSnapshot =
            await transaction.get(
                classificationRef,
            );


          if (!medicineSnapshot.exists) {
            throw new ApplicationHttpError(
                404,
                "MEDICINE_NOT_FOUND",
                "The medicine could not be found.",
            );
          }


          const medicine =
            medicineSnapshot.data() || {};


          if (
            typeof medicine.medicineId !==
              "string" ||
            medicine.medicineId.trim() !==
              cleanMedicineId
          ) {
            throw new ApplicationHttpError(
                409,
                "MEDICINE_ID_MISMATCH",
                "The medicine record is inconsistent.",
            );
          }


          if (medicine.isActive !== true) {
            throw new ApplicationHttpError(
                409,
                "MEDICINE_INACTIVE",
                "ABC-VED classification cannot be " +
                  "changed for an inactive medicine.",
            );
          }


          const existing =
            classificationSnapshot.exists
              ? classificationSnapshot.data() || {}
              : null;


          if (
            existing !== null &&
            typeof existing.medicineId ===
              "string" &&
            existing.medicineId.trim() !==
              cleanMedicineId
          ) {
            throw new ApplicationHttpError(
                409,
                "CLASSIFICATION_MEDICINE_MISMATCH",
                "The existing ABC-VED classification " +
                  "is inconsistent.",
            );
          }


          const previousAbc =
            existing === null
              ? null
              : normalizeNullableText(
                  existing.abcClassification,
              );


          const previousVed =
            existing === null
              ? null
              : normalizeNullableText(
                  existing.vedClassification,
              );



          if (
            existing !== null &&
            previousAbc === cleanAbc &&
            previousVed === cleanVed
          ) {
            return {
              changed:
                false,

              created:
                false,

              medicineId:
                cleanMedicineId,

              abcClassification:
                cleanAbc,

              vedClassification:
                cleanVed,
            };
          }


          const created =
            existing === null;


          const classificationData = {
            medicineId:
              cleanMedicineId,

            abcClassification:
              cleanAbc,

            vedClassification:
              cleanVed,

            updatedAt:
              serverTimestamp(),
          };


          if (created) {
            transaction.create(
                classificationRef,
                classificationData,
            );
          } else {
            transaction.update(
                classificationRef,
                classificationData,
            );
          }



          auditWriter(
              transaction,
              context.db,
              {
                auditId,

                actionType:
                  created
                    ? AUDIT_ACTIONS
                      .ABC_VED_CREATED
                    : AUDIT_ACTIONS
                      .ABC_VED_UPDATED,

                entityType:
                  "MEDICINE_CLASSIFICATION",

                entityId:
                  cleanMedicineId,

                performedBy:
                  authenticatedUid,

                details: {
                  medicineId:
                    cleanMedicineId,

                  previousAbcClassification:
                    previousAbc,

                  previousVedClassification:
                    previousVed,

                  abcClassification:
                    cleanAbc,

                  vedClassification:
                    cleanVed,
                },
              },
          );


          return {
            changed:
              true,

            created,

            medicineId:
              cleanMedicineId,

            abcClassification:
              cleanAbc,

            vedClassification:
              cleanVed,
          };
        },
    );
  }


  return {
    setMedicineClassification:
      setClassification,
  };
}


const defaultService =
  createMedicineClassificationAdminService();


async function setMedicineClassification(
    authenticatedUid,
    medicineId,
    abcClassification,
    vedClassification,
) {
  return defaultService
    .setMedicineClassification(
        authenticatedUid,
        medicineId,
        abcClassification,
        vedClassification,
    );
}


function requireMedicineId(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new ApplicationHttpError(
        400,
        "MEDICINE_ID_REQUIRED",
        "A medicine is required.",
    );
  }


  return value.trim();
}


function requireAbcClassification(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new ApplicationHttpError(
        400,
        "ABC_CLASSIFICATION_REQUIRED",
        "An ABC classification is required.",
    );
  }


  const normalized =
    value
      .trim()
      .toUpperCase();


  if (!VALID_ABC.has(
      normalized,
  )) {
    throw new ApplicationHttpError(
        400,
        "INVALID_ABC_CLASSIFICATION",
        "ABC classification must be A, B or C.",
    );
  }


  return normalized;
}


function requireVedClassification(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new ApplicationHttpError(
        400,
        "VED_CLASSIFICATION_REQUIRED",
        "A VED classification is required.",
    );
  }


  const normalized =
    value
      .trim()
      .toUpperCase();


  if (!VALID_VED.has(
      normalized,
  )) {
    throw new ApplicationHttpError(
        400,
        "INVALID_VED_CLASSIFICATION",
        "VED classification must be V, E or D.",
    );
  }


  return normalized;
}


function normalizeNullableText(
    value,
) {
  if (typeof value !==
      "string") {
    return null;
  }


  const normalized =
    value.trim();


  return normalized === ""
    ? null
    : normalized;
}


module.exports = {
  setMedicineClassification,
  createMedicineClassificationAdminService,
};