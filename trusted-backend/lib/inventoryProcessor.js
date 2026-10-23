/**
 * This is the server-side decision point for submitted stock operations. It
 * validates the caller and operation, then uses Firestore transactions to apply
 * one authoritative result.
 *
 * The duplicate-batch and retry checks protect against stock being counted twice
 * when requests race or are delivered again.
 */
const {createHash} = require("node:crypto");

const {
  FieldValue,
  Timestamp,
} = require("firebase-admin/firestore");

const {
  getAdminFirestore,
} = require("./firebaseAdmin");

const {
  AUDIT_ACTIONS,
  inventoryAuditId,
  writeAuditRecord,
} = require("./audit");


const TERMINAL_STATUSES =
  new Set([
    "CONFIRMED",
    "CONFLICT",
    "REJECTED",
  ]);

const RECEIVE_STOCK =
  "RECEIVE_STOCK";

const CREATE_ISSUE =
  "CREATE_ISSUE";

const CREATE_ADJUSTMENT =
  "CREATE_ADJUSTMENT";

const CREATE_CORRECTION =
  "CREATE_CORRECTION";

const OVERRIDE_FEFO =
  "OVERRIDE_FEFO";

const BUSINESS_TIME_ZONE =
  "Africa/Johannesburg";


class ProcessorHttpError extends Error {
  constructor(
      statusCode,
      code,
      message,
  ) {
    super(message);

    this.statusCode =
      statusCode;

    this.code =
      code;
  }
}


/**
 * Processes a submitted stock operation. The server checks the request and decides
 * which validated, transactional stock change can be accepted.
 * Firestore transaction retries are expected; no stock change should be applied
 * twice.
 */
async function processInventoryOperation(
    operationId,
    authenticatedUid,
)
{
  const db =
    getAdminFirestore();

  const operationRef =
    db.collection(
        "inventoryOperations",
    ).doc(
        operationId,
    );

  return db.runTransaction(
      async (transaction) => {
        const operationSnapshot =
          await transaction.get(
              operationRef,
          );

        if (!operationSnapshot.exists) {
          throw new ProcessorHttpError(
              404,
              "OPERATION_NOT_FOUND",
              "Inventory operation was not found.",
          );
        }

        const operation =
          operationSnapshot.data();


        /*
         * Never trust performedBy merely because
         * it exists in Firestore.
         */
        if (operation.performedBy !==
            authenticatedUid) {
          throw new ProcessorHttpError(
              403,
              "OPERATION_OWNER_MISMATCH",
              "The authenticated user does not own this operation.",
          );
        }


        // Terminal responses are also protected: deactivated or deleted
        // accounts may not retrieve an operation result through this API.
        // The profile read is in the same transaction as stock processing.
        const sessionSnapshot = await transaction.get(
            db.collection("users").doc(authenticatedUid));
        if (!sessionSnapshot.exists) {
          throw new ProcessorHttpError(403, "USER_PROFILE_NOT_FOUND",
              "Your account could not be verified.");
        }
        const sessionProfile = sessionSnapshot.data() || {};
        if (sessionProfile.uid !== undefined &&
            sessionProfile.uid !== authenticatedUid) {
          throw new ProcessorHttpError(403, "USER_PROFILE_INVALID",
              "Your account profile is invalid.");
        }
        if (sessionProfile.isActive !== true) {
          throw new ProcessorHttpError(403, "ACCOUNT_INACTIVE",
              "Your account is inactive.");
        }

        /*
         * Idempotency:
         *
         * If this operation already reached a terminal
         * cloud state, return the existing result.
         */
        if (TERMINAL_STATUSES.has(
            operation.status,
        )) {
          return operationResult(
              operationId,
              operation.status,
              operation.resultCode,
              operation.resultMessage,
              false,
          );
        }


        if (operation.status !==
            "SUBMITTED") {
          throw new ProcessorHttpError(
              409,
              "OPERATION_NOT_SUBMITTED",
              "Only submitted operations can be processed.",
          );
        }


        const envelopeError =
          validateOperationEnvelope(
              operationId,
              operation,
          );


        if (envelopeError) {
          return rejectOperation(
              transaction,
              operationRef,
              operationId,
              envelopeError.code,
              envelopeError.message,
          );
        }


        /*
         * Dispatch to the trusted processor for each
         * supported inventory operation.
         */
        if (operation.operationType ===
            "RECEIPT") {
          return processReceiptOperation(
              db,
              transaction,
              operationRef,
              operationId,
              authenticatedUid,
              operation,
          );
        }


        if (operation.operationType ===
            "ISSUE") {
          return processIssueOperation(
              db,
              transaction,
              operationRef,
              operationId,
              authenticatedUid,
              operation,
          );
        }


        if (operation.operationType ===
            "ADJUSTMENT") {
          return processAdjustmentOperation(
              db,
              transaction,
              operationRef,
              operationId,
              authenticatedUid,
              operation,
          );
        }


        if (operation.operationType ===
            "CORRECTION") {
          return processCorrectionOperation(
              db,
              transaction,
              operationRef,
              operationId,
              authenticatedUid,
              operation,
          );
        }


        return rejectOperation(
            transaction,
            operationRef,
            operationId,
            "UNSUPPORTED_OPERATION_TYPE",
            "This operation type is not yet supported " +
            "by the trusted processor.",
        );
      },
  );
}


/**
 * Validates the receipt and its batch identity before increasing authoritative
 * stock. Retried and competing receipts must not create duplicate batches.
 */
