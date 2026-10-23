package com.medicationinventory.ui.receive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.model.domain.Batch;
import java.util.List;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.service.ReceiptService;
import com.medicationinventory.validation.ReceiptValidationService;



/**
 * Coordinates the receiving form's medicine and batch data with asynchronous receipt submission.
 * It prevents duplicate submissions while a request is active and translates service callbacks
 * into distinct validation, duplicate-lot, pending-cloud, and failure states for the activity.
 * A local save is shown as pending because trusted processing may still reject or conflict with
 * the operation later.
 */
public class ReceiveStockViewModel
        extends ViewModel
{

    private final String medicineId;
    private final ReceiptService receiptService;

    private final LiveData<Medicine> medicine;
    private final LiveData<List<Batch>> batches;

    private final MutableLiveData<ReceiptState> receiptState =
            new MutableLiveData<>(ReceiptState.idle());

    private boolean submissionInProgress = false;


    public ReceiveStockViewModel(String medicineId, MedicineRepository medicineRepository,
                                 BatchRepository batchRepository, ReceiptService receiptService)
    {
        this.medicineId = medicineId;

        this.receiptService = receiptService;

        medicine = medicineRepository.observeMedicine(medicineId);
        batches = batchRepository.observeBatchesForMedicine(medicineId);
    }

    public LiveData<Medicine> getMedicine()
    {
        return medicine;
    }

    public LiveData<List<Batch>> getBatches() { return batches; }

    public LiveData<ReceiptState> getReceiptState()
    {
        return receiptState;
    }

    public ReceiptValidationService.ValidationResult
    validateReceipt(String batchNumber, String quantity, long receivedDate, long expiryDate)
    {
        return ReceiptValidationService.validate(batchNumber, quantity, receivedDate, expiryDate);
    }


    /**
     * Submits the form values through {@link ReceiptService}; this method does not write stock
     * directly. The in-progress guard blocks a second tap until the service reports validation,
     * duplicate-batch, local persistence, or processing status.
     *
     * @param batchNumber lot number for a new batch
     * @param quantity quantity text retained for service validation
     * @param receivedDate receipt date in epoch milliseconds
     * @param expiryDate expiry date in epoch milliseconds
     */
    public void submitReceipt(String batchNumber, String quantity, long receivedDate,
                              long expiryDate)
    {

        if (submissionInProgress)
        {
            return;
        }

        submissionInProgress = true;

        receiptState.setValue(ReceiptState.loading());

        receiptService.createNewBatchReceipt(medicineId, batchNumber, quantity, receivedDate,
                expiryDate, new ReceiptService.ReceiptCallback()
                {
                    @Override

                    public void onValidationError(ReceiptValidationService.ValidationResult result)
                    {
                        submissionInProgress = false;

                        receiptState.setValue(ReceiptState.validationError(result));
                    }

                    @Override

                    public void onDuplicateBatch(String message)
                    {
                        submissionInProgress = false;

                        receiptState.setValue(ReceiptState.duplicateBatch(message));
                    }

                    @Override

                    public void onSuccess(ReceiptService.ReceiptCommitResult result)
                    {
                        submissionInProgress = false;

                        receiptState.setValue(ReceiptState.pending(result));
                    }

                    @Override
                    public void onError(String message)
                    {
                        submissionInProgress = false;

                        receiptState.setValue(ReceiptState.error(message));
                    }
                }
        );
    }


    public void submitExistingBatchReceipt(String batchId, String quantity,
                                           long receivedDate) {
        if (submissionInProgress) return;
        submissionInProgress = true;
        receiptState.setValue(ReceiptState.loading());
        receiptService.receiveIntoExistingBatch(medicineId, batchId, quantity,
                receivedDate, new ReceiptService.ReceiptCallback() {
                    @Override public void onValidationError(ReceiptValidationService.ValidationResult result) {
                        submissionInProgress = false;
                        receiptState.setValue(ReceiptState.validationError(result));
                    }
                    @Override public void onDuplicateBatch(String message) {
                        submissionInProgress = false;
                        receiptState.setValue(ReceiptState.duplicateBatch(message));
                    }
                    @Override public void onSuccess(ReceiptService.ReceiptCommitResult result) {
                        submissionInProgress = false;
                        receiptState.setValue(ReceiptState.pending(result));
                    }
                    @Override public void onError(String message) {
                        submissionInProgress = false;
                        receiptState.setValue(ReceiptState.error(message));
                    }
                });
    }

    public static class ReceiptState
    {
        public enum Status
        {
            IDLE, LOADING, VALIDATION_ERROR, DUPLICATE_BATCH, PENDING, ERROR
        }

        private final Status status;

        private final ReceiptValidationService.ValidationResult validationResult;

        private final ReceiptService.ReceiptCommitResult commitResult;

        private final String message;


        private ReceiptState(Status status,
                             ReceiptValidationService.ValidationResult validationResult,
                             ReceiptService.ReceiptCommitResult commitResult, String message)
        {
            this.status = status;

            this.validationResult = validationResult;

            this.commitResult = commitResult;

            this.message = message;
        }


        public static ReceiptState idle()
        {
            return new ReceiptState(Status.IDLE, null, null, null);
        }


        public static ReceiptState loading()
        {
            return new ReceiptState(Status.LOADING, null, null, null);
        }


        public static ReceiptState validationError(ReceiptValidationService.ValidationResult result)
        {
            return new ReceiptState(Status.VALIDATION_ERROR, result, null, null);
        }


        public static ReceiptState duplicateBatch(String message)
        {
            return new ReceiptState(Status.DUPLICATE_BATCH, null, null, message);
        }


        public static ReceiptState pending(ReceiptService.ReceiptCommitResult result)
        {
            return new ReceiptState(Status.PENDING, null, result, null);
        }


        public static ReceiptState error(String message)
        {

            return new ReceiptState(Status.ERROR, null, null, message);
        }

        public Status getStatus()
        {
            return status;
        }

        public ReceiptValidationService.ValidationResult
        getValidationResult()
        {
            return validationResult;
        }

        public ReceiptService.ReceiptCommitResult
        getCommitResult()
        {
            return commitResult;
        }

        public String getMessage()
        {
            return message;
        }
    }
}