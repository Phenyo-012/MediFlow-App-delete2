package com.medicationinventory.service;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.Medicine;

import com.medicationinventory.model.enums.BatchStatus;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.util.DeviceIdProvider;
import com.medicationinventory.validation.ReceiptValidationService;

import com.medicationinventory.sync.InventorySyncScheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;


/**
 * Coordinates the receipt workflow from form validation through current-account authorization, medicine and batch checks, and atomic local operation creation. A successful local save remains pending until the trusted backend confirms it; the scheduler is then asked to upload it. Existing-batch top-ups use a separate explicit path so a duplicate batch number cannot silently increase stock.
 */
public class ReceiptService
{
    private final AuthenticationRepository authenticationRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final BatchRepository batchRepository;
    private final LocalStockOperationService localStockOperationService;
    private final DeviceIdProvider deviceIdProvider;
    private final InventorySyncScheduler inventorySyncScheduler;


    public ReceiptService(AuthenticationRepository authenticationRepository,
                          UserRepository userRepository, MedicineRepository medicineRepository,
                          BatchRepository batchRepository, LocalStockOperationService localStockOperationService,
                          DeviceIdProvider deviceIdProvider, InventorySyncScheduler inventorySyncScheduler)
    {

        this.authenticationRepository = authenticationRepository;

        this.userRepository = userRepository;

        this.medicineRepository = medicineRepository;

        this.batchRepository = batchRepository;

        this.localStockOperationService = localStockOperationService;

        this.deviceIdProvider = deviceIdProvider;

        this.inventorySyncScheduler = inventorySyncScheduler;
    }


    /**
     * Validates a new-batch receipt and checks the current user and medicine before
     * saving a local operation for later trusted-server confirmation.
     *
     * @param medicineId medicine receiving stock
     * @param batchNumber supplier batch identifier
     * @param quantityText raw quantity entered in the form
     * @param receivedDate receipt date in epoch milliseconds
     * @param expiryDate batch expiry in epoch milliseconds
     * @param callback receives validation, authorization, or save outcome
     */
    public void createNewBatchReceipt(String medicineId, String batchNumber, String quantityText,
                                      long receivedDate, long expiryDate, ReceiptCallback callback) {
        ReceiptValidationService.ValidationResult validation = ReceiptValidationService.validate(
                batchNumber, quantityText, receivedDate, expiryDate);
        if (!validation.isValid()) {
            callback.onValidationError(validation);
            return;
        }
        authorizeAndVerify(medicineId, validation, null, callback);
    }


    /**
     * Adds stock to the selected existing batch only after confirming it belongs to
     * the medicine and remains eligible for receipt. This explicit path avoids turning
     * a duplicate new-batch receipt into an accidental top-up.
     */
    /**
     * Adds a receipt to a selected batch only after confirming its medicine, status, expiry, and quantity.
     * This explicit operation preserves the existing batch identity and does not create a new batch.
     * @param medicineId medicine expected to own the batch
     * @param batchId selected existing batch
     * @param quantityText quantity entered by the user
     * @param receivedDate receipt date in epoch milliseconds
     * @param callback receives validation, authorization, or persistence outcomes
     */
    public void receiveIntoExistingBatch(String medicineId, String batchId,
                                         String quantityText, long receivedDate, ReceiptCallback callback) {
        if (batchId == null || batchId.trim().isEmpty()) {
            callback.onError("Select an existing batch first.");
            return;
        }
        batchRepository.getBatchById(batchId, batch -> {
            if (batch == null || !batch.getMedicineId().equals(medicineId)) {
                callback.onError("Selected batch is unavailable for this medicine.");
                return;
            }
            ReceiptValidationService.ValidationResult validation =
                    ReceiptValidationService.validate(batch.getBatchNumber(), quantityText,
                            receivedDate, batch.getExpiryDate());
            if (!validation.isValid()) {
                callback.onValidationError(validation);
                return;
            }
            if (batch.getStatus() != BatchStatus.ACTIVE
                    && batch.getStatus() != BatchStatus.DEPLETED) {
                callback.onError("Only active or depleted, unexpired batches can receive stock.");
                return;
            }
            if (Instant.ofEpochMilli(batch.getExpiryDate()).atZone(ZoneId.of("Africa/Johannesburg"))
                    .toLocalDate().isBefore(LocalDate.now(ZoneId.of("Africa/Johannesburg")))) {
                callback.onError("An expired batch cannot receive stock.");
                return;
            }
            authorizeAndVerify(medicineId, validation, batch, callback);
        });
    }


    private void authorizeAndVerify(String medicineId,
                                    ReceiptValidationService.ValidationResult validation,
                                    com.medicationinventory.model.domain.Batch selectedBatch,
                                    ReceiptCallback callback) {
        if (medicineId == null || medicineId.trim().isEmpty()) {
            callback.onError("Medicine is required.");
            return;
        }
        String uid = authenticationRepository.getCurrentUserId();
        if (uid == null) {
            callback.onError("Authentication is required.");
            return;
        }
        userRepository.getUser(uid, profile -> {
            if (profile == null || !profile.isActive()) {
                callback.onError("Your user profile is unavailable or inactive.");
                return;
            }
            if (!profile.can(Permission.RECEIVE_STOCK)) {
                callback.onError("You do not have permission to receive stock.");
                return;
            }
            medicineRepository.getMedicineById(medicineId, medicine -> {
                if (medicine == null) {
                    callback.onError("Medicine could not be found.");
                    return;
                }
                if (!medicine.isActive()) {
                    callback.onError("Stock cannot be received for an inactive medicine.");
                    return;
                }
                if (selectedBatch == null) {
                    verifyBatchNumber(medicine, uid, validation, callback);
                } else {
                    commitExistingReceipt(medicine, uid, validation, selectedBatch, callback);
                }
            });
        });
    }