async function processReceiptOperation(
    db,
    transaction,
    operationRef,
    operationId,
    authenticatedUid,
    operation,
)
{
  const receiptValidation =
    validateReceiptPayload(
        operation,
    );


  if (!receiptValidation.valid) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        receiptValidation.code,
        receiptValidation.message,
    );
  }


  const allocation =
    receiptValidation.allocation;

  const receiptMode = receiptValidation.receiptMode;


  const userRef =
    db.collection(
        "users",
    ).doc(
        authenticatedUid,
    );


  const medicineRef =
    db.collection(
        "medicines",
    ).doc(
        operation.medicineId,
    );


  const batchRef =
    db.collection(
        "batches",
    ).doc(
        allocation.batchId,
    );


  /*
   * A randomly generated batchId is not a unique lot identity:
   * different devices may submit the same medicine + batch number.
   * Reserve this logical identity in the SAME Firestore transaction
   * that creates the batch. Firestore retries competing transactions
   * when they read/write the same claim document.
   */
  const batchNumberKey =
    normalizeBatchNumber(
        allocation.batchNumber,
    );

  const batchClaimId =
    createHash("sha256")
        .update(JSON.stringify([
          operation.medicineId,
          batchNumberKey,
        ]))
        .digest("hex");

  const batchClaimRef =
    db.collection(
        "medicineBatchClaims",
    ).doc(
        batchClaimId,
    );

  /*
   * Previous batches have no claim document. Read them inside the
   * transaction and compare normalized numbers before accepting
   * a new claim; a simple batchId check would miss legacy lots.
   * A single medicineId filter needs no composite index.
   */
  const existingMedicineBatchesQuery =
    db.collection("batches")
        .where("medicineId", "==", operation.medicineId);


  /*
   * Deterministic transaction ID.
   */
  const confirmedTransactionId =
    `${operationId}_${allocation.batchId}_0`;


  const confirmedTransactionRef =
    db.collection(
        "inventoryTransactions",
    ).doc(
        confirmedTransactionId,
    );


  /*
   * ALL reads happen before stock-changing writes.
   */
  const userSnapshot =
    await transaction.get(
        userRef,
    );


  const medicineSnapshot =
    await transaction.get(
        medicineRef,
    );


  const batchSnapshot =
    await transaction.get(
        batchRef,
    );


  const batchClaimSnapshot =
    await transaction.get(
        batchClaimRef,
    );


  const existingMedicineBatches =
    receiptMode === "NEW_BATCH" ?
      await transaction.get(existingMedicineBatchesQuery) : {docs: []};


  const confirmedTransactionSnapshot =
    await transaction.get(
        confirmedTransactionRef,
    );


  if (!userSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "USER_NOT_FOUND",
        "The user account could not be verified.",
    );
  }


  const user =
    userSnapshot.data();


  if (user.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ACCOUNT_INACTIVE",
        "The user account is inactive.",
    );
  }


  const permissions =
    permissionsForUser(
        user,
    );


  if (!permissions.includes(
      RECEIVE_STOCK,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "PERMISSION_DENIED",
        "The user no longer has permission to receive stock.",
    );
  }


  if (!medicineSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine does not exist in shared inventory.",
    );
  }


  const medicine =
    medicineSnapshot.data();


  if (medicine.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine record is invalid.",
    );
  }


  if (medicine.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "MEDICINE_INACTIVE",
        "Stock cannot be received for an inactive medicine.",
    );
  }


  /*
   * Secondary receipt idempotency barrier.
   */
  if (confirmedTransactionSnapshot.exists) {
    const existingTransaction =
      confirmedTransactionSnapshot.data();


    if (existingTransaction.operationId !==
        operationId) {
      throw new Error(
          "Confirmed transaction ID collision.",
      );
    }


    return confirmAlreadyProcessed(
        transaction,
        operationRef,
        operationId,
        "This receipt was already processed.",
    );
  }


  /*
   * EXISTING_BATCH is a separately authorised, explicit receipt mode.
   * A duplicate NEW_BATCH request must never silently turn into a top-up.
   * Keep all reads above this block so stock updates remain transactional.
   */
  if (receiptMode === "EXISTING_BATCH") {
    if (!batchSnapshot.exists) {
      return conflictOperation(transaction, operationRef, operationId,
          "BATCH_NOT_FOUND", "The selected existing batch was not found.");
    }

    const batch = batchSnapshot.data();
    const expiryMillis = batch.expiryDate &&
        typeof batch.expiryDate.toMillis === "function" ?
      batch.expiryDate.toMillis() : batch.expiryDate;

    if (batch.batchId !== allocation.batchId ||
        batch.medicineId !== operation.medicineId ||
        normalizeBatchNumber(batch.batchNumber) !== batchNumberKey ||
        expiryMillis !== allocation.expiryDate) {
      return conflictOperation(transaction, operationRef, operationId,
          "BATCH_DETAILS_CHANGED", "Selected batch details do not match shared inventory.");
    }

    if (batchClaimSnapshot.exists &&
        batchClaimSnapshot.data().batchId !== allocation.batchId) {
      return conflictOperation(transaction, operationRef, operationId,
          "BATCH_CLAIM_MISMATCH", "The batch identity belongs to another record.");
    }

    if (isExpiredBusinessDate(expiryMillis) || batch.status === "EXPIRED") {
      return conflictOperation(transaction, operationRef, operationId,
          "BATCH_EXPIRED", "Additional stock cannot be received into an expired batch.");
    }

    if (batch.status !== "ACTIVE" && batch.status !== "DEPLETED") {
      return conflictOperation(transaction, operationRef, operationId,
          "BATCH_UNAVAILABLE", "The selected batch is not eligible for receiving.");
    }

    const oldAvailable = batch.quantityAvailable;
    const oldInitial = batch.initialQuantity;
    const maxQuantity = 2147483647;
    if (!Number.isSafeInteger(oldAvailable) || oldAvailable < 0 ||
        !Number.isSafeInteger(oldInitial) || oldInitial < 0 ||
        oldAvailable > maxQuantity - allocation.quantity ||
        oldInitial > maxQuantity - allocation.quantity) {
      return rejectOperation(transaction, operationRef, operationId,
          "INVALID_STOCK_QUANTITY", "Receiving would exceed supported stock limits.");
    }

    transaction.update(batchRef, {
      quantityAvailable: oldAvailable + allocation.quantity,
      initialQuantity: oldInitial + allocation.quantity,
      status: "ACTIVE",
      updatedAt: FieldValue.serverTimestamp(),
    });

    transaction.create(confirmedTransactionRef, {
      transactionId: confirmedTransactionId,
      operationId,
      medicineId: operation.medicineId,
      batchId: allocation.batchId,
      transactionType: "RECEIPT",
      quantityChange: allocation.quantity,
      performedBy: authenticatedUid,
      clientCreatedAt: operation.clientCreatedAt,
      receivedDate: Timestamp.fromMillis(allocation.receivedDate),
      serverCreatedAt: FieldValue.serverTimestamp(),
    });

    const resultCode = "EXISTING_BATCH_RECEIPT_CONFIRMED";
    const resultMessage = "Additional stock received into existing batch.";
    transaction.update(operationRef,
        terminalFields("CONFIRMED", resultCode, resultMessage));
    return operationResult(operationId, "CONFIRMED",
        resultCode, resultMessage, true);
  }

  /*
   * NEW_BATCH retains the strict unique medicine + lot-number claim.
   * Missing receiptMode is treated as NEW_BATCH for older clients.
   */
  const legacyBatchExists =
    existingMedicineBatches.docs.some(
        (doc) => {
          const stored = doc.data();
          return stored &&
            normalizeBatchNumber(stored.batchNumber) ===
                batchNumberKey;
        },
    );

  if (batchSnapshot.exists ||
      batchClaimSnapshot.exists ||
      legacyBatchExists) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "BATCH_ALREADY_EXISTS",
        "This medicine already has a batch with that number.",
    );
  }

  const status =
    isExpiredBusinessDate(
        allocation.expiryDate,
    )
      ? "EXPIRED"
      : "ACTIVE";


  transaction.create(
      batchClaimRef,
      {
        medicineId: operation.medicineId,
        batchId: allocation.batchId,
        batchNumber: allocation.batchNumber,
        batchNumberKey,
        operationId,
        createdAt: FieldValue.serverTimestamp(),
      },
  );


  transaction.create(
      batchRef,
      {
        batchId:
          allocation.batchId,

        medicineId:
          operation.medicineId,

        batchNumber:
          allocation.batchNumber,

        receivedDate:
          Timestamp.fromMillis(
              allocation.receivedDate,
          ),

        expiryDate:
          Timestamp.fromMillis(
              allocation.expiryDate,
          ),

        initialQuantity:
          operation.requestedQuantity,

        quantityAvailable:
          operation.requestedQuantity,

        status,

        updatedAt:
          FieldValue.serverTimestamp(),
      },
  );


  const confirmedTransaction = {
    transactionId:
      confirmedTransactionId,

    operationId,

    medicineId:
      operation.medicineId,

    batchId:
      allocation.batchId,

    transactionType:
      "RECEIPT",

    quantityChange:
      operation.requestedQuantity,

    performedBy:
      authenticatedUid,

    clientCreatedAt:
      operation.clientCreatedAt,

    serverCreatedAt:
      FieldValue.serverTimestamp(),
  };


  copyOptionalReason(
      confirmedTransaction,
      operation.reason,
  );


  transaction.create(
      confirmedTransactionRef,
      confirmedTransaction,
  );


  const resultCode =
    "RECEIPT_CONFIRMED";


  const resultMessage =
    "Stock receipt confirmed.";


  transaction.update(
      operationRef,
      terminalFields(
          "CONFIRMED",
          resultCode,
          resultMessage,
      ),
  );


  return operationResult(
      operationId,
      "CONFIRMED",
      resultCode,
      resultMessage,
      true,
  );
}


/**
 * Checks the requested issue against available stock and the authoritative
 * expiry-ordered allocation before recording the movement.
 */
