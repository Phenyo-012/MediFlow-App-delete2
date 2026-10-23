package com.medicationinventory.service;

import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.enums.CorrectionDirection;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.sync.InventorySyncScheduler;

import com.medicationinventory.util.DeviceIdProvider;

import java.util.UUID;


/**
 * Records a compensating inventory operation when a previously confirmed movement needs correction. The original history remains intact; the new movement references the related operation, is validated and authorized against current account and batch state, and follows the pending local commit and trusted-processing path.
 */
public class CorrectionService
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MedicineRepository
            medicineRepository;

    private final LocalStockOperationService
            localStockOperationService;

    private final DeviceIdProvider
            deviceIdProvider;

    private final InventorySyncScheduler
            inventorySyncScheduler;


    public CorrectionService(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            LocalStockOperationService localStockOperationService,
            DeviceIdProvider deviceIdProvider,
            InventorySyncScheduler inventorySyncScheduler)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.medicineRepository =
                medicineRepository;

        this.localStockOperationService =
                localStockOperationService;

        this.deviceIdProvider =
                deviceIdProvider;

        this.inventorySyncScheduler =
                inventorySyncScheduler;
    }


    /**
     * Records a compensating movement linked to the original operation instead of editing
     * its confirmed transaction history in place.
     * @param medicineId medicine whose stock is corrected
     * @param batchId batch affected by the correction
     * @param relatedOperationId confirmed operation being corrected
     * @param direction whether the compensating movement adds or removes stock
     * @param quantityText user-entered quantity
     * @param reason explanation retained for audit
     * @param callback receives validation, authorization, save, or processing status
     */
    public void commitCorrection(
            String medicineId,
            String batchId,
            String relatedOperationId,
            CorrectionDirection direction,
            String quantityText,
            String reason,
            CorrectionCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Correction callback is required."
            );
        }


        String validationError =
                validateInput(
                        medicineId,
                        batchId,
                        relatedOperationId,
                        direction,
                        quantityText,
                        reason
                );


        if (validationError != null)
        {
            callback.onError(
                    validationError
            );

            return;
        }


        int quantity;


        try
        {
            quantity =
                    Integer.parseInt(
                            quantityText.trim()
                    );
        }
        catch (NumberFormatException exception)
        {
            callback.onError(
                    "Correction quantity must be a whole number."
            );

            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                || uid.trim().isEmpty())
        {
            callback.onError(
                    "Authentication is required."
            );

            return;
        }

        medicineRepository.getMedicineById(
                medicineId,
                medicine ->
                {
                    if (medicine == null)
                    {
                        callback.onError(
                                "Medicine could not be found."
                        );

                        return;
                    }


                    if (!medicine.isActive())
                    {
                        callback.onError(
                                "Stock cannot be corrected for an inactive medicine."
                        );

                        return;
                    }


                    userRepository.getUser(
                            uid,
                            userProfile ->
                            {
                                if (userProfile == null
                                        || !userProfile.isActive())
                                {
                                    callback.onError(
                                            "Your user profile is unavailable or inactive."
                                    );

                                    return;
                                }


                                if (!userProfile.can(
                                        Permission.CREATE_CORRECTION
                                ))
                                {
                                    callback.onError(
                                            "You do not have permission to correct stock."
                                    );

                                    return;
                                }


                                String latestUid =
                                        authenticationRepository
                                                .getCurrentUserId();


                                if (latestUid == null
                                        || !uid.equals(
                                        latestUid
                                ))
                                {
                                    callback.onError(
                                            "Your authentication session changed. Please try again."
                                    );

                                    return;
                                }


                                commitValidatedCorrection(
                                        medicineId,
                                        batchId,
                                        relatedOperationId.trim(),
                                        direction,
                                        quantity,
                                        reason.trim(),
                                        uid,
                                        callback
                                );
                            }
                    );
                }
        );
    }


    private void commitValidatedCorrection(
            String medicineId,
            String batchId,
            String relatedOperationId,
            CorrectionDirection direction,
            int quantity,
            String reason,
            String uid,
            CorrectionCallback callback)
    {
        long clientCreatedAt =
                System.currentTimeMillis();


        String operationId =
                UUID.randomUUID()
                        .toString();


        String allocationId =
                operationId
                        + "_allocation_0";


        String transactionId =
                operationId
                        + "_correction_0";


        int signedQuantityChange =
                direction
                        == CorrectionDirection.IN
                        ? quantity
                        : -quantity;


        InventoryOperationEntity operation =
                new InventoryOperationEntity(
                        operationId,
                        InventoryOperationType.CORRECTION,
                        medicineId,
                        quantity,
                        reason,
                        uid,
                        clientCreatedAt,
                        InventoryOperationStatus.LOCAL_PENDING,


                        relatedOperationId,


                        null,

                        deviceIdProvider.getDeviceId(),
                        null,
                        null
                );


        OperationAllocationEntity allocation =
                new OperationAllocationEntity(
                        allocationId,
                        operationId,
                        batchId,
                        quantity,
                        0
                );


        InventoryTransactionEntity transaction =
                new InventoryTransactionEntity(
                        transactionId,
                        operationId,
                        medicineId,
                        batchId,
                        "CORRECTION",
                        signedQuantityChange,
                        reason,
                        uid,
                        clientCreatedAt,
                        0L,
                        true
                );


        SyncQueueEntity queueItem =
                new SyncQueueEntity(
                        operationId,
                        0,
                        clientCreatedAt,
                        0L,
                        null
                );


        localStockOperationService
                .commitCorrection(
                        operation,
                        allocation,
                        transaction,
                        queueItem,
                        new LocalStockOperationService.CommitCallback()
                        {
                            @Override

                            public void onSuccess()
                            {

                                inventorySyncScheduler
                                        .schedulePendingSync();


                                callback.onSuccess(
                                        new CorrectionCommitResult(
                                                operationId,
                                                relatedOperationId,
                                                batchId,
                                                direction,
                                                InventoryOperationStatus.LOCAL_PENDING
                                        )
                                );
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {
                                callback.onError(
                                        friendlyCommitError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }


    private String validateInput(
            String medicineId,
            String batchId,
            String relatedOperationId,
            CorrectionDirection direction,
            String quantityText,
            String reason)
    {
        if (medicineId == null
                || medicineId.trim().isEmpty())
        {
            return "Medicine is required.";
        }


        if (batchId == null
                || batchId.trim().isEmpty())
        {
            return "Batch is required.";
        }


        if (relatedOperationId == null
                || relatedOperationId
                .trim()
                .isEmpty())
        {
            return "The original operation is required.";
        }


        if (direction == null)
        {
            return "Correction direction is required.";
        }


        if (quantityText == null
                || quantityText.trim().isEmpty())
        {
            return "Correction quantity is required.";
        }


        int quantity;


        try
        {
            quantity =
                    Integer.parseInt(
                            quantityText.trim()
                    );
        }
        catch (NumberFormatException exception)
        {
            return "Correction quantity must be a whole number.";
        }


        if (quantity <= 0)
        {
            return "Correction quantity must be greater than zero.";
        }


        if (reason == null
                || reason.trim().isEmpty())
        {
            return "A reason is required for a stock correction.";
        }


        return null;
    }


    private String friendlyCommitError(
            Exception exception)
    {
        String message =
                exception == null
                        ? null
                        : exception.getMessage();


        if ("NEGATIVE_STOCK".equals(
                message
        ))
        {
            return "This correction would result in negative stock and was not recorded.";
        }


        if ("INVALID_BATCH".equals(
                message
        ))
        {
            return "The selected batch is no longer available for this medicine.";
        }


        if ("ORIGINAL_OPERATION_NOT_FOUND".equals(
                message
        ))
        {
            return "The original operation is not available locally and cannot be corrected.";
        }


        if ("ORIGINAL_OPERATION_NOT_CONFIRMED".equals(
                message
        ))
        {
            return "Only a confirmed inventory operation can be corrected.";
        }


        if ("ORIGINAL_OPERATION_MEDICINE_MISMATCH".equals(
                message
        ))
        {
            return "The selected original operation belongs to a different medicine.";
        }


        if ("ORIGINAL_OPERATION_BATCH_MISMATCH".equals(
                message
        ))
        {
            return "The selected batch was not part of the original confirmed operation.";
        }


        if ("BATCH_UPDATE_FAILED".equals(
                message
        ))
        {
            return "The batch changed while the correction was being recorded. Please try again.";
        }


        if ("CORRECTION_REASON_REQUIRED".equals(
                message
        ))
        {
            return "A reason is required for a stock correction.";
        }


        if ("CORRECTION_RELATED_OPERATION_REQUIRED".equals(
                message
        ))
        {
            return "The original operation is required for a correction.";
        }


        if ("INVALID_QUANTITY".equals(
                message
        ))
        {
            return "The correction quantity is invalid.";
        }


        return "Unable to record the stock correction locally.";
    }


    public interface CorrectionCallback
    {
        void onSuccess(
                CorrectionCommitResult result
        );


        void onError(
                String message
        );
    }


    /*
     * COMMIT RESULT
     */
    public static class CorrectionCommitResult
    {
        private final String operationId;

        private final String relatedOperationId;

        private final String batchId;

        private final CorrectionDirection direction;

        private final InventoryOperationStatus status;


        public CorrectionCommitResult(
                String operationId,
                String relatedOperationId,
                String batchId,
                CorrectionDirection direction,
                InventoryOperationStatus status)
        {
            this.operationId =
                    operationId;

            this.relatedOperationId =
                    relatedOperationId;

            this.batchId =
                    batchId;

            this.direction =
                    direction;

            this.status =
                    status;
        }


        public String getOperationId()
        {
            return operationId;
        }


        public String getRelatedOperationId()
        {
            return relatedOperationId;
        }


        public String getBatchId()
        {
            return batchId;
        }


        public CorrectionDirection getDirection()
        {
            return direction;
        }


        public InventoryOperationStatus getStatus()
        {
            return status;
        }
    }
}