package com.medicationinventory.data.repository;

import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.AlertDao;
import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.dao.SyncMetadataDao;

import com.medicationinventory.data.local.database.AppDatabase;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;

import com.medicationinventory.data.repository.SyncMetadataRepository;

import com.medicationinventory.model.dto.InventoryReconciliationResult;

import com.medicationinventory.model.enums.BatchStatus;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Applies only terminal results to the matching local operation inside one Room transaction. It validates result identity and affected batches, then updates confirmed or rejected local rows, queue state, and sync metadata together so observers cannot see a partially reconciled operation.
 */
public class InventoryReconciliationRepository
{
    private final AppDatabase database;

    private final BatchDao batchDao;

    private final AlertDao alertDao;

    private final AllocationDao allocationDao;

    private final InventoryOperationDao operationDao;

    private final TransactionDao transactionDao;

    private final SyncQueueDao syncQueueDao;

    private final SyncMetadataDao syncMetadataDao;

    public InventoryReconciliationRepository(
            AppDatabase database)
    {
        this.database =
                database;


        this.batchDao =
                database.batchDao();

        this.alertDao = database.alertDao();

        this.allocationDao =
                database.allocationDao();


        this.operationDao =
                database.inventoryOperationDao();


        this.transactionDao =
                database.transactionDao();


        this.syncQueueDao =
                database.syncQueueDao();


        this.syncMetadataDao =
                database.syncMetadataDao();
    }


    /**
     * Applies a terminal trusted result atomically to the corresponding local operation and related inventory rows.
     * Non-terminal or malformed results are rejected before entering the Room transaction.
     * @param result trusted reconciliation result; must include an operation ID and terminal status
     * @throws IllegalArgumentException when required result identity or terminal status is absent
     * @throws IllegalStateException when the local operation is missing or unsupported
     */
    public void reconcile(
            InventoryReconciliationResult result)
    {
        if (result == null)
        {
            throw new IllegalArgumentException(
                    "Reconciliation result is required."
            );
        }


        if (result.getOperationId() == null
                || result
                .getOperationId()
                .trim()
                .isEmpty())
        {
            throw new IllegalArgumentException(
                    "Reconciliation operation ID is required."
            );
        }


        if (!isTerminal(
                result.getStatus()
        ))
        {
            throw new IllegalArgumentException(
                    "Reconciliation requires a terminal operation."
            );
        }


        database.runInTransaction(
                () ->
                        reconcileInsideTransaction(
                                result
                        )
        );
    }


