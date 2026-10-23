package com.medicationinventory.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.TypeConverters;

import com.medicationinventory.data.local.converter.BatchStatusConverter;
import com.medicationinventory.data.local.converter.InventoryOperationConverter;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;
import com.medicationinventory.model.enums.BatchStatus;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Defines Room transactions that commit local receipts, issues, adjustments, and corrections together with their operation, allocations, movements, and sync queue row. Issue and correction paths recheck current batch and history state inside the transaction so stale UI selections cannot silently alter stock.
 */
@Dao
@TypeConverters({InventoryOperationConverter.class, BatchStatusConverter.class})
public interface StockOperationDao
{
    ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Johannesburg");


    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertOperation(InventoryOperationEntity operation);

    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertBatch(BatchEntity batch);


    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertAllocation(OperationAllocationEntity allocation);

    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertTransaction(InventoryTransactionEntity transaction);


    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertQueueItem(SyncQueueEntity queueItem);


    @Query("SELECT * FROM batches " + "WHERE medicineId = :medicineId " +
            "AND quantityAvailable > 0 " + "AND status = 'ACTIVE' " +
            "ORDER BY expiryDate ASC, " + "receivedDate ASC, batchId ASC")

    List<BatchEntity> getIssueCandidates(String medicineId);

    @Query("SELECT * FROM batches " + "WHERE batchId = :batchId " + "LIMIT 1")

    BatchEntity getAdjustmentBatch(String batchId);

    @Query("SELECT * FROM inventory_operations " + "WHERE operationId = :operationId " + "LIMIT 1")

    InventoryOperationEntity getCorrectionOriginalOperation(String operationId);


    @Query("SELECT COUNT(*) FROM inventory_transactions " + "WHERE operationId = :operationId " +
            "AND batchId = :batchId " + "AND provisional = 0")

    int countConfirmedOriginalTransactionsForBatch(String operationId, String batchId);


    @Query("UPDATE batches " + "SET quantityAvailable = :quantityAvailable, " +
            "status = :status, " + "updatedAt = :updatedAt " + "WHERE batchId = :batchId")

    int updateBatchAfterIssue(String batchId, int quantityAvailable, BatchStatus status, long updatedAt);

    @Query("UPDATE batches " + "SET quantityAvailable = :quantityAvailable, " +
            "status = :status, " + "updatedAt = :updatedAt " + "WHERE batchId = :batchId")

    int updateBatchAfterAdjustment(String batchId, int quantityAvailable, BatchStatus status, long updatedAt);



    /**
     * Atomically records a new lot receipt as an operation, newly created batch, allocation,
     * provisional movement, and sync queue row. Keeping all writes in one Room transaction prevents
     * a partially stored receipt from being uploaded or counted locally.
     *
     * @param operation pending receipt envelope
     * @param batch new local lot created for this receipt
     * @param allocation quantity assigned to the new lot
     * @param transaction provisional receipt movement
     * @param queueItem retry metadata that schedules trusted processing
     */
    @Transaction

    default void commitNewBatchReceipt(
            InventoryOperationEntity operation,
            BatchEntity batch,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity transaction,
            SyncQueueEntity queueItem)
    {


        insertOperation(operation);


        insertBatch(batch);


        insertAllocation(allocation);


        insertTransaction(transaction);


        insertQueueItem(queueItem);
    }


    /**
     * Persists an explicit top-up against an existing eligible lot without creating a second batch
     * row. It checks receipt mode, operation/allocation/queue identity, medicine ownership, active
     * or depleted status, and non-expiry before writing; authoritative quantity changes are applied
     * by trusted processing.
     *
     * @param operation pending receipt marked as an existing-batch top-up
     * @param allocation selected existing batch and received quantity
     * @param queueItem retry metadata for trusted processing
     */
    @Transaction