async function processIssueOperation(
    db,
    transaction,
    operationRef,
    operationId,
    authenticatedUid,
    operation,
)
{
  /*
   * -----------------------------------------------
   * Validate the submitted ISSUE structure.
   * -----------------------------------------------
   */
  const issueValidation =
    validateIssuePayload(
        operation,
    );


  if (!issueValidation.valid) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        issueValidation.code,
        issueValidation.message,
    );
  }


  const allocations =
    issueValidation.allocations;


  /*
   * -----------------------------------------------
   * Load user and medicine first.
   * -----------------------------------------------
   */
  const userRef =
    db.collection(
        "users",
    ).doc(
        authenticatedUid,
    );


  const medicineRef =
    db.collection(
        "medicines",
    ).doc(
        operation.medicineId,
    );


  const userSnapshot =
    await transaction.get(
        userRef,
    );


  const medicineSnapshot =
    await transaction.get(
        medicineRef,
    );


  /*
   * -----------------------------------------------
   * Re-authorize CURRENT cloud user.
   * -----------------------------------------------
   */
  if (!userSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "USER_NOT_FOUND",
        "The user account could not be verified.",
    );
  }


  const user =
    userSnapshot.data();


  if (user.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ACCOUNT_INACTIVE",
        "The user account is inactive.",
    );
  }


  const permissions =
    permissionsForUser(
        user,
    );


  if (!permissions.includes(
      CREATE_ISSUE,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "PERMISSION_DENIED",
        "The user no longer has permission to issue stock.",
    );
  }


  /*
   * -----------------------------------------------
   * Validate authoritative medicine.
   * -----------------------------------------------
   */
  if (!medicineSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine does not exist in shared inventory.",
    );
  }


  const medicine =
    medicineSnapshot.data();


  if (medicine.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine record is invalid.",
    );
  }


  if (medicine.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "MEDICINE_INACTIVE",
        "Stock cannot be issued for an inactive medicine.",
    );
  }


  /*
   * -----------------------------------------------
   * Build all references before remaining reads.
   * -----------------------------------------------
   */
  const batchRefs =
    allocations.map(
        (allocation) =>
          db.collection(
              "batches",
          ).doc(
              allocation.batchId,
          ),
    );


  const transactionRefs =
    allocations.map(
        (allocation, index) =>
          db.collection(
              "inventoryTransactions",
          ).doc(
              `${operationId}_${allocation.batchId}_${index}`,
          ),
    );


  /*
   * Read every specifically allocated batch.
   */
  const allocatedBatchSnapshots = [];


  for (const batchRef of batchRefs) {
    allocatedBatchSnapshots.push(
        await transaction.get(
            batchRef,
        ),
    );
  }


  /*
   * Read deterministic confirmed transaction IDs.
   *
   * This is our secondary idempotency barrier.
   */
  const confirmedTransactionSnapshots = [];


  for (const transactionRef of transactionRefs) {
    confirmedTransactionSnapshots.push(
        await transaction.get(
            transactionRef,
        ),
    );
  }


  /*
   * Load all batches for this medicine.
   *
   * We need the complete current authoritative
   * medicine stock set to independently calculate
   * the correct FEFO recommendation.
   */
  const medicineBatchesQuery =
    db.collection(
        "batches",
    ).where(
        "medicineId",
        "==",
        operation.medicineId,
    );


  const medicineBatchesSnapshot =
    await transaction.get(
        medicineBatchesQuery,
    );


  /*
   * -----------------------------------------------
   * Secondary idempotency check.
   * -----------------------------------------------
   */
  const existingConfirmedCount =
    confirmedTransactionSnapshots.filter(
        (snapshot) => snapshot.exists,
    ).length;


  if (existingConfirmedCount > 0) {
    /*
     * A real processor transaction creates all ISSUE
     * transactions atomically.
     *
     * Therefore some-but-not-all deterministic
     * transaction documents indicate inconsistent
     * cloud data and must never cause another stock
     * deduction.
     */
    if (existingConfirmedCount !==
        confirmedTransactionSnapshots.length) {
      throw new Error(
          "Partial confirmed transaction state detected.",
      );
    }


    for (const snapshot of
      confirmedTransactionSnapshots) {
      const confirmed =
        snapshot.data();


      if (confirmed.operationId !==
          operationId) {
        throw new Error(
            "Confirmed transaction ID collision.",
        );
      }
    }


    return confirmAlreadyProcessed(
        transaction,
        operationRef,
        operationId,
        "This stock issue was already processed.",
    );
  }


  /*
   * -----------------------------------------------
   * Validate each requested batch against CURRENT
   * authoritative stock.
   * -----------------------------------------------
   */
  const allocatedBatchesById =
    new Map();


  for (let index = 0;
    index < allocations.length;
    index++) {
    const allocation =
      allocations[index];


    const batchSnapshot =
      allocatedBatchSnapshots[index];


    if (!batchSnapshot.exists) {
      return conflictOperation(
          transaction,
          operationRef,
          operationId,
          "INVALID_BATCH",
          "An allocated batch no longer exists.",
      );
    }


    const batch =
      batchSnapshot.data();


    if (batch.medicineId !==
        operation.medicineId) {
      return rejectOperation(
          transaction,
          operationRef,
          operationId,
          "INVALID_BATCH",
          "An allocated batch does not belong to the selected medicine.",
      );
    }


    if (batch.status !==
        "ACTIVE") {
      return conflictOperation(
          transaction,
          operationRef,
          operationId,
          "INVALID_BATCH",
          "An allocated batch is no longer active.",
      );
    }


    const expiryMillis =
      firestoreTimestampMillis(
          batch.expiryDate,
      );


    if (expiryMillis === null) {
      throw new Error(
          "Allocated batch has an invalid expiry date.",
      );
    }


    if (isExpiredBusinessDate(
        expiryMillis,
    )) {
      return conflictOperation(
          transaction,
          operationRef,
          operationId,
          "BATCH_EXPIRED",
          "An allocated batch is expired.",
      );
    }


    if (!Number.isInteger(
        batch.quantityAvailable,
    ) ||
        batch.quantityAvailable < 0) {
      throw new Error(
          "Allocated batch has an invalid authoritative quantity.",
      );
    }


    if (allocation.quantity >
        batch.quantityAvailable) {
      return conflictOperation(
          transaction,
          operationRef,
          operationId,
          "INSUFFICIENT_STOCK",
          "Authoritative stock is no longer sufficient for this issue.",
      );
    }


    allocatedBatchesById.set(
        allocation.batchId,
        batch,
    );
  }


  /*
   * -----------------------------------------------
   * Build current authoritative FEFO recommendation.
   * -----------------------------------------------
   */
  const authoritativeBatches = [];


  medicineBatchesSnapshot.forEach(
      (snapshot) => {
        authoritativeBatches.push({
          id:
            snapshot.id,

          data:
            snapshot.data(),
        });
      },
  );


  const fefoResult =
    buildAuthoritativeFefo(
        authoritativeBatches,
        operation.requestedQuantity,
    );


  /*
   * The device may have had enough stock when the
   * issue was created, but another device may have
   * consumed some before this operation synced.
   */
  if (!fefoResult.sufficientStock) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "INSUFFICIENT_STOCK",
        "Authoritative stock is no longer sufficient for this issue.",
    );
  }


  /*
   * -----------------------------------------------
   * Determine whether Android followed FEFO.
   * -----------------------------------------------
   *
   * We compare batch quantities rather than array
   * order because order itself does not change the
   * physical stock result.
   */
  const followsFefo =
    allocationsMatch(
        allocations,
        fefoResult.allocations,
    );


  let fefoOverrideReason =
    null;


  if (!followsFefo) {
    const overrideReason =
      normalizedOptionalString(
          operation.overrideReason,
      );


    if (!overrideReason) {
      return rejectOperation(
          transaction,
          operationRef,
          operationId,
          "FEFO_OVERRIDE_REASON_REQUIRED",
          "A reason is required when overriding the FEFO recommendation.",
      );
    }


    if (!permissions.includes(
        OVERRIDE_FEFO,
    )) {
      return rejectOperation(
          transaction,
          operationRef,
          operationId,
          "FEFO_OVERRIDE_NOT_ALLOWED",
          "The user does not have permission to override FEFO.",
      );
    }


    /*
     * Preserve the trusted, validated reason so the
     * authoritative audit record can be written after
     * all validation has passed.
     */
    fefoOverrideReason =
      overrideReason;
  }


  /*
   * ALL AUTHORITATIVE READS HAVE COMPLETED.
   *
   * Only writes occur below this point.
   */


  /*
   * Deduct every allocated batch and create one
   * immutable confirmed transaction per batch.
   */
  for (let index = 0;
    index < allocations.length;
    index++) {
    const allocation =
      allocations[index];


    const batch =
      allocatedBatchesById.get(
          allocation.batchId,
      );


    const quantityAfterIssue =
      batch.quantityAvailable -
      allocation.quantity;


    /*
     * Issue validation above guarantees this can
     * never be negative.
     */
    const batchStatus =
      quantityAfterIssue === 0
        ? "DEPLETED"
        : "ACTIVE";


    transaction.update(
        batchRefs[index],
        {
          quantityAvailable:
            quantityAfterIssue,

          status:
            batchStatus,

          updatedAt:
            FieldValue.serverTimestamp(),
        },
    );


    const confirmedTransaction = {
      transactionId:
        transactionRefs[index].id,

      operationId,

      medicineId:
        operation.medicineId,

      batchId:
        allocation.batchId,

      transactionType:
        "ISSUE",

      /*
       * Outgoing stock is always negative.
       */
      quantityChange:
        -allocation.quantity,

      performedBy:
        authenticatedUid,

      clientCreatedAt:
        operation.clientCreatedAt,

      serverCreatedAt:
        FieldValue.serverTimestamp(),
    };


    copyOptionalReason(
        confirmedTransaction,
        operation.reason,
    );


    transaction.create(
        transactionRefs[index],
        confirmedTransaction,
    );
  }

  /*
   * Authoritative FEFO override audit
   *
   * Only write this event when the submitted
   * allocations genuinely differed from FEFO and the
   * trusted permission/reason checks succeeded.
   *
   * This is part of the same Firestore transaction as
   * the batch updates, immutable transactions, and
   * operation confirmation.
   */
  if (fefoOverrideReason !== null) {
    writeAuditRecord(
        transaction,
        db,
        {
          auditId:
            inventoryAuditId(
                operationId,
                AUDIT_ACTIONS.FEFO_OVERRIDE,
            ),

          actionType:
            AUDIT_ACTIONS.FEFO_OVERRIDE,

          entityType:
            "INVENTORY_OPERATION",

          entityId:
            operationId,

          performedBy:
            authenticatedUid,

          details: {
            operationId,

            medicineId:
              operation.medicineId,

            requestedQuantity:
              operation.requestedQuantity,

            overrideReason:
              fefoOverrideReason,

            allocationCount:
              allocations.length,
          },
        },
    );
  }


  /*
   * Mark the parent operation CONFIRMED.
   */
  const resultCode =
    "ISSUE_CONFIRMED";


  const resultMessage =
    "Stock issue confirmed.";


  transaction.update(
      operationRef,
      terminalFields(
          "CONFIRMED",
          resultCode,
          resultMessage,
      ),
  );


  return operationResult(
      operationId,
      "CONFIRMED",
      resultCode,
      resultMessage,
      true,
  );
}