    private void reconcileInsideTransaction(
            InventoryReconciliationResult result)
    {
        InventoryOperationEntity localOperation =
                operationDao.getById(
                        result.getOperationId()
                );


        if (localOperation == null)
        {
            throw new IllegalStateException(
                    "Local inventory operation was not found."
            );
        }


        InventoryOperationType operationType =
                localOperation.getOperationType();


        if (operationType
                != InventoryOperationType.RECEIPT
                &&
                operationType
                        != InventoryOperationType.ISSUE
                &&
                operationType
                        != InventoryOperationType.ADJUSTMENT
                &&
                operationType
                        != InventoryOperationType.CORRECTION)
        {
            throw new IllegalStateException(
                    "This operation type is not supported by reconciliation yet."
            );
        }


        List<String> affectedBatchIds =
                result.getAffectedBatchIds();


        validateAffectedBatchIds(
                affectedBatchIds
        );


        Integer expectedAdjustmentQuantityChange =
                null;

        Integer expectedCorrectionQuantityChange =
                null;


        if (operationType
                == InventoryOperationType.ADJUSTMENT)
        {
            expectedAdjustmentQuantityChange =
                    getExpectedLocalAdjustmentQuantityChange(
                            localOperation,
                            affectedBatchIds
                    );
        }

        if (operationType
                == InventoryOperationType.CORRECTION)
        {
            expectedCorrectionQuantityChange =
                    getExpectedLocalCorrectionQuantityChange(
                            localOperation,
                            affectedBatchIds
                    );
        }


        Map<String,
                InventoryReconciliationResult.AuthoritativeBatch>
                cloudBatchesById =
                new HashMap<>();


        List<InventoryReconciliationResult.AuthoritativeBatch>
                authoritativeBatches =
                result.getBatches();


        if (authoritativeBatches != null)
        {
            for (
                    InventoryReconciliationResult.AuthoritativeBatch batch :
                    authoritativeBatches)
            {
                if (batch == null)
                {
                    throw new IllegalStateException(
                            "Reconciliation contains an invalid authoritative batch."
                    );
                }


                String batchId =
                        batch.getBatchId();


                if (batchId == null
                        || batchId
                        .trim()
                        .isEmpty())
                {
                    throw new IllegalStateException(
                            "Authoritative batch ID is missing."
                    );
                }


                if (cloudBatchesById.put(
                        batchId,
                        batch
                ) != null)
                {
                    throw new IllegalStateException(
                            "Duplicate authoritative batch returned by reconciliation."
                    );
                }
            }
        }


        boolean failedReceipt =
                operationType == InventoryOperationType.RECEIPT
                        && (result.getStatus() == InventoryOperationStatus.CONFLICT
                        || result.getStatus() == InventoryOperationStatus.REJECTED);

        boolean failedNewBatchReceipt = failedReceipt
                && !"EXISTING_BATCH".equals(localOperation.getReceiptMode());

        if (failedReceipt) {
            transactionDao.deleteProvisionalForOperation(result.getOperationId());
        }


        for (String affectedBatchId :
                affectedBatchIds)
        {
            InventoryReconciliationResult.AuthoritativeBatch
                    cloudBatch =
                    cloudBatchesById.get(
                            affectedBatchId
                    );


            if (cloudBatch != null)
            {
                batchDao.upsert(
                        toBatchEntity(
                                cloudBatch
                        )
                );


                continue;
            }


            if (failedNewBatchReceipt)
            {
                BatchEntity localBatch = batchDao.getById(affectedBatchId);
                if (localBatch != null && !localOperation.getMedicineId()
                        .equals(localBatch.getMedicineId())) {
                    throw new IllegalStateException(
                            "Rejected receipt references a batch from another medicine."
                    );
                }

                alertDao.deleteForRejectedProvisionalBatch(affectedBatchId);

                allocationDao.deleteForRejectedReceiptBatch(
                        result.getOperationId(), affectedBatchId
                );

                batchDao.deleteById(affectedBatchId);


                continue;
            }


            if (failedReceipt && !failedNewBatchReceipt) {

                continue;
            }
            throw new IllegalStateException(
                    "Authoritative affected batch is missing from reconciliation."
            );
        }


        if (!failedReceipt) {
            transactionDao.deleteProvisionalForOperation(result.getOperationId());
        }


        if (result.getStatus()
                == InventoryOperationStatus.CONFIRMED)
        {
            List<InventoryReconciliationResult.AuthoritativeTransaction>
                    transactions =
                    result.getTransactions();


            if (transactions == null
                    || transactions.isEmpty())
            {
                throw new IllegalStateException(
                        "Confirmed operation is missing its authoritative transactions."
                );
            }


            validateConfirmedTransactions(
                    localOperation,
                    affectedBatchIds,
                    transactions,
                    expectedAdjustmentQuantityChange,
                    expectedCorrectionQuantityChange
            );


            for (
                    InventoryReconciliationResult.AuthoritativeTransaction
                            transaction :
                    transactions)
            {
                transactionDao.upsertConfirmed(
                        toTransactionEntity(
                                transaction
                        )
                );
            }
        }
        else
        {

            List<InventoryReconciliationResult.AuthoritativeTransaction>
                    transactions =
                    result.getTransactions();


            if (transactions != null
                    && !transactions.isEmpty())
            {
                throw new IllegalStateException(
                        "Rejected or conflicted operation returned authoritative transactions."
                );
            }
        }


        operationDao.updateStatus(
                result.getOperationId(),
                result.getStatus(),
                result.getResultCode(),
                result.getResultMessage()
        );


        syncQueueDao
                .removeAfterReconciliation(
                        result.getOperationId()
                );

        long lastServerTimestamp =
                result.getProcessedAt() > 0
                        ? result.getProcessedAt()
                        : result.getServerCreatedAt();


        syncMetadataDao.upsert(
                new SyncMetadataEntity(
                        SyncMetadataRepository.KEY_INVENTORY_SYNC,
                        System.currentTimeMillis(),
                        Math.max(
                                0L,
                                lastServerTimestamp
                        ),
                        result.getOperationId()
                )
        );
    }


