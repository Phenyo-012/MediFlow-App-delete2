/**
 * Builds audit records for important actions against shared inventory and user
 * data.
 *
 * The actor, action and affected record need to remain meaningful when an
 * operation is retried.
 */
const {
  FieldValue,
} = require("firebase-admin/firestore");


const AUDIT_ACTIONS =
  Object.freeze({
    USER_CREATED:
      "USER_CREATED",

    USER_DEACTIVATED:
      "USER_DEACTIVATED",

    USER_REACTIVATED:
      "USER_REACTIVATED",

    ROLE_CHANGED:
      "ROLE_CHANGED",

    PERMISSION_GRANTED:
      "PERMISSION_GRANTED",

    PERMISSION_REVOKED:
      "PERMISSION_REVOKED",

    MEDICINE_CREATED:
      "MEDICINE_CREATED",

    MEDICINE_UPDATED:
      "MEDICINE_UPDATED",

    MEDICINE_DEACTIVATED:
      "MEDICINE_DEACTIVATED",

    ABC_VED_CREATED:
      "ABC_VED_CREATED",

    ABC_VED_UPDATED:
      "ABC_VED_UPDATED",

    FEFO_OVERRIDE:
      "FEFO_OVERRIDE",

    CORRECTION_CREATED:
      "CORRECTION_CREATED",

    SETTING_CHANGED:
      "SETTING_CHANGED",

    SECURITY_RELEVANT_REJECTION:
      "SECURITY_RELEVANT_REJECTION",
  });


/**
 * Writes one authoritative audit record inside an
 * existing trusted Firestore transaction.
 *
 * The caller supplies a deterministic auditId so
 * retries of the same logical action cannot create
 * duplicate audit records.
 *
 * @param {FirebaseFirestore.Transaction} transaction
 * @param {FirebaseFirestore.Firestore} db
 * @param {Object} input
 * @return {FirebaseFirestore.DocumentReference}
 */
/**
 * Adds write audit record to the audit trail so important changes remain
 * attributable.
 */
function writeAuditRecord(
    transaction,
    db,
    input,
) {
  if (!transaction) {
    throw new Error(
        "Audit transaction is required.",
    );
  }


  if (!db) {
    throw new Error(
        "Firestore is required.",
    );
  }


  const auditId =
    requireText(
        input &&
        input.auditId,
        "Audit ID",
    );


  const actionType =
    requireText(
        input &&
        input.actionType,
        "Audit action type",
    );


  const performedBy =
    requireText(
        input &&
        input.performedBy,
        "Audit performer",
    );


  const entityType =
    optionalText(
        input &&
        input.entityType,
    );


  const entityId =
    optionalText(
        input &&
        input.entityId,
    );


  const details =
    input &&
    input.details &&
    typeof input.details === "object" &&
    !Array.isArray(
        input.details,
    )
      ? input.details
      : {};


  const auditRef =
    db
        .collection(
            "auditLogs",
        )
        .doc(
            auditId,
        );


  transaction.set(
      auditRef,
      {
        auditId,

        actionType,

        entityType,

        entityId,

        performedBy,

        performedAt:
          FieldValue.serverTimestamp(),

        details,
      },
  );


  return auditRef;
}


/**
 * Creates a stable audit document ID for an
 * inventory-operation audit event.
 *
 * One operation/action pair therefore maps to one
 * authoritative audit record.
 *
 * @param {string} operationId
 * @param {string} actionType
 * @return {string}
 */
/**
 * Adds inventory audit ID to the audit trail so important changes remain
 * attributable.
 */
function inventoryAuditId(
    operationId,
    actionType,
) {
  const safeOperationId =
    requireText(
        operationId,
        "Operation ID",
    );


  const safeActionType =
    requireText(
        actionType,
        "Audit action type",
    );


  return (
    safeOperationId +
    "__" +
    safeActionType
  );
}


/**
 * Validates a required string.
 *
 * @param {*} value
 * @param {string} fieldName
 * @return {string}
 */
/**
 * Contains the require text step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function requireText(
    value,
    fieldName,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new Error(
        fieldName +
        " is required.",
    );
  }


  return value.trim();
}


/**
 * Normalises an optional string.
 *
 * @param {*} value
 * @return {string|null}
 */
/**
 * Contains the optional text step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function optionalText(
    value,
) {
  if (
    typeof value !== "string"
  ) {
    return null;
  }


  const normalized =
    value.trim();


  return normalized === ""
    ? null
    : normalized;
}


module.exports = {
  AUDIT_ACTIONS,
  inventoryAuditId,
  writeAuditRecord,
};