/**
 * Checks an adjustment against the current stock record and records the accepted
 * change with its operation history.
 */
async function processAdjustmentOperation(
    db,
    transaction,
    operationRef,
    operationId,
    authenticatedUid,
    operation,
)
{
  const validation =
    validateAdjustmentPayload(
        operation,
    );


  if (!validation.valid) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        validation.code,
        validation.message,
    );
  }


  const allocation =
    validation.allocation;


  const direction =
    validation.direction;


  const reason =
    validation.reason;


  const userRef =
    db.collection(
        "users",
    ).doc(
        authenticatedUid,
    );


  const medicineRef =
    db.collection(
        "medicines",
    ).doc(
        operation.medicineId,
    );


  const batchRef =
    db.collection(
        "batches",
    ).doc(
        allocation.batchId,
    );


  const confirmedTransactionId =
    `${operationId}_${allocation.batchId}_0`;


  const confirmedTransactionRef =
    db.collection(
        "inventoryTransactions",
    ).doc(
        confirmedTransactionId,
    );


  /*
   * All reads before writes.
   */
  const userSnapshot =
    await transaction.get(
        userRef,
    );


  const medicineSnapshot =
    await transaction.get(
        medicineRef,
    );


  const batchSnapshot =
    await transaction.get(
        batchRef,
    );


  const confirmedTransactionSnapshot =
    await transaction.get(
        confirmedTransactionRef,
    );


  /*
   * Re-authorize against CURRENT cloud profile.
   */
  if (!userSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "USER_NOT_FOUND",
        "The user account could not be verified.",
    );
  }


  const user =
    userSnapshot.data();


  if (user.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ACCOUNT_INACTIVE",
        "The user account is inactive.",
    );
  }


  const permissions =
    permissionsForUser(
        user,
    );


  if (!permissions.includes(
      CREATE_ADJUSTMENT,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "PERMISSION_DENIED",
        "The user no longer has permission to adjust stock.",
    );
  }


  /*
   * Validate authoritative medicine.
   */
  if (!medicineSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine does not exist in shared inventory.",
    );
  }


  const medicine =
    medicineSnapshot.data();


  if (medicine.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine record is invalid.",
    );
  }


  if (medicine.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "MEDICINE_INACTIVE",
        "Stock cannot be adjusted for an inactive medicine.",
    );
  }


  /*
   * The selected batch existed locally when the
   * adjustment was created. If it disappeared from
   * authoritative inventory, that is a business
   * conflict rather than a technical retry.
   */
  if (!batchSnapshot.exists) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_BATCH",
        "The selected batch no longer exists.",
    );
  }


  const batch =
    batchSnapshot.data();


  if (batch.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_BATCH",
        "The selected batch does not belong to the medicine.",
    );
  }


  if (!Number.isInteger(
      batch.quantityAvailable,
  ) ||
      batch.quantityAvailable < 0) {
    throw new Error(
        "Authoritative batch has an invalid quantity.",
    );
  }


  /*
   * Secondary idempotency barrier.
   */
  if (confirmedTransactionSnapshot.exists) {
    const confirmed =
      confirmedTransactionSnapshot.data();


    if (confirmed.operationId !==
        operationId) {
      throw new Error(
          "Confirmed transaction ID collision.",
      );
    }


    return confirmAlreadyProcessed(
        transaction,
        operationRef,
        operationId,
        "This stock adjustment was already processed.",
    );
  }


  const signedQuantity =
    direction === "IN"
      ? allocation.quantity
      : -allocation.quantity;


  const quantityAfter =
    batch.quantityAvailable +
    signedQuantity;


  /*
   * OUT may have been valid locally but become
   * impossible after another device changed the
   * authoritative quantity.
   */
  if (quantityAfter < 0) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "INSUFFICIENT_STOCK",
        "Authoritative stock is insufficient for this adjustment.",
    );
  }


  if (!Number.isSafeInteger(
      quantityAfter,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_QUANTITY",
        "The resulting stock quantity is invalid.",
    );
  }


  const expiryMillis =
    firestoreTimestampMillis(
        batch.expiryDate,
    );


  if (expiryMillis === null) {
    throw new Error(
        "Authoritative batch has an invalid expiry date.",
    );
  }


  /*
   * Increasing expired stock must never reactivate
   * it for normal issuing.
   */
  let status;


  if (isExpiredBusinessDate(
      expiryMillis,
  )) {
    status =
      "EXPIRED";
  } else if (quantityAfter === 0) {
    status =
      "DEPLETED";
  } else {
    status =
      "ACTIVE";
  }


  transaction.update(
      batchRef,
      {
        quantityAvailable:
          quantityAfter,

        status,

        updatedAt:
          FieldValue.serverTimestamp(),
      },
  );


  const confirmedTransaction = {
    transactionId:
      confirmedTransactionId,

    operationId,

    medicineId:
      operation.medicineId,

    batchId:
      allocation.batchId,

    transactionType:
      "ADJUSTMENT",

    quantityChange:
      signedQuantity,

    reason,

    performedBy:
      authenticatedUid,

    clientCreatedAt:
      operation.clientCreatedAt,

    serverCreatedAt:
      FieldValue.serverTimestamp(),
  };


  transaction.create(
      confirmedTransactionRef,
      confirmedTransaction,
  );


  const resultCode =
    "ADJUSTMENT_CONFIRMED";


  const resultMessage =
    "Stock adjustment confirmed.";


  transaction.update(
      operationRef,
      terminalFields(
          "CONFIRMED",
          resultCode,
          resultMessage,
      ),
  );


  return operationResult(
      operationId,
      "CONFIRMED",
      resultCode,
      resultMessage,
      true,
  );
}

/**
 * Uses the original operation to decide whether a correction is permitted and how
 * to represent it without duplicating stock.
 */