    private void validateAffectedBatchIds(
            List<String> affectedBatchIds)
    {
        if (affectedBatchIds == null
                || affectedBatchIds.isEmpty())
        {
            throw new IllegalStateException(
                    "Reconciliation contains no affected batches."
            );
        }


        Set<String> uniqueBatchIds =
                new HashSet<>();


        for (String batchId :
                affectedBatchIds)
        {
            if (batchId == null
                    || batchId.trim().isEmpty())
            {
                throw new IllegalStateException(
                        "Reconciliation contains an invalid affected batch ID."
                );
            }


            if (!uniqueBatchIds.add(
                    batchId
            ))
            {
                throw new IllegalStateException(
                        "Reconciliation contains a duplicate affected batch."
                );
            }
        }
    }


    private void validateConfirmedTransactions(
            InventoryOperationEntity localOperation,
            List<String> affectedBatchIds,
            List<InventoryReconciliationResult.AuthoritativeTransaction>
                    transactions,
            Integer expectedAdjustmentQuantityChange,
            Integer expectedCorrectionQuantityChange)
    {
        Set<String> affectedBatchIdSet =
                new HashSet<>(
                        affectedBatchIds
                );


        Set<String> transactionIds =
                new HashSet<>();


        Set<String> transactionBatchIds =
                new HashSet<>();


        long signedTotal =
                0L;


        String expectedTransactionType =
                localOperation
                        .getOperationType()
                        .name();


        for (
                InventoryReconciliationResult.AuthoritativeTransaction
                        transaction :
                transactions)
        {
            if (transaction == null)
            {
                throw new IllegalStateException(
                        "Authoritative transaction is invalid."
                );
            }


            if (transaction.getTransactionId() == null
                    || transaction
                    .getTransactionId()
                    .trim()
                    .isEmpty())
            {
                throw new IllegalStateException(
                        "Authoritative transaction ID is missing."
                );
            }


            if (!localOperation
                    .getOperationId()
                    .equals(
                            transaction.getOperationId()
                    ))
            {
                throw new IllegalStateException(
                        "Authoritative transaction belongs to another operation."
                );
            }


            if (!localOperation
                    .getMedicineId()
                    .equals(
                            transaction.getMedicineId()
                    ))
            {
                throw new IllegalStateException(
                        "Authoritative transaction belongs to another medicine."
                );
            }


            if (!affectedBatchIdSet.contains(
                    transaction.getBatchId()
            ))
            {
                throw new IllegalStateException(
                        "Authoritative transaction references an unexpected batch."
                );
            }


            if (!transactionIds.add(
                    transaction.getTransactionId()
            ))
            {
                throw new IllegalStateException(
                        "Duplicate authoritative transaction returned."
                );
            }


            if (!transactionBatchIds.add(
                    transaction.getBatchId()
            ))
            {
                throw new IllegalStateException(
                        "Multiple authoritative transactions reference the same batch."
                );
            }


            if (!expectedTransactionType.equals(
                    transaction.getTransactionType()
            ))
            {
                throw new IllegalStateException(
                        "Authoritative transaction type does not match the operation."
                );
            }


            int quantityChange =
                    transaction.getQuantityChange();


            /*
             * RECEIPT
             */
            if (localOperation.getOperationType()
                    == InventoryOperationType.RECEIPT)
            {
                if (quantityChange <= 0)
                {
                    throw new IllegalStateException(
                            "Confirmed receipt transaction must increase stock."
                    );
                }
            }


            else if (localOperation.getOperationType()
                    == InventoryOperationType.ISSUE)
            {
                if (quantityChange >= 0)
                {
                    throw new IllegalStateException(
                            "Confirmed issue transaction must decrease stock."
                    );
                }
            }


            else if (localOperation.getOperationType()
                    == InventoryOperationType.ADJUSTMENT)
            {
                if (quantityChange == 0)
                {
                    throw new IllegalStateException(
                            "Confirmed adjustment transaction cannot have zero quantity."
                    );
                }
            }

            else if (localOperation.getOperationType()
                    == InventoryOperationType.CORRECTION)
            {
                if (quantityChange == 0)
                {
                    throw new IllegalStateException(
                            "Confirmed correction transaction cannot have zero quantity."
                    );
                }
            }


            signedTotal +=
                    quantityChange;
        }


        if (!transactionBatchIds.equals(
                affectedBatchIdSet
        ))
        {
            throw new IllegalStateException(
                    "Authoritative transactions do not cover all affected batches."
            );
        }


        long expectedSignedTotal;


        if (localOperation.getOperationType()
                == InventoryOperationType.ISSUE)
        {
            expectedSignedTotal =
                    -(long) localOperation
                            .getRequestedQuantity();
        }


        else if (localOperation.getOperationType()
                == InventoryOperationType.ADJUSTMENT)
        {
            if (expectedAdjustmentQuantityChange == null)
            {
                throw new IllegalStateException(
                        "Local adjustment direction is unavailable."
                );
            }


            expectedSignedTotal =
                    expectedAdjustmentQuantityChange;
        }


        else if (localOperation.getOperationType()
                == InventoryOperationType.CORRECTION)
        {
            if (expectedCorrectionQuantityChange == null)
            {
                throw new IllegalStateException(
                        "Local correction direction is unavailable."
                );
            }


            expectedSignedTotal =
                    expectedCorrectionQuantityChange;
        }


        else
        {
            expectedSignedTotal =
                    localOperation
                            .getRequestedQuantity();
        }


        if (signedTotal
                != expectedSignedTotal)
        {
            throw new IllegalStateException(
                    "Authoritative transaction total does not match the operation."
            );
        }
    }