    default void commitExistingBatchReceipt(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            SyncQueueEntity queueItem)
    {
        if (operation.getOperationType() != InventoryOperationType.RECEIPT
                || !"EXISTING_BATCH".equals(operation.getReceiptMode())
                || operation.getRequestedQuantity() <= 0
                || allocation.getQuantity() != operation.getRequestedQuantity()
                || !operation.getOperationId().equals(allocation.getOperationId())
                || !operation.getOperationId().equals(queueItem.getOperationId()))
        {
            throw new IllegalArgumentException("Invalid existing-batch receipt operation.");
        }
        BatchEntity existing = getAdjustmentBatch(allocation.getBatchId());
        if (existing == null || !operation.getMedicineId().equals(existing.getMedicineId()))
        {
            throw new IllegalStateException("Selected existing batch is unavailable.");
        }
        if (existing.getStatus() != BatchStatus.ACTIVE
                && existing.getStatus() != BatchStatus.DEPLETED)
        {
            throw new IllegalStateException("Selected batch is not eligible for receiving.");
        }
        LocalDate expiry = Instant.ofEpochMilli(existing.getExpiryDate())
                .atZone(BUSINESS_ZONE).toLocalDate();
        if (expiry.isBefore(LocalDate.now(BUSINESS_ZONE)))
        {
            throw new IllegalStateException("An expired batch cannot receive stock.");
        }
        insertOperation(operation);
        insertAllocation(allocation);
        insertQueueItem(queueItem);
    }


    /**
     * Rechecks the issue's operation, allocation order, quantities, ownership, and current batch
     * availability before applying the local provisional stock reduction. Operation, allocations,
     * movement rows, batch quantities, and queue state commit together or roll back as one unit.
     *
     * @param operation pending issue request
     * @param allocations ordered batch allocation proposal
     * @param transactions provisional negative movement rows
     * @param queueItem retry metadata for trusted processing
     * @param fefoOverrideAuthorized whether the caller supplied the separately authorized override
     */
    @Transaction

    default void commitIssue(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations,
            List<InventoryTransactionEntity> transactions,
            SyncQueueEntity queueItem,
            boolean fefoOverrideAuthorized)
    {
        validateIssueEnvelope(
                operation,
                allocations,
                transactions,
                queueItem,
                fefoOverrideAuthorized
        );


        List<BatchEntity> candidates = getIssueCandidates(operation.getMedicineId());


        LocalDate today = LocalDate.now(BUSINESS_ZONE);


        List<BatchEntity> eligible = new ArrayList<>();


        for (BatchEntity batch :
                candidates)
        {
            if (batch == null)
            {
                continue;
            }


            if (batch.getQuantityAvailable() <= 0)
            {
                continue;
            }


            if (batch.getStatus() != BatchStatus.ACTIVE)
            {
                continue;
            }


            LocalDate expiry = Instant.ofEpochMilli(batch.getExpiryDate()).atZone(BUSINESS_ZONE).toLocalDate();


            if (expiry.isBefore(today))
            {
                continue;
            }


            eligible.add(batch);
        }


        eligible.sort(Comparator.comparingLong(BatchEntity::getExpiryDate).thenComparingLong(
                BatchEntity::getReceivedDate).thenComparing(BatchEntity::getBatchId));


        List<ExpectedAllocation> expected = calculateExpectedFefo(eligible, operation.getRequestedQuantity());


        Map<String, BatchEntity> eligibleById = new HashMap<>();


        for (BatchEntity batch : eligible)
        {
            eligibleById.put(batch.getBatchId(), batch);
        }


        boolean matchesFefo = matchesCurrentFefo(allocations, expected);


        boolean hasOverrideReason = hasText(operation.getOverrideReason());



        if (!matchesFefo)
        {
            if (!hasOverrideReason)
            {
                throw new IllegalStateException("FEFO_OVERRIDE_REASON_REQUIRED");
            }


            if (!fefoOverrideAuthorized)
            {
                throw new SecurityException("FEFO_OVERRIDE_NOT_ALLOWED");
            }

            validateOverrideAllocations(allocations, eligibleById);
        }
        else if (hasOverrideReason
                && !fefoOverrideAuthorized)
        {

            throw new SecurityException("FEFO_OVERRIDE_NOT_ALLOWED");
        }



        insertOperation(operation);


        for (int index = 0; index < allocations.size(); index++)
        {
            OperationAllocationEntity allocation = allocations.get(index);


            InventoryTransactionEntity inventoryTransaction = transactions.get(index);

            BatchEntity batch = eligibleById.get(allocation.getBatchId());


            if (batch == null)
            {

                throw new IllegalStateException("INVALID_BATCH");
            }


            if (allocation.getQuantity() >
                    batch.getQuantityAvailable())
            {
                throw new IllegalStateException("INSUFFICIENT_STOCK");
            }


            int quantityAfterIssue = batch.getQuantityAvailable() - allocation.getQuantity();


            if (quantityAfterIssue < 0)
            {

                throw new IllegalStateException("NEGATIVE_STOCK");
            }


            BatchStatus newStatus = quantityAfterIssue == 0 ? BatchStatus.DEPLETED : BatchStatus.ACTIVE;


            insertAllocation(allocation);

            insertTransaction(inventoryTransaction);


            int rowsUpdated = updateBatchAfterIssue(batch.getBatchId(), quantityAfterIssue, newStatus,
                            operation.getClientCreatedAt());


            if (rowsUpdated != 1)
            {
                throw new IllegalStateException("BATCH_UPDATE_FAILED");
            }
        }


        insertQueueItem(queueItem);
    }

