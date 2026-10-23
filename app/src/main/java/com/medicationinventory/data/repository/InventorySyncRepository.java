package com.medicationinventory.data.repository;

import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.TransactionDao;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;

import com.medicationinventory.data.remote.firestore.FirestoreInventoryOperationDataSource;

import com.medicationinventory.model.dto.InventoryOperationRemoteResult;
import com.medicationinventory.model.dto.InventoryOperationUploadDto;

import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Builds uploads from Room operations, allocations, batches, and provisional transactions, then submits them through the Firestore operation source. Queue ownership is checked against the current Firebase UID before worker processing; server outcomes update local operation and retry state, while terminal outcomes are handed to reconciliation.
 */
public class InventorySyncRepository
{
    private final InventoryOperationDao
            inventoryOperationDao;

    private final AllocationDao
            allocationDao;

    private final BatchDao
            batchDao;

    private final TransactionDao
            transactionDao;

    private final SyncQueueDao
            syncQueueDao;


    private final FirestoreInventoryOperationDataSource
            firestoreInventoryOperationDataSource;


    private final AuthenticationRepository
            authenticationRepository;


    private final AppExecutors
            appExecutors;


    public InventorySyncRepository(
            InventoryOperationDao inventoryOperationDao,
            AllocationDao allocationDao,
            BatchDao batchDao,
            TransactionDao transactionDao,
            SyncQueueDao syncQueueDao,
            FirestoreInventoryOperationDataSource
                    firestoreInventoryOperationDataSource,
            AuthenticationRepository authenticationRepository,
            AppExecutors appExecutors)
    {
        this.inventoryOperationDao =
                inventoryOperationDao;

        this.allocationDao =
                allocationDao;

        this.batchDao =
                batchDao;

        this.transactionDao =
                transactionDao;

        this.syncQueueDao =
                syncQueueDao;

        this.firestoreInventoryOperationDataSource =
                firestoreInventoryOperationDataSource;

        this.authenticationRepository =
                authenticationRepository;

        this.appExecutors =
                appExecutors;
    }



    /**
     * Prepares and submits one queued operation on the database executor after validating its local envelope.
     * Completion is delivered through the callback; authoritative stock changes only after trusted processing and reconciliation.
     * @param operationId stable local operation identifier
     * @param callback receives upload acceptance, terminal result, or retryable/permanent failure
     */
    public void uploadPendingOperation(
            String operationId,
            UploadCallback callback)
    {
        if (operationId == null
                || operationId.trim().isEmpty())
        {
            callback.onError(
                    new IllegalArgumentException(
                            "Operation ID is required."
                    )
            );

            return;
        }


        appExecutors
                .databaseIO()
                .execute(() ->
                        prepareUpload(
                                operationId,
                                callback
                        )
                );
    }


    public List<String>
    getDueLocalPendingOperationIdsForWorker(
            long now)
    {
        return restrictDueOperationsToCurrentUser(
                syncQueueDao.getDueLocalPendingOperationIds(now)
        );
    }


    public List<String>
    getDueSubmittedOperationIdsForWorker(
            long now)
    {
        return restrictDueOperationsToCurrentUser(
                syncQueueDao.getDueSubmittedOperationIds(now)
        );
    }


    private List<String> restrictDueOperationsToCurrentUser(
            List<String> candidateIds)
    {
        String uid = authenticationRepository.getCurrentUserId();
        if (uid == null || uid.trim().isEmpty()
                || candidateIds == null || candidateIds.isEmpty())
        {
            return Collections.emptyList();
        }

        List<String> ownedIds = new ArrayList<>();
        for (String operationId : candidateIds)
        {
            if (!uid.equals(authenticationRepository.getCurrentUserId()))
            {
                return Collections.emptyList();
            }
            if (operationId == null || operationId.trim().isEmpty())
            {
                continue;
            }
            InventoryOperationEntity operation =
                    inventoryOperationDao.getById(operationId);
            if (operation != null && uid.equals(operation.getPerformedBy()))
            {
                ownedIds.add(operationId);
            }
        }
        return uid.equals(authenticationRepository.getCurrentUserId())
                ? ownedIds : Collections.emptyList();
    }