async function processCorrectionOperation(
    db,
    transaction,
    operationRef,
    operationId,
    authenticatedUid,
    operation,
)
{
  const validation =
    validateCorrectionPayload(
        operation,
    );


  if (!validation.valid) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        validation.code,
        validation.message,
    );
  }


  const allocation =
    validation.allocation;


  const direction =
    validation.direction;


  const reason =
    validation.reason;


  const relatedOperationId =
    validation.relatedOperationId;


  const userRef =
    db.collection(
        "users",
    ).doc(
        authenticatedUid,
    );


  const medicineRef =
    db.collection(
        "medicines",
    ).doc(
        operation.medicineId,
    );


  const batchRef =
    db.collection(
        "batches",
    ).doc(
        allocation.batchId,
    );


  /*
   * The correction must point to a real existing
   * confirmed operation.
   */
  const originalOperationRef =
    db.collection(
        "inventoryOperations",
    ).doc(
        relatedOperationId,
    );


  /*
   * We also independently verify that the selected
   * correction batch actually participated in the
   * immutable movement history of the referenced
   * operation.
   */
  const originalTransactionsQuery =
    db.collection(
        "inventoryTransactions",
    ).where(
        "operationId",
        "==",
        relatedOperationId,
    );


  const confirmedTransactionId =
    `${operationId}_${allocation.batchId}_0`;


  const confirmedTransactionRef =
    db.collection(
        "inventoryTransactions",
    ).doc(
        confirmedTransactionId,
    );


  /*
   * ALL READS BEFORE WRITES
   */
  const userSnapshot =
    await transaction.get(
        userRef,
    );


  const medicineSnapshot =
    await transaction.get(
        medicineRef,
    );


  const batchSnapshot =
    await transaction.get(
        batchRef,
    );


  const originalOperationSnapshot =
    await transaction.get(
        originalOperationRef,
    );


  const originalTransactionsSnapshot =
    await transaction.get(
        originalTransactionsQuery,
    );


  const confirmedTransactionSnapshot =
    await transaction.get(
        confirmedTransactionRef,
    );


  /*
   * Re-authorize against the CURRENT cloud profile.
   */
  if (!userSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "USER_NOT_FOUND",
        "The user account could not be verified.",
    );
  }


  const user =
    userSnapshot.data();


  if (user.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ACCOUNT_INACTIVE",
        "The user account is inactive.",
    );
  }


  const permissions =
    permissionsForUser(
        user,
    );


  if (!permissions.includes(
      CREATE_CORRECTION,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "PERMISSION_DENIED",
        "The user no longer has permission to correct stock.",
    );
  }


  /*
   * Validate authoritative medicine.
   */
  if (!medicineSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine does not exist in shared inventory.",
    );
  }


  const medicine =
    medicineSnapshot.data();


  if (medicine.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_MEDICINE",
        "The medicine record is invalid.",
    );
  }


  if (medicine.isActive !== true) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "MEDICINE_INACTIVE",
        "Stock cannot be corrected for an inactive medicine.",
    );
  }


  /*
   * The affected batch must still exist
   * authoritatively.
   */
  if (!batchSnapshot.exists) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_BATCH",
        "The selected batch no longer exists.",
    );
  }


  const batch =
    batchSnapshot.data();


  if (batch.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_BATCH",
        "The selected batch does not belong to the medicine.",
    );
  }


  if (!Number.isInteger(
      batch.quantityAvailable,
  ) ||
      batch.quantityAvailable < 0) {
    throw new Error(
        "Authoritative batch has an invalid quantity.",
    );
  }


  /*
   * ORIGINAL OPERATION VALIDATION
   *
   * Never update originalOperationRef.
   */
  if (!originalOperationSnapshot.exists) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ORIGINAL_OPERATION_NOT_FOUND",
        "The original operation could not be verified.",
    );
  }


  const originalOperation =
    originalOperationSnapshot.data();


  if (originalOperation.status !==
      "CONFIRMED") {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ORIGINAL_OPERATION_NOT_CONFIRMED",
        "Only a confirmed inventory operation can be corrected.",
    );
  }


  if (originalOperation.medicineId !==
      operation.medicineId) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ORIGINAL_OPERATION_MEDICINE_MISMATCH",
        "The correction does not match the original medicine.",
    );
  }


  /*
   * Verify that the correction batch was actually part
   * of the referenced original confirmed movement.
   */
  let originalBatchMatched =
    false;


  originalTransactionsSnapshot.forEach(
      (snapshot) => {
        const originalTransaction =
          snapshot.data();


        if (originalTransaction &&
            originalTransaction.batchId ===
                allocation.batchId &&
            originalTransaction.medicineId ===
                operation.medicineId) {
          originalBatchMatched =
            true;
        }
      },
  );


  if (!originalBatchMatched) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "ORIGINAL_OPERATION_BATCH_MISMATCH",
        "The selected batch was not part of the original operation.",
    );
  }


  /*
   * Secondary idempotency barrier.
   */
  if (confirmedTransactionSnapshot.exists) {
    const confirmed =
      confirmedTransactionSnapshot.data();


    if (confirmed.operationId !==
        operationId) {
      throw new Error(
          "Confirmed transaction ID collision.",
      );
    }


    return confirmAlreadyProcessed(
        transaction,
        operationRef,
        operationId,
        "This stock correction was already processed.",
    );
  }


  const signedQuantity =
    direction === "IN"
      ? allocation.quantity
      : -allocation.quantity;


  const quantityAfter =
    batch.quantityAvailable +
    signedQuantity;


  /*
   * CORRECTION_OUT may have been valid locally but
   * become impossible because authoritative stock
   * changed before synchronisation.
   */
  if (quantityAfter < 0) {
    return conflictOperation(
        transaction,
        operationRef,
        operationId,
        "INSUFFICIENT_STOCK",
        "Authoritative stock is insufficient for this correction.",
    );
  }


  if (!Number.isSafeInteger(
      quantityAfter,
  )) {
    return rejectOperation(
        transaction,
        operationRef,
        operationId,
        "INVALID_QUANTITY",
        "The resulting stock quantity is invalid.",
    );
  }


  const expiryMillis =
    firestoreTimestampMillis(
        batch.expiryDate,
    );


  if (expiryMillis === null) {
    throw new Error(
        "Authoritative batch has an invalid expiry date.",
    );
  }


  /*
   * A correction must not accidentally make expired
   * stock ACTIVE again.
   */
  let status;


  if (isExpiredBusinessDate(
      expiryMillis,
  )) {
    status =
      "EXPIRED";
  } else if (quantityAfter === 0) {
    status =
      "DEPLETED";
  } else {
    status =
      "ACTIVE";
  }


  /*
   * WRITES
   *
   * Only the NEW correction and affected batch are
   * changed. The referenced original operation and
   * original transactions remain untouched.
   */
  transaction.update(
      batchRef,
      {
        quantityAvailable:
          quantityAfter,

        status,

        updatedAt:
          FieldValue.serverTimestamp(),
      },
  );


  const confirmedTransaction = {
    transactionId:
      confirmedTransactionId,

    operationId,

    medicineId:
      operation.medicineId,

    batchId:
      allocation.batchId,

    transactionType:
      "CORRECTION",

    quantityChange:
      signedQuantity,

    reason,

    performedBy:
      authenticatedUid,

    clientCreatedAt:
      operation.clientCreatedAt,

    serverCreatedAt:
      FieldValue.serverTimestamp(),
  };


  transaction.create(
      confirmedTransactionRef,
      confirmedTransaction,
  );

  /*
   * Authoritative correction audit
   *
   * A correction is a new immutable inventory action.
   * The original operation remains unchanged.
   *
   * The audit event is committed inside the same
   * Firestore transaction as:
   *
   * - authoritative batch mutation
   * - confirmed correction transaction
   * - correction operation confirmation
   */
  writeAuditRecord(
      transaction,
      db,
      {
        auditId:
          inventoryAuditId(
              operationId,
              AUDIT_ACTIONS.CORRECTION_CREATED,
          ),

        actionType:
          AUDIT_ACTIONS.CORRECTION_CREATED,

        entityType:
          "INVENTORY_OPERATION",

        entityId:
          operationId,

        performedBy:
          authenticatedUid,

        details: {
          operationId,

          relatedOperationId,

          medicineId:
            operation.medicineId,

          batchId:
            allocation.batchId,

          correctionDirection:
            direction,

          quantity:
            allocation.quantity,

          signedQuantity,

          reason,
        },
      },
  );

  const resultCode =
    "CORRECTION_CONFIRMED";


  const resultMessage =
    "Stock correction confirmed.";


  transaction.update(
      operationRef,
      terminalFields(
          "CONFIRMED",
          resultCode,
          resultMessage,
      ),
  );


  return operationResult(
      operationId,
      "CONFIRMED",
      resultCode,
      resultMessage,
      true,
  );
}

