/**
 * Helpers for retrieving owner-scoped inventory reconciliation data from Firestore.
 */

const {
  getAdminFirestore,
} = require("./firebaseAdmin");

const {requireActiveUser, ApplicationHttpError} =
  require("./applicationAuthorization");

const TERMINAL_STATUSES = new Set([
  "CONFIRMED",
  "CONFLICT",
  "REJECTED",
]);


class ReconciliationHttpError extends Error {
  constructor(
    statusCode,
    code,
    message,
  ) {
    super(message);

    this.name = "ReconciliationHttpError";
    this.statusCode = statusCode;
    this.code = code;
  }
}


/**
 * Loads a terminal operation only for its active owner, then gathers the related
 * batches and transactions needed to reconcile local pending state.
 */
async function getInventoryReconciliation(
  operationId,
  authenticatedUid,
) {

  try {
    await requireActiveUser(authenticatedUid);
  } catch (error) {
    if (error instanceof ApplicationHttpError) {
      throw new ReconciliationHttpError(error.statusCode,
          error.code, error.message);
    }
    throw error;
  }

  const db = getAdminFirestore();

  const operationRef =
    db
      .collection("inventoryOperations")
      .doc(operationId);

  const operationSnapshot =
    await operationRef.get();


  if (!operationSnapshot.exists) {
    throw new ReconciliationHttpError(
      404,
      "OPERATION_NOT_FOUND",
      "Inventory operation was not found.",
    );
  }


  const operation =
    operationSnapshot.data();


  if (
    !operation ||
    operation.performedBy !== authenticatedUid
  ) {
    throw new ReconciliationHttpError(
      403,
      "OPERATION_OWNER_MISMATCH",
      "You cannot reconcile this inventory operation.",
    );
  }


  const status =
    operation.status;


  if (!TERMINAL_STATUSES.has(status)) {
    throw new ReconciliationHttpError(
      409,
      "OPERATION_NOT_TERMINAL",
      "Inventory operation has not reached a terminal state.",
    );
  }



  const affectedBatchIds =
    new Set();


  if (Array.isArray(operation.allocations)) {
    for (const allocation of operation.allocations) {

      if (
        allocation &&
        typeof allocation.batchId === "string" &&
        allocation.batchId.trim() !== ""
      ) {
        affectedBatchIds.add(
          allocation.batchId.trim(),
        );
      }
    }
  }




  const transactionSnapshot =
    await db
      .collection("inventoryTransactions")
      .where(
        "operationId",
        "==",
        operationId,
      )
      .get();


  const transactions = [];


  transactionSnapshot.forEach(
    (documentSnapshot) => {

      const transaction =
        documentSnapshot.data() || {};


      if (
        typeof transaction.batchId === "string" &&
        transaction.batchId.trim() !== ""
      ) {
        affectedBatchIds.add(
          transaction.batchId.trim(),
        );
      }


      transactions.push(
        normalizeTransaction(
          documentSnapshot.id,
          transaction,
        ),
      );
    },
  );




  const batches = [];


  for (const batchId of affectedBatchIds) {

    const batchSnapshot =
      await db
        .collection("batches")
        .doc(batchId)
        .get();



    if (!batchSnapshot.exists) {
      continue;
    }


    batches.push(
      normalizeBatch(
        batchSnapshot.id,
        batchSnapshot.data() || {},
      ),
    );
  }


  const nestedResult =
    operation.result &&
    typeof operation.result === "object"
      ? operation.result
      : {};


  return {
    operationId,

    status,

    resultCode:
      firstDefined(
        operation.resultCode,
        nestedResult.resultCode,
        nestedResult.code,
      ),

    resultMessage:
      firstDefined(
        operation.resultMessage,
        nestedResult.resultMessage,
        nestedResult.message,
      ),

    serverCreatedAt:
      toMillis(
        operation.serverCreatedAt,
      ),

    processedAt:
      toMillis(
        operation.processedAt,
      ),

    affectedBatchIds:
      Array.from(
        affectedBatchIds,
      ),

    batches,

    transactions,
  };
}