    private int getExpectedLocalAdjustmentQuantityChange(
            InventoryOperationEntity localOperation,
            List<String> affectedBatchIds)
    {
        if (localOperation.getOperationType()
                != InventoryOperationType.ADJUSTMENT)
        {
            throw new IllegalArgumentException(
                    "Operation is not an adjustment."
            );
        }


        if (affectedBatchIds == null
                || affectedBatchIds.size() != 1)
        {
            throw new IllegalStateException(
                    "Adjustment reconciliation must affect exactly one batch."
            );
        }


        if (localOperation.getRequestedQuantity() <= 0)
        {
            throw new IllegalStateException(
                    "Local adjustment requested quantity is invalid."
            );
        }


        if (localOperation.getReason() == null
                || localOperation
                .getReason()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Local adjustment reason is missing."
            );
        }


        List<InventoryTransactionEntity>
                localTransactions =
                transactionDao.getForOperation(
                        localOperation.getOperationId()
                );


        if (localTransactions == null
                || localTransactions.size() != 1)
        {
            throw new IllegalStateException(
                    "Local adjustment must contain exactly one provisional transaction."
            );
        }


        InventoryTransactionEntity transaction =
                localTransactions.get(
                        0
                );


        if (!transaction.isProvisional())
        {
            throw new IllegalStateException(
                    "Local adjustment movement is not provisional."
            );
        }


        if (!"ADJUSTMENT".equals(
                transaction.getTransactionType()
        ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement type is invalid."
            );
        }


        if (!localOperation
                .getOperationId()
                .equals(
                        transaction.getOperationId()
                ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement belongs to another operation."
            );
        }