    /**
     * Applies the validated adjustment to its target batch and writes the operation, allocation,
     * provisional movement, and queue row atomically. The stored direction determines whether the
     * quantity is added or removed; any validation failure rolls back the entire local commit.
     *
     * @param operation pending adjustment envelope
     * @param allocation target batch and absolute adjustment quantity
     * @param transaction provisional signed movement row
     * @param queueItem retry metadata for trusted processing
     */
    @Transaction

    default void commitAdjustment(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity inventoryTransaction,
            SyncQueueEntity queueItem)
    {
        validateAdjustmentEnvelope(operation, allocation, inventoryTransaction, queueItem);


        BatchEntity batch = getAdjustmentBatch(allocation.getBatchId());


        if (batch == null)
        {
            throw new IllegalStateException("INVALID_BATCH");
        }


        if (!operation.getMedicineId().equals(batch.getMedicineId()))
        {
            throw new IllegalStateException("INVALID_BATCH");
        }


        int signedQuantity = inventoryTransaction.getQuantityChange();


        long quantityAfterLong = (long) batch.getQuantityAvailable() + signedQuantity;


        if (quantityAfterLong < 0)
        {
            throw new IllegalStateException("NEGATIVE_STOCK");
        }


        if (quantityAfterLong >
                Integer.MAX_VALUE)
        {
            throw new IllegalStateException("INVALID_QUANTITY");
        }


        int quantityAfter = (int) quantityAfterLong;


        BatchStatus newStatus = deriveAdjustmentBatchStatus(batch, quantityAfter);


        insertOperation(operation);


        insertAllocation(allocation);


        insertTransaction(inventoryTransaction);


        int rowsUpdated = updateBatchAfterAdjustment(batch.getBatchId(), quantityAfter, newStatus, operation.getClientCreatedAt());


        if (rowsUpdated != 1)
        {
            throw new IllegalStateException("BATCH_UPDATE_FAILED");
        }


        insertQueueItem(queueItem);
    }

    static void validateAdjustmentEnvelope(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity inventoryTransaction,
            SyncQueueEntity queueItem)
    {
        if (operation == null)
        {
            throw new IllegalArgumentException("Operation is required.");
        }


        if (operation.getOperationType() !=
                InventoryOperationType.ADJUSTMENT)
        {
            throw new IllegalArgumentException("Operation must be ADJUSTMENT.");
        }


        if (operation.getStatus() !=
                InventoryOperationStatus.LOCAL_PENDING)
        {
            throw new IllegalArgumentException("New adjustment must be LOCAL_PENDING.");
        }


        if (operation.getRequestedQuantity() <= 0)
        {
            throw new IllegalArgumentException("Adjustment quantity must be greater than zero.");
        }


        if (!hasText(operation.getReason()))
        {
            throw new IllegalArgumentException("ADJUSTMENT_REASON_REQUIRED");
        }


        if (hasText(operation.getRelatedOperationId()))
        {
            throw new IllegalArgumentException("Adjustment cannot reference a related operation.");
        }


        if (hasText(operation.getOverrideReason()))
        {
            throw new IllegalArgumentException("Adjustment cannot contain a FEFO override reason.");
        }


        if (allocation == null)
        {
            throw new IllegalArgumentException("Adjustment allocation is required.");
        }


        if (!operation.getOperationId().equals(allocation.getOperationId()))
        {
            throw new IllegalArgumentException("Allocation operation ID mismatch.");
        }


        if (allocation.getBatchId() == null || allocation.getBatchId().trim().isEmpty())
        {
            throw new IllegalArgumentException("Adjustment batch ID is required.");
        }


        if (allocation.getAllocationOrder() != 0)
        {
            throw new IllegalArgumentException("Adjustment allocation order must be zero.");
        }


        if (allocation.getQuantity() <= 0)
        {
            throw new IllegalArgumentException("Adjustment allocation quantity must be positive.");
        }


        if (allocation.getQuantity() != operation.getRequestedQuantity())
        {
            throw new IllegalArgumentException("Adjustment allocation must equal requested quantity.");
        }


        validateAdjustmentTransaction(operation, allocation, inventoryTransaction);


        if (queueItem == null || !operation.getOperationId().equals(queueItem.getOperationId()))
        {
            throw new IllegalArgumentException("Sync queue operation ID is invalid.");
        }
    }