/**
 * Projects a Firestore batch document into the stable reconciliation response shape. Missing
 * optional identifiers become null, numeric stock fields default to zero, and timestamp
 * variants are converted to epoch milliseconds for the Android client.
 *
 * @param {string} documentId Firestore document ID used as an identity fallback
 * @param {object} batch stored batch fields
 * @return {object} normalized batch snapshot
 */
function normalizeBatch(
  documentId,
  batch,
) {
  return {
    batchId:
      firstDefined(
        batch.batchId,
        documentId,
      ),

    medicineId:
      nullableString(
        batch.medicineId,
      ),

    batchNumber:
      nullableString(
        batch.batchNumber,
      ),

    receivedDate:
      toMillis(
        batch.receivedDate,
      ),

    expiryDate:
      toMillis(
        batch.expiryDate,
      ),

    initialQuantity:
      integerOrZero(
        batch.initialQuantity,
      ),

    quantityAvailable:
      integerOrZero(
        batch.quantityAvailable,
      ),

    status:
      nullableString(
        batch.status,
      ),

    updatedAt:
      toMillis(
        batch.updatedAt,
      ),
  };
}



/**
 * Normalizes one authoritative stock transaction for local reconciliation. The helper accepts
 * legacy field naming for transaction type and uses the document ID when an explicit ID is absent,
 * allowing older and newer records to share the same response contract.
 *
 * @param {string} documentId Firestore document ID
 * @param {object} transaction stored transaction fields
 * @return {object} normalized transaction snapshot
 */
function normalizeTransaction(
  documentId,
  transaction,
) {
  return {
    transactionId:
      firstDefined(
        transaction.transactionId,
        documentId,
      ),

    operationId:
      nullableString(
        transaction.operationId,
      ),

    medicineId:
      nullableString(
        transaction.medicineId,
      ),

    batchId:
      nullableString(
        transaction.batchId,
      ),



    transactionType:
      firstDefined(
        transaction.transactionType,
        transaction.type,
      ),

    quantityChange:
      integerOrZero(
        transaction.quantityChange,
      ),

    reason:
      nullableString(
        transaction.reason,
      ),

    performedBy:
      nullableString(
        transaction.performedBy,
      ),

    clientCreatedAt:
      toMillis(
        transaction.clientCreatedAt,
      ),

    serverCreatedAt:
      toMillis(
        transaction.serverCreatedAt,
      ),
  };
}


function firstDefined(
  ...values
) {
  for (const value of values) {

    if (
      value !== undefined &&
      value !== null
    ) {
      return value;
    }
  }

  return null;
}


function nullableString(
  value,
) {
  if (
    value === undefined ||
    value === null
  ) {
    return null;
  }

  return String(value);
}


function integerOrZero(
  value,
) {
  if (
    typeof value === "number" &&
    Number.isFinite(value)
  ) {
    return Math.trunc(value);
  }

  return 0;
}


/**
 * Converts Firestore Timestamp, Date, or numeric time representations to epoch milliseconds.
 * Unknown values return zero because reconciliation responses use a numeric field consistently.
 *
 * @param {*} value stored timestamp representation
 * @return {number} epoch milliseconds, or zero when no supported time is present
 */
function toMillis(
  value,
) {
  if (
    value === undefined ||
    value === null
  ) {
    return 0;
  }


  if (
    typeof value === "number" &&
    Number.isFinite(value)
  ) {
    return Math.trunc(value);
  }


  if (
    value instanceof Date
  ) {
    return value.getTime();
  }


  if (
    typeof value.toMillis === "function"
  ) {
    return value.toMillis();
  }


  if (
    typeof value.toDate === "function"
  ) {
    return value
      .toDate()
      .getTime();
  }


  return 0;
}


module.exports = {
  getInventoryReconciliation,
  ReconciliationHttpError,
};