    public String getCurrentUserIdForWorker()
    {
        return authenticationRepository.getCurrentUserId();
    }

    public boolean isOwnedBySessionForWorker(String operationId, String uid)
    {
        if (uid == null || uid.trim().isEmpty()
                || operationId == null || operationId.trim().isEmpty()
                || !uid.equals(authenticationRepository.getCurrentUserId()))
        {
            return false;
        }
        InventoryOperationEntity operation =
                inventoryOperationDao.getById(operationId);
        return operation != null
                && uid.equals(operation.getPerformedBy())
                && uid.equals(authenticationRepository.getCurrentUserId());
    }

    public void recordTemporaryFailureForWorker(
            String operationId,
            long lastAttemptAt,
            long nextAttemptAt,
            String errorCode)
    {
        syncQueueDao.recordAttempt(
                operationId,
                lastAttemptAt,
                nextAttemptAt,
                errorCode
        );
    }


    /**
     * Requeues a permission-denied operation only for the same active owner after the conditional update succeeds.
     * @param operationId held operation to retry
     * @param expectedUserId account that initiated the request
     * @param callback reports whether the queue state was actually changed
     */
    public void retryHeldPermissionDeniedUpload(
            String operationId,
            String expectedUserId,
            ManualRetryCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException("Retry callback is required.");
        }

        if (!hasText(operationId) || !hasText(expectedUserId))
        {
            appExecutors.mainThread().execute(() ->
                    callback.onError("An operation and signed-in user are required."));
            return;
        }