    static void validateAdjustmentTransaction(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity inventoryTransaction)
    {
        if (inventoryTransaction == null)
        {
            throw new IllegalArgumentException("Provisional adjustment transaction is required.");
        }


        if (!operation.getOperationId().equals(inventoryTransaction.getOperationId()))
        {
            throw new IllegalArgumentException("Transaction operation ID mismatch.");
        }


        if (!operation.getMedicineId().equals(inventoryTransaction.getMedicineId()))
        {
            throw new IllegalArgumentException("Transaction medicine ID mismatch.");
        }


        if (!allocation.getBatchId().equals(inventoryTransaction.getBatchId()))
        {
            throw new IllegalArgumentException("Transaction batch ID mismatch.");
        }


        if (!"ADJUSTMENT".equals(inventoryTransaction.getTransactionType()))
        {
            throw new IllegalArgumentException("Transaction type must be ADJUSTMENT.");
        }


        int quantityChange = inventoryTransaction.getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalArgumentException("Adjustment transaction quantity cannot be zero.");
        }


        long absoluteQuantity = Math.abs((long) quantityChange);

        if (absoluteQuantity != allocation.getQuantity())
        {
            throw new IllegalArgumentException("Adjustment transaction quantity does not match the allocation.");
        }


        if (!inventoryTransaction.isProvisional())
        {
            throw new IllegalArgumentException("Local adjustment transaction must be provisional.");
        }


        if (!operation.getPerformedBy().equals(inventoryTransaction.getPerformedBy()))
        {
            throw new IllegalArgumentException("Transaction user mismatch.");
        }


        if (!hasText(inventoryTransaction.getReason()))
        {
            throw new IllegalArgumentException("ADJUSTMENT_REASON_REQUIRED");
        }