    private void commitExistingReceipt(Medicine medicine, String uid,
                                       ReceiptValidationService.ValidationResult validation,
                                       com.medicationinventory.model.domain.Batch selectedBatch, ReceiptCallback callback) {
        long now = System.currentTimeMillis();
        String operationId = UUID.randomUUID().toString();
        String batchId = selectedBatch.getBatchId();
        InventoryOperationEntity operation = new InventoryOperationEntity(operationId,
                InventoryOperationType.RECEIPT, medicine.getMedicineId(),
                validation.getQuantity(), null, uid, now,
                InventoryOperationStatus.LOCAL_PENDING, null, null,
                deviceIdProvider.getDeviceId(), null, null);
        operation.setReceiptMode("EXISTING_BATCH");
        operation.setReceiptReceivedDate(validation.getReceivedDate());
        OperationAllocationEntity allocation = new OperationAllocationEntity(
                operationId + "_allocation_0", operationId, batchId,
                validation.getQuantity(), 0);
        SyncQueueEntity queueItem = new SyncQueueEntity(operationId, 0, now, 0L, null);
        localStockOperationService.commitExistingBatchReceipt(operation, allocation, queueItem,
                new LocalStockOperationService.CommitCallback() {
                    @Override public void onSuccess() {
                        inventorySyncScheduler.schedulePendingSync();
                        callback.onSuccess(new ReceiptCommitResult(operationId, batchId,
                                InventoryOperationStatus.LOCAL_PENDING));
                    }
                    @Override public void onError(Exception exception) {
                        callback.onError("Unable to record additional stock locally: "
                                + exception.getMessage());
                    }
                });
    }

    private void verifyBatchNumber(Medicine medicine, String uid,
                                   ReceiptValidationService.ValidationResult validation, ReceiptCallback callback)
    {
        batchRepository.getBatchByNumber(medicine.getMedicineId(),
                validation.getNormalizedBatchNumber(), existingBatch ->
                {

                    if (existingBatch != null)
                    {
                        callback.onDuplicateBatch(
                                "A batch with this number already exists for this medicine.");

                        return;
                    }

                    commitReceipt(medicine, uid, validation, callback);
                }
        );
    }


    private void commitReceipt(Medicine medicine, String uid,
                               ReceiptValidationService.ValidationResult validation, ReceiptCallback callback)
    {
        long clientCreatedAt = System.currentTimeMillis();

        String operationId = UUID.randomUUID().toString();

        String batchId = UUID.randomUUID().toString();

        String allocationId = operationId + "_allocation_0";

        String transactionId = operationId + "_receipt_0";

        BatchStatus batchStatus = deriveBatchStatus(validation.getExpiryDate(),
                validation.getQuantity());

        InventoryOperationEntity operation = new InventoryOperationEntity(operationId,
                InventoryOperationType.RECEIPT, medicine.getMedicineId(),
                validation.getQuantity(), null, uid, clientCreatedAt,
                InventoryOperationStatus.LOCAL_PENDING, null, null,
                deviceIdProvider.getDeviceId(), null, null);

        operation.setReceiptMode("NEW_BATCH");
        operation.setReceiptReceivedDate(validation.getReceivedDate());

        BatchEntity batch = new BatchEntity(batchId, medicine.getMedicineId(),
                validation.getNormalizedBatchNumber(), validation.getReceivedDate(),
                validation.getExpiryDate(), validation.getQuantity(),
                validation.getQuantity(), batchStatus, 0L);

        OperationAllocationEntity allocation = new OperationAllocationEntity(allocationId,
                operationId, batchId, validation.getQuantity(), 0);

        InventoryTransactionEntity transaction = new InventoryTransactionEntity(transactionId,
                operationId, medicine.getMedicineId(), batchId, "RECEIPT",
                validation.getQuantity(), null, uid, clientCreatedAt, 0L,
                true);

        SyncQueueEntity queueItem = new SyncQueueEntity(operationId, 0, clientCreatedAt,
                0L, null);

        localStockOperationService.commitNewBatchReceipt(operation, batch, allocation, transaction,
                queueItem, new LocalStockOperationService.CommitCallback()
                {
                    @Override
                    public void onSuccess()
                    {
                        inventorySyncScheduler.schedulePendingSync();

                        callback.onSuccess(new ReceiptCommitResult(operationId, batchId,
                                InventoryOperationStatus.LOCAL_PENDING));
                    }

                    @Override
                    public void onError(Exception exception)
                    {
                        callback.onError("Unable to record the stock receipt locally.");
                    }
                }
        );
    }

    private BatchStatus deriveBatchStatus(long expiryDate, int quantity)
    {
        if (quantity <= 0)
        {
            return BatchStatus.DEPLETED;
        }

        LocalDate expiry = Instant.ofEpochMilli(expiryDate).atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate today = LocalDate.now();

        if (expiry.isBefore(today))
        {
            return BatchStatus.EXPIRED;
        }

        return BatchStatus.ACTIVE;
    }

    public interface ReceiptCallback
    {
        void onValidationError(ReceiptValidationService.ValidationResult result);

        void onDuplicateBatch(String message);

        void onSuccess(ReceiptCommitResult result);

        void onError(String message);
    }

    public static class ReceiptCommitResult
    {
        private final String operationId;
        private final String batchId;

        private final InventoryOperationStatus status;


        public ReceiptCommitResult(String operationId, String batchId,
                                   InventoryOperationStatus status)
        {
            this.operationId = operationId;

            this.batchId = batchId;

            this.status = status;
        }

        public String getOperationId()
        {
            return operationId;
        }

        public String getBatchId()
        {
            return batchId;
        }

        public InventoryOperationStatus getStatus()
        {
            return status;
        }
    }
}