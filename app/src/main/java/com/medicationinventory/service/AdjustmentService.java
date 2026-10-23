package com.medicationinventory.service;

import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.enums.AdjustmentDirection;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.sync.InventorySyncScheduler;

import com.medicationinventory.util.DeviceIdProvider;

import java.util.UUID;



/**
 * Creates auditable, permission-checked adjustments to a medicine batch. The service validates the requested direction, quantity, reason, and target, then rechecks the active user and relevant inventory before atomically recording a pending operation and movement. It schedules trusted processing after the local commit; the adjustment does not directly establish a cloud-confirmed balance.
 */
public class AdjustmentService
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


    public AdjustmentService(
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
     * Validates and authorizes a manual quantity change before creating a pending operation.
     * The adjustment reason is retained with the transaction for later audit and sync.
     * @param medicineId medicine whose batch is adjusted
     * @param batchId target batch
     * @param direction whether stock is added or removed
     * @param quantityText user-entered quantity, validated before persistence
     * @param reason explanation retained with the operation history
     * @param callback receives validation, authorization, save, or processing status
     */
    public void commitAdjustment(
            String medicineId,
            String batchId,
            AdjustmentDirection direction,
            String quantityText,
            String reason,
            AdjustmentCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Adjustment callback is required."
            );
        }


        String validationError =
                validateInput(
                        medicineId,
                        batchId,
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
                    "Adjustment quantity must be a whole number."
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
                                "Stock cannot be adjusted for an inactive medicine."
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
                                        Permission.CREATE_ADJUSTMENT
                                ))
                                {
                                    callback.onError(
                                            "You do not have permission to adjust stock."
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


                                commitValidatedAdjustment(
                                        medicineId,
                                        batchId,
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



    private void commitValidatedAdjustment(
            String medicineId,
            String batchId,
            AdjustmentDirection direction,
            int quantity,
            String reason,
            String uid,
            AdjustmentCallback callback)
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
                        + "_adjustment_0";


        int signedQuantityChange =
                direction
                        == AdjustmentDirection.IN
                        ? quantity
                        : -quantity;


        InventoryOperationEntity operation =
                new InventoryOperationEntity(
                        operationId,
                        InventoryOperationType.ADJUSTMENT,
                        medicineId,
                        quantity,
                        reason,
                        uid,
                        clientCreatedAt,
                        InventoryOperationStatus.LOCAL_PENDING,


                        null,


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
                        "ADJUSTMENT",
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
                .commitAdjustment(
                        operation,
                        allocation,
                        transaction,
                        queueItem,
                        new LocalStockOperationService.CommitCallback()
                        {
                            @Override

                            public void onSuccess()
                            {
                                /*
                                 * Request sync only after the whole
                                 * Room transaction has committed.
                                 */
                                inventorySyncScheduler
                                        .schedulePendingSync();


                                callback.onSuccess(
                                        new AdjustmentCommitResult(
                                                operationId,
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
            AdjustmentDirection direction,
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


        if (direction == null)
        {
            return "Adjustment direction is required.";
        }


        if (quantityText == null
                || quantityText.trim().isEmpty())
        {
            return "Adjustment quantity is required.";
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
            return "Adjustment quantity must be a whole number.";
        }


        if (quantity <= 0)
        {
            return "Adjustment quantity must be greater than zero.";
        }


        if (reason == null
                || reason.trim().isEmpty())
        {
            return "A reason is required for a stock adjustment.";
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
            return "This adjustment would result in negative stock and was not recorded.";
        }


        if ("INVALID_BATCH".equals(
                message
        ))
        {
            return "The selected batch is no longer available for this medicine.";
        }


        if ("BATCH_UPDATE_FAILED".equals(
                message
        ))
        {
            return "The batch changed while the adjustment was being recorded. Please try again.";
        }


        if ("ADJUSTMENT_REASON_REQUIRED".equals(
                message
        ))
        {
            return "A reason is required for a stock adjustment.";
        }


        if ("INVALID_QUANTITY".equals(
                message
        ))
        {
            return "The adjustment quantity is invalid.";
        }


        return "Unable to record the stock adjustment locally.";
    }


    public interface AdjustmentCallback
    {
        void onSuccess(
                AdjustmentCommitResult result
        );


        void onError(
                String message
        );
    }


    public static class AdjustmentCommitResult
    {
        private final String operationId;
        private final String batchId;

        private final AdjustmentDirection direction;

        private final InventoryOperationStatus status;



        public AdjustmentCommitResult(
                String operationId,
                String batchId,
                AdjustmentDirection direction,
                InventoryOperationStatus status)
        {
            this.operationId =
                    operationId;

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


        public String getBatchId()
        {
            return batchId;
        }


        public AdjustmentDirection getDirection()
        {
            return direction;
        }


        public InventoryOperationStatus getStatus()
        {
            return status;
        }
    }
}