        if (!operation.getReason().trim().equals(inventoryTransaction.getReason().trim()))
        {
            throw new IllegalArgumentException("Adjustment transaction reason mismatch.");
        }
    }

    static BatchStatus deriveAdjustmentBatchStatus(BatchEntity batch, int quantityAfter)
    {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);


        LocalDate expiry = Instant.ofEpochMilli(batch.getExpiryDate()).atZone(BUSINESS_ZONE).toLocalDate();


        if (expiry.isBefore(today))
        {
            return BatchStatus.EXPIRED;
        }


        if (quantityAfter == 0)
        {
            return BatchStatus.DEPLETED;
        }

        return BatchStatus.ACTIVE;
    }


    /**
     * Records a compensating correction only when its referenced original operation is confirmed
     * and affected the selected batch. The inverse movement is written with the correction and sync
     * row in the same Room transaction, preserving the original history instead of rewriting it.
     *
     * @param operation pending correction envelope with the related operation ID
     * @param allocation single batch allocation being corrected
     * @param transaction provisional compensating movement
     * @param queueItem retry metadata for trusted processing
     */
    @Transaction

    default void commitCorrection(InventoryOperationEntity operation, OperationAllocationEntity allocation,
                                  InventoryTransactionEntity inventoryTransaction, SyncQueueEntity queueItem)
    {
        validateCorrectionEnvelope(operation, allocation, inventoryTransaction, queueItem);

        String relatedOperationId = operation.getRelatedOperationId().trim();


        InventoryOperationEntity originalOperation = getCorrectionOriginalOperation(relatedOperationId);


        if (originalOperation == null)
        {
            throw new IllegalStateException("ORIGINAL_OPERATION_NOT_FOUND");
        }


        if (originalOperation.getStatus() != InventoryOperationStatus.CONFIRMED)
        {
            throw new IllegalStateException("ORIGINAL_OPERATION_NOT_CONFIRMED");
        }


        if (!operation.getMedicineId().equals(originalOperation.getMedicineId()))
        {
            throw new IllegalStateException("ORIGINAL_OPERATION_MEDICINE_MISMATCH");
        }


        if (operation.getOperationId().equals(relatedOperationId))
        {
            throw new IllegalArgumentException("Correction cannot reference itself.");
        }


        int originalTransactionCount = countConfirmedOriginalTransactionsForBatch(relatedOperationId, allocation.getBatchId());


        if (originalTransactionCount <= 0)
        {
            throw new IllegalStateException("ORIGINAL_OPERATION_BATCH_MISMATCH");
        }


        BatchEntity batch = getAdjustmentBatch(allocation.getBatchId());


        if (batch == null)
        {
            throw new IllegalStateException("INVALID_BATCH");
        }


        if (!operation.getMedicineId().equals(batch.getMedicineId()))
        {
            throw new IllegalStateException("INVALID_BATCH");
        }


        int signedQuantity = inventoryTransaction.getQuantityChange();


        long quantityAfterLong = (long) batch.getQuantityAvailable() + signedQuantity;


        if (quantityAfterLong < 0)
        {
            throw new IllegalStateException("NEGATIVE_STOCK");
        }


        if (quantityAfterLong > Integer.MAX_VALUE)
        {
            throw new IllegalStateException("INVALID_QUANTITY");
        }


        int quantityAfter = (int) quantityAfterLong;


        BatchStatus newStatus = deriveAdjustmentBatchStatus(batch, quantityAfter);



        insertOperation(operation);


        insertAllocation(allocation);


        insertTransaction(inventoryTransaction);


        int rowsUpdated = updateBatchAfterAdjustment(batch.getBatchId(), quantityAfter,
                newStatus, operation.getClientCreatedAt());


        if (rowsUpdated != 1)
        {
            throw new IllegalStateException("BATCH_UPDATE_FAILED");
        }



        insertQueueItem(queueItem);
    }

    static void validateCorrectionEnvelope(InventoryOperationEntity operation, OperationAllocationEntity allocation,
                                           InventoryTransactionEntity inventoryTransaction, SyncQueueEntity queueItem)
    {
        if (operation == null)
        {
            throw new IllegalArgumentException("Operation is required.");
        }


        if (operation.getOperationType() != InventoryOperationType.CORRECTION)
        {
            throw new IllegalArgumentException("Operation must be CORRECTION.");
        }


        if (operation.getStatus() != InventoryOperationStatus.LOCAL_PENDING)
        {
            throw new IllegalArgumentException("New correction must be LOCAL_PENDING.");
        }


        if (operation.getRequestedQuantity() <= 0)
        {
            throw new IllegalArgumentException("Correction quantity must be greater than zero.");
        }


        if (!hasText(operation.getReason()))
        {
            throw new IllegalArgumentException("CORRECTION_REASON_REQUIRED");
        }


        if (!hasText(operation.getRelatedOperationId()))
        {
            throw new IllegalArgumentException("CORRECTION_RELATED_OPERATION_REQUIRED");
        }


        if (hasText(operation.getOverrideReason()))
        {
            throw new IllegalArgumentException("Correction cannot contain a FEFO override reason.");
        }


        if (allocation == null)
        {
            throw new IllegalArgumentException("Correction allocation is required.");
        }


        if (!operation.getOperationId().equals(allocation.getOperationId()))
        {
            throw new IllegalArgumentException("Allocation operation ID mismatch.");
        }


        if (allocation.getBatchId() == null || allocation.getBatchId().trim().isEmpty())
        {
            throw new IllegalArgumentException("Correction batch ID is required.");
        }


        if (allocation.getAllocationOrder() != 0)
        {
            throw new IllegalArgumentException("Correction allocation order must be zero.");
        }


        if (allocation.getQuantity() <= 0)
        {
            throw new IllegalArgumentException("Correction allocation quantity must be positive.");
        }


        if (allocation.getQuantity() != operation.getRequestedQuantity())
        {
            throw new IllegalArgumentException("Correction allocation must equal requested quantity.");
        }


        validateCorrectionTransaction(operation, allocation, inventoryTransaction);

        if (queueItem == null || !operation.getOperationId().equals(queueItem.getOperationId()))
        {
            throw new IllegalArgumentException("Sync queue operation ID is invalid.");
        }
    }

    static void validateCorrectionTransaction(InventoryOperationEntity operation, OperationAllocationEntity allocation,
            InventoryTransactionEntity inventoryTransaction)
    {
        if (inventoryTransaction == null)
        {
            throw new IllegalArgumentException("Provisional correction transaction is required.");
        }


        if (!operation.getOperationId().equals(inventoryTransaction.getOperationId()))
        {
            throw new IllegalArgumentException("Transaction operation ID mismatch.");
        }


        if (!operation.getMedicineId().equals(inventoryTransaction.getMedicineId()))
        {
            throw new IllegalArgumentException("Transaction medicine ID mismatch.");
        }


        if (!allocation.getBatchId().equals(inventoryTransaction.getBatchId()))
        {
            throw new IllegalArgumentException("Transaction batch ID mismatch.");
        }


        if (!"CORRECTION".equals(inventoryTransaction.getTransactionType()))
        {
            throw new IllegalArgumentException("Transaction type must be CORRECTION.");
        }


        int quantityChange = inventoryTransaction.getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalArgumentException("Correction transaction quantity cannot be zero.");
        }


        long absoluteQuantity = Math.abs((long) quantityChange);


        if (absoluteQuantity != allocation.getQuantity())
        {
            throw new IllegalArgumentException("Correction transaction quantity does not match the allocation.");
        }


        if (!inventoryTransaction.isProvisional())
        {
            throw new IllegalArgumentException("Local correction transaction must be provisional.");
        }


        if (!operation.getPerformedBy().equals(inventoryTransaction.getPerformedBy()))
        {
            throw new IllegalArgumentException("Transaction user mismatch.");
        }


        if (!hasText(inventoryTransaction.getReason()))
        {
            throw new IllegalArgumentException("CORRECTION_REASON_REQUIRED");
        }


        if (!operation.getReason().trim().equals(inventoryTransaction.getReason().trim()))
        {
            throw new IllegalArgumentException("Correction transaction reason mismatch.");
        }
    }


    static void validateIssueEnvelope(InventoryOperationEntity operation, List<OperationAllocationEntity> allocations,
            List<InventoryTransactionEntity> transactions, SyncQueueEntity queueItem, boolean fefoOverrideAuthorized)
    {
        if (operation == null)
        {
            throw new IllegalArgumentException("Operation is required.");
        }


        if (operation.getOperationType() != InventoryOperationType.ISSUE)
        {
            throw new IllegalArgumentException("Operation must be ISSUE.");
        }


        if (operation.getStatus() != InventoryOperationStatus.LOCAL_PENDING)
        {
            throw new IllegalArgumentException("New issue must be LOCAL_PENDING.");
        }


        if (operation.getRequestedQuantity() <= 0)
        {
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        }

        if (operation.getOverrideReason() != null && operation.getOverrideReason().trim().isEmpty())
        {
            throw new IllegalArgumentException("FEFO_OVERRIDE_REASON_REQUIRED");
        }


        if (hasText(operation.getOverrideReason()) && !fefoOverrideAuthorized)
        {
            throw new SecurityException("FEFO_OVERRIDE_NOT_ALLOWED");
        }


        if (allocations == null || allocations.isEmpty())
        {
            throw new IllegalArgumentException("Issue allocations are required.");
        }


        if (transactions == null || transactions.size() != allocations.size())
        {
            throw new IllegalArgumentException("Every allocation requires one provisional transaction.");
        }


        if (queueItem == null || !operation.getOperationId().equals(queueItem.getOperationId()))
        {
            throw new IllegalArgumentException("Sync queue operation ID is invalid.");
        }


        Set<String> batchIds = new HashSet<>();


        int allocationTotal = 0;


        for (int index = 0; index < allocations.size(); index++)
        {
            OperationAllocationEntity allocation = allocations.get(index);


            if (allocation == null)
            {
                throw new IllegalArgumentException("Issue allocation is invalid.");
            }


            if (!operation.getOperationId().equals(allocation.getOperationId()))
            {
                throw new IllegalArgumentException("Allocation operation ID mismatch.");
            }


            if (allocation.getBatchId() == null || allocation.getBatchId().trim().isEmpty())
            {
                throw new IllegalArgumentException("Allocation batch ID is required.");
            }


            if (allocation.getQuantity() <= 0)
            {
                throw new IllegalArgumentException("Allocation quantity must be greater than zero.");
            }


            if (allocation.getAllocationOrder() !=
                    index)
            {
                throw new IllegalArgumentException("Allocation order is invalid.");
            }


            if (!batchIds.add(allocation.getBatchId()))
            {
                throw new IllegalArgumentException("A batch cannot appear more than once.");
            }


            allocationTotal += allocation.getQuantity();


            InventoryTransactionEntity inventoryTransaction = transactions.get(index);


            validateIssueTransaction(operation, allocation, inventoryTransaction);
        }


        if (allocationTotal != operation.getRequestedQuantity())
        {
            throw new IllegalArgumentException("Allocation total must equal requested quantity.");
        }
    }


    static void validateIssueTransaction(InventoryOperationEntity operation, OperationAllocationEntity allocation, InventoryTransactionEntity inventoryTransaction)
    {
        if (inventoryTransaction == null)
        {
            throw new IllegalArgumentException("Provisional issue transaction is required.");
        }


        if (!operation.getOperationId().equals(inventoryTransaction.getOperationId()))
        {
            throw new IllegalArgumentException("Transaction operation ID mismatch.");
        }


        if (!operation.getMedicineId().equals(inventoryTransaction.getMedicineId()))
        {
            throw new IllegalArgumentException("Transaction medicine ID mismatch.");
        }


        if (!allocation.getBatchId().equals(inventoryTransaction.getBatchId()))
        {
            throw new IllegalArgumentException("Transaction batch ID mismatch.");
        }


        if (!"ISSUE".equals(
                inventoryTransaction.getTransactionType()
        ))
        {
            throw new IllegalArgumentException("Transaction type must be ISSUE.");
        }


        if (inventoryTransaction.getQuantityChange() != -allocation.getQuantity())
        {
            throw new IllegalArgumentException("Issue transaction quantity must be negative.");
        }


        if (!inventoryTransaction.isProvisional())
        {
            throw new IllegalArgumentException("Local issue transaction must be provisional.");
        }


        if (!operation.getPerformedBy().equals(inventoryTransaction.getPerformedBy()))
        {
            throw new IllegalArgumentException("Transaction user mismatch.");
        }
    }


    static List<ExpectedAllocation> calculateExpectedFefo(
            List<BatchEntity> eligible,
            int requestedQuantity)
    {
        List<ExpectedAllocation> expected = new ArrayList<>();

        int remaining = requestedQuantity;

        for (BatchEntity batch : eligible)
        {
            if (remaining <= 0)
            {
                break;
            }


            int quantity = Math.min(batch.getQuantityAvailable(), remaining);


            if (quantity <= 0)
            {
                continue;
            }


            expected.add(new ExpectedAllocation(batch, quantity));


            remaining -= quantity;
        }


        if (remaining > 0)
        {
            throw new IllegalStateException("INSUFFICIENT_STOCK");
        }

        return expected;
    }


    static boolean matchesCurrentFefo(List<OperationAllocationEntity> actual, List<ExpectedAllocation> expected)
    {
        if (actual.size() != expected.size())
        {
            return false;
        }


        for (int index = 0; index < expected.size(); index++)
        {
            OperationAllocationEntity actualAllocation = actual.get(index);

            ExpectedAllocation expectedAllocation = expected.get(index);


            if (!actualAllocation.getBatchId().equals(expectedAllocation.batch.getBatchId()))
            {
                return false;
            }

            if (actualAllocation.getQuantity() != expectedAllocation.quantity)
            {
                return false;
            }
        }

        return true;
    }


    static void validateOverrideAllocations(List<OperationAllocationEntity> allocations, Map<String, BatchEntity> eligibleById)
    {
        for (OperationAllocationEntity allocation : allocations)
        {
            BatchEntity batch = eligibleById.get(allocation.getBatchId());


            if (batch == null)
            {
                throw new IllegalStateException("INVALID_BATCH");
            }


            if (allocation.getQuantity() <= 0)
            {
                throw new IllegalStateException("INVALID_QUANTITY");
            }


            if (allocation.getQuantity() > batch.getQuantityAvailable())
            {
                throw new IllegalStateException("INSUFFICIENT_STOCK");
            }
        }
    }


    static boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }


    class ExpectedAllocation
    {
        final BatchEntity batch;

        final int quantity;

        ExpectedAllocation(BatchEntity batch, int quantity)
        {
            this.batch = batch;

            this.quantity = quantity;
        }
    }
}