/**
 * Rejects missing or malformed operation details before a transaction can make
 * changes to shared inventory.
 */
function validateOperationEnvelope(
    operationId,
    operation,
)
{
  if (typeof operation.operationId !==
          "string" ||
      operation.operationId !==
          operationId) {
    return validationError(
        "INVALID_OPERATION",
        "Operation ID is invalid.",
    );
  }


  if (typeof operation.operationType !==
      "string") {
    return validationError(
        "INVALID_OPERATION",
        "Operation type is required.",
    );
  }


  if (typeof operation.medicineId !==
          "string" ||
      operation.medicineId.trim()
          .length === 0) {
    return validationError(
        "INVALID_MEDICINE",
        "Medicine is required.",
    );
  }


  if (!Number.isInteger(
      operation.requestedQuantity,
  ) ||
      operation.requestedQuantity <= 0) {
    return validationError(
        "INVALID_QUANTITY",
        "Requested quantity must be greater than zero.",
    );
  }


  if (typeof operation.performedBy !==
          "string" ||
      operation.performedBy.trim()
          .length === 0) {
    return validationError(
        "INVALID_OPERATION",
        "Operation user is required.",
    );
  }


  if (typeof operation.deviceId !==
          "string" ||
      operation.deviceId.trim()
          .length === 0) {
    return validationError(
        "INVALID_OPERATION",
        "Device identifier is required.",
    );
  }


  if (!Number.isInteger(
      operation.clientCreatedAt,
  ) ||
      operation.clientCreatedAt <= 0) {
    return validationError(
        "INVALID_OPERATION",
        "Client creation timestamp is invalid.",
    );
  }


  return null;
}


/*
 * Preserve the original printed batch number on the batch record,
 * but compare numbers case-insensitively and without outer spaces.
 * Must be identical for both claim hashing and legacy checks.
 */
/**
 * Normalizes batch identifiers to a single comparison format so equivalent labels
 * are not mistaken for different batches.
 */
function normalizeBatchNumber(value) {
  return typeof value === "string" ?
    value.trim().normalize("NFC").toUpperCase() : "";
}


/**
 * Validates receipt mode, medicine and batch identifiers, dates, quantities, and allocation shape
 * before the transaction reads or changes stock. New-batch receipts and explicit existing-batch
 * top-ups have different required fields, so malformed requests cannot silently change modes.
 *
 * @param {object} operation submitted operation document
 * @return {{valid: boolean, code?: string, message?: string, allocation?: object, receiptMode?: string}}
 */
function validateReceiptPayload(
    operation,
)
{
  const receiptMode = operation.receiptMode == null ?
    "NEW_BATCH" : operation.receiptMode;
  if (receiptMode !== "NEW_BATCH" && receiptMode !== "EXISTING_BATCH") {
    return invalidReceipt("INVALID_RECEIPT_MODE", "Unsupported receipt mode.");
  }

  if (!Array.isArray(
      operation.allocations,
  ) ||
      operation.allocations.length !== 1) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Receipt must specify exactly one target batch.",
    );
  }


  const allocation =
    operation.allocations[0];


  if (!allocation ||
      typeof allocation.batchId !==
          "string" ||
      allocation.batchId.trim()
          .length === 0) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Receipt batch ID is required.",
    );
  }


  if (typeof allocation.batchNumber !==
          "string" ||
      allocation.batchNumber.trim()
          .length === 0) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Receipt batch number is required.",
    );
  }


  if (!Number.isInteger(
      allocation.quantity,
  ) ||
      allocation.quantity <= 0) {
    return invalidReceipt(
        "INVALID_QUANTITY",
        "Receipt quantity must be greater than zero.",
    );
  }


  if (allocation.quantity !==
      operation.requestedQuantity) {
    return invalidReceipt(
        "INVALID_QUANTITY",
        "Receipt allocation does not match requested quantity.",
    );
  }


  if (!Number.isInteger(
      allocation.receivedDate,
  ) ||
      allocation.receivedDate <= 0) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Received date is required.",
    );
  }


  if (!Number.isInteger(
      allocation.expiryDate,
  ) ||
      allocation.expiryDate <= 0) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Expiry date is required.",
    );
  }


  if (allocation.expiryDate <=
      allocation.receivedDate) {
    return invalidReceipt(
        "INVALID_BATCH",
        "Expiry date must be after received date.",
    );
  }


  return {
    valid: true,
    receiptMode,

    allocation: {
      batchId:
        allocation.batchId.trim(),

      batchNumber:
        allocation.batchNumber.trim(),

      quantity:
        allocation.quantity,

      receivedDate:
        allocation.receivedDate,

      expiryDate:
        allocation.expiryDate,
    },
  };
}


/**
 * Validates requested issue quantity and each client allocation, including positive quantities,
 * unique batch IDs, contiguous allocation order, and a total equal to the request. Passing this
 * structural check does not authorize the issue or establish that those batches still have stock;
 * the transaction performs those checks against current Firestore state.
 *
 * @param {object} operation submitted issue document
 * @return {{valid: boolean, code?: string, message?: string, allocations?: Array<object>}}
 */
function validateIssuePayload(
    operation,
)
{
  if (!Array.isArray(
      operation.allocations,
  ) ||
      operation.allocations.length === 0) {
    return invalidIssue(
        "INVALID_ALLOCATION",
        "Issue must contain at least one batch allocation.",
    );
  }


  const batchIds =
    new Set();


  const allocations = [];


  let total =
    0;


  for (const allocation of
    operation.allocations) {
    if (!allocation ||
        typeof allocation.batchId !==
            "string" ||
        allocation.batchId.trim()
            .length === 0) {
      return invalidIssue(
          "INVALID_BATCH",
          "Every issue allocation requires a batch ID.",
      );
    }


    const batchId =
      allocation.batchId.trim();


    /*
     * Duplicate batch rows could otherwise hide
     * over-allocation and make deterministic
     * transaction IDs ambiguous.
     */
    if (batchIds.has(
        batchId,
    )) {
      return invalidIssue(
          "INVALID_ALLOCATION",
          "The same batch cannot appear more than once in an issue.",
      );
    }


    if (!Number.isInteger(
        allocation.quantity,
    ) ||
        allocation.quantity <= 0) {
      return invalidIssue(
          "INVALID_QUANTITY",
          "Every issue allocation quantity must be greater than zero.",
      );
    }


    batchIds.add(
        batchId,
    );


    total +=
      allocation.quantity;


    allocations.push({
      batchId,

      quantity:
        allocation.quantity,
    });
  }


  /*
   * No partial fulfilment.
   */
  if (total !==
      operation.requestedQuantity) {
    return invalidIssue(
        "INVALID_ALLOCATION",
        "Issue allocation total must equal the requested quantity.",
    );
  }


  return {
    valid: true,

    allocations,
  };
}

/**
 * Validates adjustment direction, reason, requested quantity, and the single-batch allocation
 * shape. It rejects metadata that belongs to receipt, issue override, or correction operations;
 * authorization and current quantity checks are performed later inside the transaction.
 *
 * @param {object} operation submitted adjustment document
 * @return {{valid: boolean, code?: string, message?: string, direction?: string, reason?: string, allocation?: object}}
 */