        if (!localOperation
                .getMedicineId()
                .equals(
                        transaction.getMedicineId()
                ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement belongs to another medicine."
            );
        }


        if (!affectedBatchIds
                .get(0)
                .equals(
                        transaction.getBatchId()
                ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement references another batch."
            );
        }


        if (!localOperation
                .getPerformedBy()
                .equals(
                        transaction.getPerformedBy()
                ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement belongs to another user."
            );
        }


        if (transaction.getReason() == null
                || transaction
                .getReason()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Local adjustment movement reason is missing."
            );
        }


        if (!localOperation
                .getReason()
                .trim()
                .equals(
                        transaction
                                .getReason()
                                .trim()
                ))
        {
            throw new IllegalStateException(
                    "Local adjustment movement reason does not match the operation."
            );
        }


        int quantityChange =
                transaction.getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalStateException(
                    "Local adjustment movement quantity cannot be zero."
            );
        }


        if (Math.abs(
                (long) quantityChange
        )
                != localOperation
                .getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Local adjustment movement quantity is invalid."
            );
        }


        return quantityChange;
    }


    private int getExpectedLocalCorrectionQuantityChange(
            InventoryOperationEntity localOperation,
            List<String> affectedBatchIds)
    {
        if (localOperation.getOperationType()
                != InventoryOperationType.CORRECTION)
        {
            throw new IllegalArgumentException(
                    "Operation is not a correction."
            );
        }


        if (affectedBatchIds == null
                || affectedBatchIds.size() != 1)
        {
            throw new IllegalStateException(
                    "Correction reconciliation must affect exactly one batch."
            );
        }


        if (localOperation.getRequestedQuantity() <= 0)
        {
            throw new IllegalStateException(
                    "Local correction requested quantity is invalid."
            );
        }


        if (localOperation.getReason() == null
                ||
                localOperation
                        .getReason()
                        .trim()
                        .isEmpty())
        {
            throw new IllegalStateException(
                    "Local correction reason is missing."
            );
        }


        if (localOperation.getRelatedOperationId() == null
                ||
                localOperation
                        .getRelatedOperationId()
                        .trim()
                        .isEmpty())
        {
            throw new IllegalStateException(
                    "Local correction original operation reference is missing."
            );
        }


        String relatedOperationId =
                localOperation
                        .getRelatedOperationId()
                        .trim();


        if (localOperation
                .getOperationId()
                .equals(
                        relatedOperationId
                ))
        {
            throw new IllegalStateException(
                    "Correction cannot reference itself."
            );
        }


        InventoryOperationEntity originalOperation =
                operationDao
                        .getById(
                                relatedOperationId
                        );


        if (originalOperation == null)
        {
            throw new IllegalStateException(
                    "Original correction operation is unavailable locally."
            );
        }


        if (originalOperation.getStatus()
                != InventoryOperationStatus.CONFIRMED)
        {
            throw new IllegalStateException(
                    "Original correction operation is not confirmed."
            );
        }


        if (!localOperation
                .getMedicineId()
                .equals(
                        originalOperation
                                .getMedicineId()
                ))
        {
            throw new IllegalStateException(
                    "Correction and original operation belong to different medicines."
            );
        }


        List<InventoryTransactionEntity> localTransactions =
                transactionDao
                        .getForOperation(
                                localOperation.getOperationId()
                        );


        if (localTransactions == null
                || localTransactions.size() != 1)
        {
            throw new IllegalStateException(
                    "Local correction must contain exactly one provisional transaction."
            );
        }


        InventoryTransactionEntity transaction =
                localTransactions.get(
                        0
                );


        if (!transaction.isProvisional())
        {
            throw new IllegalStateException(
                    "Local correction movement is not provisional."
            );
        }


        if (!"CORRECTION".equals(
                transaction.getTransactionType()
        ))
        {
            throw new IllegalStateException(
                    "Local correction movement type is invalid."
            );
        }


        if (!localOperation
                .getOperationId()
                .equals(
                        transaction.getOperationId()
                ))
        {
            throw new IllegalStateException(
                    "Local correction movement belongs to another operation."
            );
        }


        if (!localOperation
                .getMedicineId()
                .equals(
                        transaction.getMedicineId()
                ))
        {
            throw new IllegalStateException(
                    "Local correction movement belongs to another medicine."
            );
        }


        if (!affectedBatchIds
                .get(0)
                .equals(
                        transaction.getBatchId()
                ))
        {
            throw new IllegalStateException(
                    "Local correction movement references another batch."
            );
        }


        if (!localOperation
                .getPerformedBy()
                .equals(
                        transaction.getPerformedBy()
                ))
        {
            throw new IllegalStateException(
                    "Local correction movement belongs to another user."
            );
        }


        if (transaction.getReason() == null
                ||
                transaction
                        .getReason()
                        .trim()
                        .isEmpty())
        {
            throw new IllegalStateException(
                    "Local correction movement reason is missing."
            );
        }


        if (!localOperation
                .getReason()
                .trim()
                .equals(
                        transaction
                                .getReason()
                                .trim()
                ))
        {
            throw new IllegalStateException(
                    "Local correction movement reason does not match the operation."
            );
        }


        int quantityChange =
                transaction
                        .getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalStateException(
                    "Local correction movement quantity cannot be zero."
            );
        }


        if (Math.abs(
                (long) quantityChange
        )
                != localOperation
                .getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Local correction movement quantity is invalid."
            );
        }


        return quantityChange;
    }


    private BatchEntity toBatchEntity(
            InventoryReconciliationResult.AuthoritativeBatch batch)
    {
        if (batch == null)
        {
            throw new IllegalArgumentException(
                    "Authoritative batch is required."
            );
        }


        if (batch.getBatchId() == null
                || batch
                .getBatchId()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Authoritative batch ID is missing."
            );
        }


        if (batch.getMedicineId() == null
                || batch
                .getMedicineId()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Authoritative medicine ID is missing."
            );
        }


        if (batch.getBatchNumber() == null
                || batch
                .getBatchNumber()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Authoritative batch number is missing."
            );
        }


        if (batch.getQuantityAvailable() < 0)
        {
            throw new IllegalStateException(
                    "Authoritative batch quantity cannot be negative."
            );
        }


        if (batch.getInitialQuantity() < 0)
        {
            throw new IllegalStateException(
                    "Authoritative initial quantity cannot be negative."
            );
        }


        BatchStatus status;


        try
        {
            status =
                    BatchStatus.valueOf(
                            batch.getStatus()
                    );
        }
        catch (Exception exception)
        {
            throw new IllegalStateException(
                    "Unsupported authoritative batch status: "
                            + batch.getStatus(),
                    exception
            );
        }


        return new BatchEntity(
                batch.getBatchId(),
                batch.getMedicineId(),
                batch.getBatchNumber(),
                batch.getReceivedDate(),
                batch.getExpiryDate(),
                batch.getQuantityAvailable(),
                batch.getInitialQuantity(),
                status,
                batch.getUpdatedAt()
        );
    }




    private InventoryTransactionEntity
    toTransactionEntity(
            InventoryReconciliationResult.AuthoritativeTransaction transaction)
    {
        if (transaction == null)
        {
            throw new IllegalArgumentException(
                    "Authoritative transaction is required."
            );
        }


        return new InventoryTransactionEntity(
                transaction.getTransactionId(),
                transaction.getOperationId(),
                transaction.getMedicineId(),
                transaction.getBatchId(),
                transaction.getTransactionType(),
                transaction.getQuantityChange(),
                transaction.getReason(),
                transaction.getPerformedBy(),
                transaction.getClientCreatedAt(),
                transaction.getServerCreatedAt(),
                false
        );
    }




    private boolean isTerminal(
            InventoryOperationStatus status)
    {
        return status
                == InventoryOperationStatus.CONFIRMED
                ||
                status
                        == InventoryOperationStatus.CONFLICT
                ||
                status
                        == InventoryOperationStatus.REJECTED;
    }
}