        appExecutors.databaseIO().execute(() ->
        {
            try
            {
                String activeUserId = authenticationRepository.getCurrentUserId();
                if (!expectedUserId.equals(activeUserId))
                {
                    postManualRetryError(callback,
                            "Your account changed. Sign in again to retry your operation.");
                    return;
                }

                int changed = syncQueueDao.retryHeldPermissionDeniedUpload(
                        operationId, expectedUserId);

                if (!expectedUserId.equals(authenticationRepository.getCurrentUserId()))
                {
                    postManualRetryError(callback,
                            "Your account changed. Sign in again to continue " +
                                    "synchronisation.");
                    return;
                }

                if (changed != 1)
                {
                    postManualRetryError(callback,
                            "This operation is not an eligible held upload, " +
                                    "or it was already retried.");
                    return;
                }

                appExecutors.mainThread().execute(callback::onReady);
            }
            catch (Exception exception)
            {
                postManualRetryError(callback,
                        "The local retry could not be enabled. Please try again.");
            }
        });
    }

    private void postManualRetryError(ManualRetryCallback callback, String message)
    {
        appExecutors.mainThread().execute(() -> callback.onError(message));
    }

    public interface ManualRetryCallback
    {
        void onReady();
        void onError(String message);
    }

    /**
     * Loads and validates an operation's Room envelope before creating its trusted-backend DTO.
     * The current Firebase UID must still match the operation owner, and every allocation must
     * resolve to a batch belonging to the same medicine. Failures are returned through the
     * callback; only a valid envelope is submitted, where the server makes the authoritative stock
     * decision.
     */
    private void prepareUpload(
            String operationId,
            UploadCallback callback)
    {
        try
        {

            InventoryOperationEntity operation =
                    inventoryOperationDao
                            .getById(
                                    operationId
                            );


            if (operation == null)
            {
                postError(
                        callback,
                        new IllegalStateException(
                                "Pending operation could not be found."
                        )
                );

                return;
            }

            if (operation.getStatus()
                    != InventoryOperationStatus.LOCAL_PENDING)
            {
                postError(
                        callback,
                        new IllegalStateException(
                                "Only LOCAL_PENDING operations can be uploaded by this method."
                        )
                );

                return;
            }


            String currentUid =
                    authenticationRepository
                            .getCurrentUserId();


            if (currentUid == null
                    || currentUid.trim().isEmpty())
            {
                postError(
                        callback,
                        new IllegalStateException(
                                "No authenticated session is available."
                        )
                );

                return;
            }


            if (!currentUid.equals(
                    operation.getPerformedBy()
            ))
            {
                postError(
                        callback,
                        new SecurityException(
                                "The pending operation belongs to a different user."
                        )
                );

                return;
            }


            List<OperationAllocationEntity> allocations =
                    allocationDao
                            .getForOperation(
                                    operationId
                            );


            if (allocations == null
                    || allocations.isEmpty())
            {
                postError(
                        callback,
                        new IllegalStateException(
                                "The operation has no batch allocation."
                        )
                );

                return;
            }


            List<InventoryOperationUploadDto.AllocationDto>
                    uploadAllocations =
                    buildAllocationDtos(
                            operation,
                            allocations
                    );


            String adjustmentDirection =
                    resolveAdjustmentDirection(
                            operation,
                            allocations
                    );

            String correctionDirection =
                    resolveCorrectionDirection(
                            operation,
                            allocations
                    );


            InventoryOperationUploadDto upload =
                    new InventoryOperationUploadDto(
                            operation.getOperationId(),
                            operation.getOperationType()
                                    .name(),
                            operation.getMedicineId(),
                            operation.getRequestedQuantity(),
                            uploadAllocations,
                            operation.getReason(),
                            operation.getPerformedBy(),
                            operation.getDeviceId(),
                            operation.getClientCreatedAt(),
                            operation.getRelatedOperationId(),
                            operation.getOverrideReason(),
                            adjustmentDirection,
                            correctionDirection,
                            operation.getReceiptMode()
                    );


            submitToFirestore(
                    operation,
                    upload,
                    callback
            );
        }
        catch (Exception exception)
        {
            postError(
                    callback,
                    exception
            );
        }
    }




    /**
     * Orders local allocations by their persisted order, resolves batch metadata needed by receipt
     * uploads, and checks the resulting shape before serialization. Uploading receipt batch dates
     * from the selected local batch keeps an existing-batch top-up tied to that batch's identity.
     *
     * @param operation operation whose allocations are being serialized
     * @param allocations stored allocation rows
     * @return validated allocation payload in operation order
     */
    private List<InventoryOperationUploadDto.AllocationDto>
    buildAllocationDtos(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations)
    {
        List<OperationAllocationEntity> orderedAllocations =
                new ArrayList<>(
                        allocations
                );


        orderedAllocations.sort(
                Comparator.comparingInt(
                        OperationAllocationEntity::getAllocationOrder
                )
        );


        List<InventoryOperationUploadDto.AllocationDto> result =
                new ArrayList<>();


        for (OperationAllocationEntity allocation :
                orderedAllocations)
        {
            if (allocation.getQuantity() <= 0)
            {
                throw new IllegalStateException(
                        "Operation allocation quantity must be positive."
                );
            }


            BatchEntity batch =
                    batchDao.getById(
                            allocation.getBatchId()
                    );


            if (batch == null)
            {
                throw new IllegalStateException(
                        "A batch referenced by the operation could not be found."
                );
            }


            if (!operation
                    .getMedicineId()
                    .equals(
                            batch.getMedicineId()
                    ))
            {
                throw new IllegalStateException(
                        "Operation batch does not belong to the selected medicine."
                );
            }


            boolean receipt =
                    operation.getOperationType()
                            == InventoryOperationType.RECEIPT;


            InventoryOperationUploadDto.AllocationDto
                    uploadAllocation =
                    new InventoryOperationUploadDto.AllocationDto(
                            allocation.getBatchId(),
                            allocation.getQuantity(),
                            allocation.getAllocationOrder(),

                            receipt
                                    ? batch.getBatchNumber()
                                    : null,

                            receipt
                                    ? ("EXISTING_BATCH".equals(operation.getReceiptMode())
                                    && operation.getReceiptReceivedDate() != null
                                       ? operation.getReceiptReceivedDate()
                                       : batch.getReceivedDate())
                                    : null,

                            receipt
                                    ? batch.getExpiryDate()
                                    : null
                    );


            result.add(
                    uploadAllocation
            );
        }


        validateAllocationShape(
                operation,
                result
        );


        return result;
    }


    private void validateAllocationShape(
            InventoryOperationEntity operation,
            List<InventoryOperationUploadDto.AllocationDto> allocations)
    {
        if (operation.getRequestedQuantity() <= 0)
        {
            throw new IllegalStateException(
                    "Requested quantity must be positive."
            );
        }


        if (allocations == null
                || allocations.isEmpty())
        {
            throw new IllegalStateException(
                    "The operation requires at least one allocation."
            );
        }


        int allocationTotal =
                0;


        Set<String> batchIds =
                new HashSet<>();


        for (int index = 0;
             index < allocations.size();
             index++)
        {
            InventoryOperationUploadDto.AllocationDto allocation =
                    allocations.get(
                            index
                    );


            if (allocation.getBatchId() == null
                    || allocation
                    .getBatchId()
                    .trim()
                    .isEmpty())
            {
                throw new IllegalStateException(
                        "Allocation batch ID is missing."
                );
            }


            if (allocation.getQuantity() <= 0)
            {
                throw new IllegalStateException(
                        "Allocation quantity must be positive."
                );
            }


            if (allocation.getAllocationOrder()
                    != index)
            {
                throw new IllegalStateException(
                        "Allocation order is invalid."
                );
            }


            if (!batchIds.add(
                    allocation.getBatchId()
            ))
            {
                throw new IllegalStateException(
                        "The same batch cannot appear more than once."
                );
            }


            allocationTotal +=
                    allocation.getQuantity();
        }


        if (operation.getOperationType()
                == InventoryOperationType.RECEIPT
                ||
                operation.getOperationType()
                        == InventoryOperationType.ISSUE
                ||
                operation.getOperationType()
                        == InventoryOperationType.ADJUSTMENT
                ||
                operation.getOperationType()
                        == InventoryOperationType.CORRECTION)
        {
            if (allocationTotal
                    != operation.getRequestedQuantity())
            {
                throw new IllegalStateException(
                        "Allocation total does not match the requested quantity."
                );
            }
        }


        validateReceiptAllocationShape(
                operation,
                allocations
        );


        validateAdjustmentAllocationShape(
                operation,
                allocations
        );

        validateCorrectionAllocationShape(
                operation,
                allocations
        );
    }

    private void validateCorrectionAllocationShape(
            InventoryOperationEntity operation,
            List<InventoryOperationUploadDto.AllocationDto> allocations)
    {
        if (operation.getOperationType()
                != InventoryOperationType.CORRECTION)
        {
            return;
        }


        if (allocations == null
                || allocations.size() != 1)
        {
            throw new IllegalStateException(
                    "Correction must affect exactly one batch."
            );
        }


        if (!hasText(
                operation.getReason()
        ))
        {
            throw new IllegalStateException(
                    "Correction reason is required."
            );
        }


        if (!hasText(
                operation.getRelatedOperationId()
        ))
        {
            throw new IllegalStateException(
                    "Correction must reference the original operation."
            );
        }


        String relatedOperationId =
                operation
                        .getRelatedOperationId()
                        .trim();


        if (operation
                .getOperationId()
                .equals(
                        relatedOperationId
                ))
        {
            throw new IllegalStateException(
                    "Correction cannot reference itself."
            );
        }

        if (hasText(
                operation.getOverrideReason()
        ))
        {
            throw new IllegalStateException(
                    "Correction cannot contain a FEFO override reason."
            );
        }


        InventoryOperationUploadDto.AllocationDto allocation =
                allocations.get(
                        0
                );


        if (allocation.getQuantity()
                != operation.getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Correction allocation does not match requested quantity."
            );
        }


        InventoryOperationEntity originalOperation =
                inventoryOperationDao
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
                    "Only a confirmed operation can be corrected."
            );
        }


        if (!operation
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

        List<InventoryTransactionEntity> originalTransactions =
                transactionDao
                        .getForOperation(
                                relatedOperationId
                        );


        boolean matchingConfirmedBatch =
                false;


        if (originalTransactions != null)
        {
            for (InventoryTransactionEntity transaction :
                    originalTransactions)
            {
                if (transaction == null)
                {
                    continue;
                }


                if (transaction.isProvisional())
                {
                    continue;
                }


                if (allocation
                        .getBatchId()
                        .equals(
                                transaction.getBatchId()
                        ))
                {
                    matchingConfirmedBatch =
                            true;

                    break;
                }
            }
        }


        if (!matchingConfirmedBatch)
        {
            throw new IllegalStateException(
                    "Correction batch was not part of the original confirmed operation."
            );
        }
    }


    private void validateReceiptAllocationShape(
            InventoryOperationEntity operation,
            List<InventoryOperationUploadDto.AllocationDto> allocations)
    {
        if (operation.getOperationType()
                != InventoryOperationType.RECEIPT)
        {
            return;
        }


        if (allocations.size() != 1)
        {
            throw new IllegalStateException(
                    "A new-batch receipt must have exactly one allocation."
            );
        }


        InventoryOperationUploadDto.AllocationDto
                receiptAllocation =
                allocations.get(
                        0
                );


        if (receiptAllocation.getBatchNumber() == null
                || receiptAllocation
                .getBatchNumber()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Receipt batch number is missing."
            );
        }


        if (receiptAllocation.getReceivedDate() == null
                || receiptAllocation.getExpiryDate() == null)
        {
            throw new IllegalStateException(
                    "Receipt batch dates are missing."
            );
        }
    }


    private void validateAdjustmentAllocationShape(
            InventoryOperationEntity operation,
            List<InventoryOperationUploadDto.AllocationDto> allocations)
    {
        if (operation.getOperationType()
                != InventoryOperationType.ADJUSTMENT)
        {
            return;
        }


        if (allocations.size() != 1)
        {
            throw new IllegalStateException(
                    "Adjustment must affect exactly one batch."
            );
        }


        if (operation.getReason() == null
                || operation
                .getReason()
                .trim()
                .isEmpty())
        {
            throw new IllegalStateException(
                    "Adjustment reason is required."
            );
        }


        if (hasText(
                operation.getRelatedOperationId()
        ))
        {
            throw new IllegalStateException(
                    "Adjustment cannot reference a related operation."
            );
        }


        if (hasText(
                operation.getOverrideReason()
        ))
        {
            throw new IllegalStateException(
                    "Adjustment cannot contain a FEFO override reason."
            );
        }
    }


    private String resolveAdjustmentDirection(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations)
    {
        if (operation.getOperationType()
                != InventoryOperationType.ADJUSTMENT)
        {
            return null;
        }


        if (!hasText(
                operation.getReason()
        ))
        {
            throw new IllegalStateException(
                    "Adjustment reason is required."
            );
        }


        if (allocations == null
                || allocations.size() != 1)
        {
            throw new IllegalStateException(
                    "Adjustment must have exactly one batch allocation."
            );
        }


        OperationAllocationEntity allocation =
                allocations.get(
                        0
                );


        List<InventoryTransactionEntity> transactions =
                transactionDao
                        .getForOperation(
                                operation.getOperationId()
                        );


        if (transactions == null
                || transactions.size() != 1)
        {
            throw new IllegalStateException(
                    "Adjustment must have exactly one local transaction."
            );
        }


        InventoryTransactionEntity transaction =
                transactions.get(
                        0
                );

        if (!transaction.isProvisional())
        {
            throw new IllegalStateException(
                    "Pending adjustment transaction must be provisional."
            );
        }


        if (!"ADJUSTMENT".equals(
                transaction.getTransactionType()
        ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction type is invalid."
            );
        }


        if (!operation
                .getOperationId()
                .equals(
                        transaction.getOperationId()
                ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction operation mismatch."
            );
        }


        if (!operation
                .getMedicineId()
                .equals(
                        transaction.getMedicineId()
                ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction medicine mismatch."
            );
        }


        if (!allocation
                .getBatchId()
                .equals(
                        transaction.getBatchId()
                ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction batch mismatch."
            );
        }


        if (allocation.getQuantity()
                != operation.getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Adjustment allocation does not match requested quantity."
            );
        }


        int quantityChange =
                transaction.getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalStateException(
                    "Adjustment quantity cannot be zero."
            );
        }


        if (Math.abs(
                (long) quantityChange
        )
                != operation.getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Adjustment transaction does not match requested quantity."
            );
        }


        if (!hasText(
                transaction.getReason()
        ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction reason is required."
            );
        }


        if (!operation
                .getReason()
                .trim()
                .equals(
                        transaction
                                .getReason()
                                .trim()
                ))
        {
            throw new IllegalStateException(
                    "Adjustment transaction reason mismatch."
            );
        }


        return quantityChange > 0
                ? "IN"
                : "OUT";
    }

    private String resolveCorrectionDirection(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations)
    {
        if (operation.getOperationType()
                != InventoryOperationType.CORRECTION)
        {
            return null;
        }


        if (!hasText(
                operation.getReason()
        ))
        {
            throw new IllegalStateException(
                    "Correction reason is required."
            );
        }


        if (!hasText(
                operation.getRelatedOperationId()
        ))
        {
            throw new IllegalStateException(
                    "Correction original operation reference is required."
            );
        }


        if (allocations == null
                || allocations.size() != 1)
        {
            throw new IllegalStateException(
                    "Correction must have exactly one batch allocation."
            );
        }


        OperationAllocationEntity allocation =
                allocations.get(
                        0
                );


        List<InventoryTransactionEntity> transactions =
                transactionDao
                        .getForOperation(
                                operation.getOperationId()
                        );


        if (transactions == null
                || transactions.size() != 1)
        {
            throw new IllegalStateException(
                    "Correction must have exactly one local transaction."
            );
        }


        InventoryTransactionEntity transaction =
                transactions.get(
                        0
                );


        if (!transaction.isProvisional())
        {
            throw new IllegalStateException(
                    "Pending correction transaction must be provisional."
            );
        }


        if (!"CORRECTION".equals(
                transaction.getTransactionType()
        ))
        {
            throw new IllegalStateException(
                    "Correction transaction type is invalid."
            );
        }


        if (!operation
                .getOperationId()
                .equals(
                        transaction.getOperationId()
                ))
        {
            throw new IllegalStateException(
                    "Correction transaction operation mismatch."
            );
        }


        if (!operation
                .getMedicineId()
                .equals(
                        transaction.getMedicineId()
                ))
        {
            throw new IllegalStateException(
                    "Correction transaction medicine mismatch."
            );
        }


        if (!allocation
                .getBatchId()
                .equals(
                        transaction.getBatchId()
                ))
        {
            throw new IllegalStateException(
                    "Correction transaction batch mismatch."
            );
        }


        if (allocation.getQuantity()
                != operation.getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Correction allocation does not match requested quantity."
            );
        }


        int quantityChange =
                transaction
                        .getQuantityChange();


        if (quantityChange == 0)
        {
            throw new IllegalStateException(
                    "Correction quantity cannot be zero."
            );
        }


        if (Math.abs(
                (long) quantityChange
        )
                != operation.getRequestedQuantity())
        {
            throw new IllegalStateException(
                    "Correction transaction does not match requested quantity."
            );
        }


        if (!hasText(
                transaction.getReason()
        ))
        {
            throw new IllegalStateException(
                    "Correction transaction reason is required."
            );
        }


        if (!operation
                .getReason()
                .trim()
                .equals(
                        transaction
                                .getReason()
                                .trim()
                ))
        {
            throw new IllegalStateException(
                    "Correction transaction reason mismatch."
            );
        }


        return quantityChange > 0
                ? "IN"
                : "OUT";
    }



    private void submitToFirestore(
            InventoryOperationEntity localOperation,
            InventoryOperationUploadDto upload,
            UploadCallback callback)
    {
        if (!localOperation.getPerformedBy().equals(
                authenticationRepository.getCurrentUserId()))
        {
            callback.onError(new SecurityException(
                    "The authenticated account changed before upload."));
            return;
        }
        firestoreInventoryOperationDataSource
                .submitOperation(
                        upload,
                        new FirestoreInventoryOperationDataSource
                                .UploadCallback()
                        {
                            @Override
                            public void onSuccess(
                                    InventoryOperationRemoteResult remoteResult)
                            {
                                handleRemoteResult(
                                        localOperation,
                                        remoteResult,
                                        callback
                                );
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {

                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }



    private void handleRemoteResult(
            InventoryOperationEntity localOperation,
            InventoryOperationRemoteResult remoteResult,
            UploadCallback callback)
    {
        if (remoteResult == null)
        {
            callback.onError(
                    new IllegalStateException(
                            "Firestore returned no operation result."
                    )
            );

            return;
        }


        if (!localOperation
                .getOperationId()
                .equals(
                        remoteResult.getOperationId()
                ))
        {
            callback.onError(
                    new IllegalStateException(
                            "Remote operation does not match the local operation."
                    )
            );

            return;
        }


        InventoryOperationStatus remoteStatus =
                remoteResult.getStatus();


        if (remoteStatus
                == InventoryOperationStatus.SUBMITTED)
        {
            markLocalSubmitted(
                    localOperation.getOperationId(),
                    remoteResult,
                    callback
            );

            return;
        }


        if (remoteStatus
                == InventoryOperationStatus.CONFIRMED
                ||
                remoteStatus
                        == InventoryOperationStatus.CONFLICT
                ||
                remoteStatus
                        == InventoryOperationStatus.REJECTED)
        {

            callback.onRemoteTerminalState(
                    remoteResult
            );

            return;
        }


        callback.onError(
                new IllegalStateException(
                        "Unexpected remote operation state."
                )
        );
    }


    private void markLocalSubmitted(
            String operationId,
            InventoryOperationRemoteResult remoteResult,
            UploadCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        InventoryOperationEntity latest =
                                inventoryOperationDao
                                        .getById(
                                                operationId
                                        );


                        if (latest == null)
                        {
                            throw new IllegalStateException(
                                    "Local operation disappeared before upload acknowledgement."
                            );
                        }

                        if (latest.getStatus()
                                == InventoryOperationStatus.CONFIRMED
                                ||
                                latest.getStatus()
                                        == InventoryOperationStatus.CONFLICT
                                ||
                                latest.getStatus()
                                        == InventoryOperationStatus.REJECTED)
                        {
                            postError(
                                    callback,
                                    new IllegalStateException(
                                            "Local operation is already terminal."
                                    )
                            );

                            return;
                        }


                        inventoryOperationDao
                                .updateStatus(
                                        operationId,
                                        InventoryOperationStatus.SUBMITTED,
                                        remoteResult.getResultCode(),
                                        remoteResult.getResultMessage()
                                );


                        appExecutors
                                .mainThread()
                                .execute(() ->
                                        callback.onSubmitted(
                                                remoteResult
                                        )
                                );
                    }
                    catch (Exception exception)
                    {
                        postError(
                                callback,
                                exception
                        );
                    }
                });
    }


    private boolean hasText(
            String value)
    {
        return value != null
                && !value.trim().isEmpty();
    }

    private void postError(
            UploadCallback callback,
            Exception exception)
    {
        appExecutors
                .mainThread()
                .execute(() ->
                        callback.onError(
                                exception
                        )
                );
    }



    public interface UploadCallback
    {
        void onSubmitted(
                InventoryOperationRemoteResult result
        );


        void onRemoteTerminalState(
                InventoryOperationRemoteResult result
        );


        void onError(
                Exception exception
        );
    }
}