function validateAdjustmentPayload(
    operation,
)
{
  const reason =
    normalizedOptionalString(
        operation.reason,
    );


  if (!reason) {
    return invalidAdjustment(
        "ADJUSTMENT_REASON_REQUIRED",
        "A reason is required for a stock adjustment.",
    );
  }


  const direction =
    normalizedOptionalString(
        operation.adjustmentDirection,
    );


  if (direction !== "IN" &&
      direction !== "OUT") {
    return invalidAdjustment(
        "INVALID_ADJUSTMENT_DIRECTION",
        "Adjustment direction must be IN or OUT.",
    );
  }


  if (normalizedOptionalString(
      operation.relatedOperationId,
  )) {
    return invalidAdjustment(
        "INVALID_ADJUSTMENT",
        "An adjustment cannot reference a correction operation.",
    );
  }


  if (normalizedOptionalString(
      operation.overrideReason,
  )) {
    return invalidAdjustment(
        "INVALID_ADJUSTMENT",
        "An adjustment cannot contain FEFO override metadata.",
    );
  }


  if (!Array.isArray(
      operation.allocations,
  ) ||
      operation.allocations.length !== 1) {
    return invalidAdjustment(
        "INVALID_ALLOCATION",
        "Adjustment must affect exactly one batch.",
    );
  }


  const allocation =
    operation.allocations[0];


  if (!allocation ||
      typeof allocation.batchId !==
          "string" ||
      allocation.batchId.trim()
          .length === 0) {
    return invalidAdjustment(
        "INVALID_BATCH",
        "Adjustment batch ID is required.",
    );
  }


  if (!Number.isInteger(
      allocation.quantity,
  ) ||
      allocation.quantity <= 0) {
    return invalidAdjustment(
        "INVALID_QUANTITY",
        "Adjustment quantity must be greater than zero.",
    );
  }


  if (allocation.quantity !==
      operation.requestedQuantity) {
    return invalidAdjustment(
        "INVALID_QUANTITY",
        "Adjustment allocation must equal the requested quantity.",
    );
  }


  if (!Number.isInteger(
      allocation.allocationOrder,
  ) ||
      allocation.allocationOrder !== 0) {
    return invalidAdjustment(
        "INVALID_ALLOCATION",
        "Adjustment allocation order is invalid.",
    );
  }


  return {
    valid: true,

    direction,

    reason,

    allocation: {
      batchId:
        allocation.batchId.trim(),

      quantity:
        allocation.quantity,
    },
  };
}

/**
 * Validates a compensating correction's direction, reason, reference to another operation, and
 * one-batch allocation. The local payload must describe one exact quantity; the transaction then
 * checks the referenced confirmed movement and current batch state before applying its inverse.
 *
 * @param {object} operation submitted correction document
 * @return {{valid: boolean, code?: string, message?: string, direction?: string, reason?: string, relatedOperationId?: string, allocation?: object}}
 */
function validateCorrectionPayload(
    operation,
)
{
  const reason =
    normalizedOptionalString(
        operation.reason,
    );


  if (!reason) {
    return invalidCorrection(
        "CORRECTION_REASON_REQUIRED",
        "A reason is required for a stock correction.",
    );
  }


  const direction =
    normalizedOptionalString(
        operation.correctionDirection,
    );


  if (direction !== "IN" &&
      direction !== "OUT") {
    return invalidCorrection(
        "INVALID_CORRECTION_DIRECTION",
        "Correction direction must be IN or OUT.",
    );
  }


  const relatedOperationId =
    normalizedOptionalString(
        operation.relatedOperationId,
    );


  if (!relatedOperationId) {
    return invalidCorrection(
        "CORRECTION_RELATED_OPERATION_REQUIRED",
        "Correction must reference the original operation.",
    );
  }


  if (relatedOperationId ===
      operation.operationId) {
    return invalidCorrection(
        "INVALID_CORRECTION",
        "A correction cannot reference itself.",
    );
  }


  /*
   * adjustmentDirection belongs only to ADJUSTMENT.
   */
  if (normalizedOptionalString(
      operation.adjustmentDirection,
  )) {
    return invalidCorrection(
        "INVALID_CORRECTION",
        "A correction cannot contain adjustment metadata.",
    );
  }


  /*
   * FEFO metadata belongs only to ISSUE override.
   */
  if (normalizedOptionalString(
      operation.overrideReason,
  )) {
    return invalidCorrection(
        "INVALID_CORRECTION",
        "A correction cannot contain FEFO override metadata.",
    );
  }


  if (!Array.isArray(
      operation.allocations,
  ) ||
      operation.allocations.length !== 1) {
    return invalidCorrection(
        "INVALID_ALLOCATION",
        "Correction must affect exactly one batch.",
    );
  }


  const allocation =
    operation.allocations[0];


  if (!allocation ||
      typeof allocation.batchId !==
          "string" ||
      allocation.batchId.trim()
          .length === 0) {
    return invalidCorrection(
        "INVALID_BATCH",
        "Correction batch ID is required.",
    );
  }


  if (!Number.isInteger(
      allocation.quantity,
  ) ||
      allocation.quantity <= 0) {
    return invalidCorrection(
        "INVALID_QUANTITY",
        "Correction quantity must be greater than zero.",
    );
  }


  if (allocation.quantity !==
      operation.requestedQuantity) {
    return invalidCorrection(
        "INVALID_QUANTITY",
        "Correction allocation must equal the requested quantity.",
    );
  }


  if (!Number.isInteger(
      allocation.allocationOrder,
  ) ||
      allocation.allocationOrder !== 0) {
    return invalidCorrection(
        "INVALID_ALLOCATION",
        "Correction allocation order is invalid.",
    );
  }


  return {
    valid: true,

    direction,

    reason,

    relatedOperationId,

    allocation: {
      batchId:
        allocation.batchId.trim(),

      quantity:
        allocation.quantity,
    },
  };
}


/**
 * Constructs the first-expiry-first-out allocation from trusted batch state rather
 * than blindly accepting the allocation a device suggested.
 */
function buildAuthoritativeFefo(
    snapshots,
    requestedQuantity,
)
{
  const eligible = [];


  let totalEligibleStock =
    0;


  for (const snapshot of snapshots) {
    const batch =
      snapshot.data;


    /*
     * Only ACTIVE batches can participate in FEFO.
     */
    if (!batch ||
        batch.status !==
            "ACTIVE") {
      continue;
    }


    if (!Number.isInteger(
        batch.quantityAvailable,
    ) ||
        batch.quantityAvailable < 0) {
      throw new Error(
          "Authoritative batch has an invalid quantity.",
      );
    }


    if (batch.quantityAvailable === 0) {
      continue;
    }


    const expiryMillis =
      firestoreTimestampMillis(
          batch.expiryDate,
      );


    const receivedMillis =
      firestoreTimestampMillis(
          batch.receivedDate,
      );


    if (expiryMillis === null ||
        receivedMillis === null) {
      throw new Error(
          "Authoritative active batch has invalid dates.",
      );
    }


    if (isExpiredBusinessDate(
        expiryMillis,
    )) {
      continue;
    }


    totalEligibleStock +=
      batch.quantityAvailable;


    eligible.push({
      batchId:
        snapshot.id,

      quantityAvailable:
        batch.quantityAvailable,

      expiryMillis,

      receivedMillis,
    });
  }


  if (totalEligibleStock <
      requestedQuantity) {
    return {
      sufficientStock: false,

      totalEligibleStock,

      allocations: [],
    };
  }


  /*
   * Same deterministic FEFO ordering as Android:
   *
   * expiryDate
   * receivedDate
   * batchId
   */
  eligible.sort(
      (left, right) => {
        if (left.expiryMillis !==
            right.expiryMillis) {
          return left.expiryMillis -
            right.expiryMillis;
        }


        if (left.receivedMillis !==
            right.receivedMillis) {
          return left.receivedMillis -
            right.receivedMillis;
        }


        return left.batchId.localeCompare(
            right.batchId,
        );
      },
  );


  const allocations = [];


  let remaining =
    requestedQuantity;


  for (const batch of eligible) {
    if (remaining <= 0) {
      break;
    }


    const quantity =
      Math.min(
          batch.quantityAvailable,
          remaining,
      );


    allocations.push({
      batchId:
        batch.batchId,

      quantity,
    });


    remaining -=
      quantity;
  }


  if (remaining !== 0) {
    throw new Error(
        "Authoritative FEFO allocation was inconsistent.",
    );
  }


  return {
    sufficientStock: true,

    totalEligibleStock,

    allocations,
  };
}


/**
 * Compares two allocations by batch and quantity while ignoring array order. The caller uses this
 * to determine whether a device's proposed issue follows the server's authoritative FEFO result;
 * batch identity and quantity still must match exactly.
 */
function allocationsMatch(
    left,
    right,
)
{
  if (left.length !==
      right.length) {
    return false;
  }


  const leftByBatch =
    allocationMap(
        left,
    );


  const rightByBatch =
    allocationMap(
        right,
    );


  if (leftByBatch.size !==
      rightByBatch.size) {
    return false;
  }


  for (const [batchId, quantity] of
    leftByBatch.entries()) {
    if (rightByBatch.get(
        batchId,
    ) !== quantity) {
      return false;
    }
  }


  return true;
}


/**
 * Indexes an allocation list by batch ID for order-independent comparison in
 * {@link allocationsMatch}. Allocation validation guarantees duplicate batch IDs were rejected
 * before this helper is used.
 */
function allocationMap(
    allocations,
)
{
  const result =
    new Map();


  for (const allocation of allocations) {
    result.set(
        allocation.batchId,
        allocation.quantity,
    );
  }


  return result;
}


/**
 * Resolves the employee permissions used to authorize this operation; a valid
 * login alone is not enough.
 */
function permissionsForUser(
    user,
)
{
  return Array.isArray(
      user.permissions,
  )
    ? user.permissions
    : [];
}


/**
 * Keeps the copy optional reason representation consistent for all callers.
 */
function copyOptionalReason(
    destination,
    reason,
)
{
  const normalized =
    normalizedOptionalString(
        reason,
    );


  if (normalized) {
    destination.reason =
      normalized;
  }
}


/**
 * Checks d optional string before the value reaches trusted storage. Invalid or
 * ambiguous input should fail here.
 */
function normalizedOptionalString(
    value,
)
{
  if (typeof value !==
      "string") {
    return null;
  }


  const normalized =
    value.trim();


  return normalized.length > 0
    ? normalized
    : null;
}


/**
 * Reads a Firestore timestamp as epoch milliseconds, also accepting timestamp-like test values
 * and positive integer timestamps. Unsupported or invalid date values return null, which
 * lets validation reject authoritative batch records with unusable dates.
 *
 * @param {*} value Firestore or compatible date value
 * @return {number|null} epoch milliseconds when the value is supported
 */
function firestoreTimestampMillis(
    value,
)
{
  if (value instanceof Timestamp) {
    return value.toMillis();
  }


  if (value &&
      typeof value.toMillis ===
          "function") {
    return value.toMillis();
  }


  /*
   * Support numeric timestamps defensively,
   * although cloud batch dates normally use
   * Firestore Timestamp.
   */
  if (Number.isInteger(
      value,
  ) &&
      value > 0) {
    return value;
  }


  return null;
}


/**
 * Builds the reject operation response so callers can distinguish a rejected
 * request from an infrastructure error.
 */
function rejectOperation(
    transaction,
    operationRef,
    operationId,
    code,
    message,
)
{
  transaction.update(
      operationRef,
      terminalFields(
          "REJECTED",
          code,
          message,
      ),
  );


  return operationResult(
      operationId,
      "REJECTED",
      code,
      message,
      true,
  );
}


/**
 * Builds the conflict operation response so callers can distinguish a rejected
 * request from an infrastructure error.
 */
function conflictOperation(
    transaction,
    operationRef,
    operationId,
    code,
    message,
)
{
  transaction.update(
      operationRef,
      terminalFields(
          "CONFLICT",
          code,
          message,
      ),
  );


  return operationResult(
      operationId,
      "CONFLICT",
      code,
      message,
      true,
  );
}


/**
 * Marks an operation confirmed when the transaction finds its already-created
 * transaction record. The processor validates that record's operation ID first;
 * this update records the idempotent replay result without applying stock a
 * second time.
 *
 * @param {Object} transaction active Firestore transaction
 * @param {Object} operationRef reference to the submitted operation document
 * @param {string} operationId stable client operation ID
 * @param {string} message replay explanation returned to the client
 * @return {Object} terminal confirmed result
 */
function confirmAlreadyProcessed(
    transaction,
    operationRef,
    operationId,
    message,
)
{
  const code =
    "ALREADY_PROCESSED";


  transaction.update(
      operationRef,
      terminalFields(
          "CONFIRMED",
          code,
          message,
      ),
  );


  return operationResult(
      operationId,
      "CONFIRMED",
      code,
      message,
      true,
  );
}


/**
 * Builds the code/message pair returned by operation-envelope validation. Keeping
 * this as a data result lets the transaction boundary decide whether malformed
 * input should be rejected or recorded as a terminal operation outcome.
 *
 * @param {string} code stable validation code
 * @param {string} message caller-facing explanation
 * @return {{code: string, message: string}} validation failure details
 */
function validationError(
    code,
    message,
)
{
  return {
    code,

    message,
  };
}


/**
 * Shapes a failed receipt-validation result for the receipt processor. The
 * caller uses this result to produce the operation's conflict/rejection response;
 * this helper itself does not update a batch or calculate accepted stock.
 *
 * @param {string} code receipt validation code
 * @param {string} message explanation associated with the failure
 * @return {{valid: boolean, code: string, message: string}} invalid receipt result
 */
function invalidReceipt(
    code,
    message,
)
{
  return {
    valid: false,

    code,

    message,
  };
}


/**
 * Shapes a failed issue-validation result consumed by the trusted issue flow.
 * Availability, expiry order, and allocation checks are performed by the caller;
 * this helper only preserves their error code and message.
 *
 * @param {string} code issue validation code
 * @param {string} message explanation associated with the failure
 * @return {{valid: boolean, code: string, message: string}} invalid issue result
 */
function invalidIssue(
    code,
    message,
)
{
  return {
    valid: false,

    code,

    message,
  };
}

/**
 * Packages an adjustment-validation failure for the operation processor. The
 * adjustment validator performs quantity and batch checks; this helper only
 * returns a consistent invalid-result shape for the caller to handle.
 *
 * @param {string} code adjustment validation code
 * @param {string} message explanation associated with the failure
 * @return {{valid: boolean, code: string, message: string}} invalid adjustment result
 */
function invalidAdjustment(
    code,
    message,
)
{
  return {
    valid: false,

    code,

    message,
  };
}

/**
 * Packages a correction-validation failure for the operation processor. The
 * correction validator performs quantity and batch checks; this helper only
 * returns a consistent invalid-result shape for the caller to handle.
 *
 * @param {string} code correction validation code
 * @param {string} message explanation associated with the failure
 * @return {{valid: boolean, code: string, message: string}} invalid correction result
 */
function invalidCorrection(
    code,
    message,
)
{
  return {
    valid: false,

    code,

    message,
  };
}


/**
 * Produces the common operation fields written when processing reaches a terminal status. Server
 * timestamps are assigned here so retries do not copy device clocks into authoritative processing
 * time; result details remain available for clients reconciling pending local operations.
 */
function terminalFields(
    status,
    resultCode,
    resultMessage,
)
{
  return {
    status,

    serverCreatedAt:
      FieldValue.serverTimestamp(),

    processedAt:
      FieldValue.serverTimestamp(),

    resultCode,

    resultMessage,
  };
}


/**
 * Shapes the processor response returned to the API caller. The processedNow flag distinguishes a
 * newly written terminal result from an idempotent replay of an operation already finalized.
 */
function operationResult(
    operationId,
    status,
    resultCode,
    resultMessage,
    processedNow,
)
{
  return {
    operationId,

    status,

    resultCode:
      resultCode || null,

    resultMessage:
      resultMessage || null,

    processedNow,
  };
}


/**
 * Compares an expiry instant with today's date in the configured inventory
 * business timezone. Comparing calendar-date keys avoids treating stock as
 * expired partway through its expiry day because of the server's UTC clock.
 *
 * @param {number} expiryMillis batch expiry timestamp in milliseconds
 * @return {boolean} whether the expiry calendar date is before today's business date
 */
function isExpiredBusinessDate(
    expiryMillis,
)
{
  const expiryDate =
    businessDateKey(
        expiryMillis,
    );


  const today =
    businessDateKey(
        Date.now(),
    );


  return expiryDate < today;
}


/**
 * Formats a timestamp as a sortable year-month-day key in the configured
 * business timezone. This gives expiry validation the same calendar boundary
 * across devices and backend hosts regardless of their local timezone.
 *
 * @param {number} millis timestamp to convert
 * @return {string} zero-padded business date key
 */
function businessDateKey(
    millis,
)
{
  const formatter =
    new Intl.DateTimeFormat(
        "en-CA",
        {
          timeZone:
            BUSINESS_TIME_ZONE,

          year:
            "numeric",

          month:
            "2-digit",

          day:
            "2-digit",
        },
    );


  const parts =
    formatter.formatToParts(
        new Date(millis),
    );


  const values = {};


  for (const part of parts) {
    if (part.type !==
        "literal") {
      values[part.type] =
        part.value;
    }
  }


  return `${values.year}-${values.month}-${values.day}`;
}


module.exports = {
  processInventoryOperation,
  ProcessorHttpError,
};