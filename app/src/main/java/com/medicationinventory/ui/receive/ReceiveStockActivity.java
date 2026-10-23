package com.medicationinventory.ui.receive;

import android.app.Activity;
import android.content.Intent;
import android.app.DatePickerDialog;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import java.util.ArrayList;
import java.util.List;
import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.enums.BatchStatus;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.service.ReceiptService;
import com.medicationinventory.validation.ReceiptValidationService;
import com.medicationinventory.ui.scanner.BarcodeScannerActivity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;



/**
 * Implements the Android screen for Receive Stock and connects user actions to application state.
 */
public class ReceiveStockActivity
        extends AppCompatActivity
{

    public static final String EXTRA_MEDICINE_ID = "medicine_id";

    private static final String STATE_RECEIVED_DATE = "received_date";

    private static final String STATE_EXPIRY_DATE = "expiry_date";
    private static final String STATE_RECEIPT_MODE = "existing_receipt_mode";
    private static final String STATE_EXISTING_BATCH_ID = "existing_batch_id";

    private final DateTimeFormatter displayDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private TextView medicineNameText;
    private TextView medicineCodeText;

    private TextInputLayout batchNumberLayout;
    private TextInputLayout existingBatchLayout;
    private TextInputEditText existingBatchInput;
    private RadioGroup receiptModeGroup;
    private boolean existingMode = false;
    private String selectedExistingBatchId;
    private Batch selectedExistingBatch;
    private List<Batch> availableBatches = new ArrayList<>();
    private TextInputLayout quantityLayout;
    private TextInputLayout receivedDateLayout;
    private TextInputLayout expiryDateLayout;

    private TextInputEditText batchNumberInput;
    private TextInputEditText quantityInput;
    private TextInputEditText receivedDateInput;
    private TextInputEditText expiryDateInput;

    private TextView formErrorText;

    private ProgressBar receiptProgress;

    private MaterialButton reviewReceiptButton;

    private View receiptFormScroll;
    private View pendingStateContainer;

    private TextView pendingMessageText;
    private TextView pendingReferenceText;

    private MaterialButton backToMedicineButton;

    private ReceiveStockViewModel viewModel;
    private MedicineRepository medicineRepository;
    private String medicineId;
    private MaterialButton scanReceiptBarcodeButton;
    private TextView receiptScanStatusText;

    private final ActivityResultLauncher<Intent> receiptScanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                String scanned = result.getData().getStringExtra(
                        BarcodeScannerActivity.EXTRA_SCANNED_VALUE);
                verifyReceiptScan(scanned);
            });

    private long selectedReceivedDate = 0L;

    private long selectedExpiryDate = 0L;

    @Override

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        medicineId = getIntent().getStringExtra(EXTRA_MEDICINE_ID);

        if (medicineId == null || medicineId.trim().isEmpty())
        {
            finish();
            return;
        }

        setContentView(R.layout.activity_receive_stock);

        bindViews();

        if (savedInstanceState != null)
        {
            selectedReceivedDate = savedInstanceState.getLong(STATE_RECEIVED_DATE, 0L);

            selectedExpiryDate = savedInstanceState.getLong(STATE_EXPIRY_DATE, 0L);
            existingMode = savedInstanceState.getBoolean(STATE_RECEIPT_MODE, false);
            selectedExistingBatchId = savedInstanceState.getString(STATE_EXISTING_BATCH_ID);

            restoreDateText();
        }

        setupViewModel(medicineId);

        setupActions();

        observeData();
    }

    private void bindViews()
    {
        medicineNameText = findViewById(R.id.medicineNameText);

        medicineCodeText = findViewById(R.id.medicineCodeText);

        batchNumberLayout = findViewById(R.id.batchNumberLayout);
        existingBatchLayout = findViewById(R.id.existingBatchLayout);
        existingBatchInput = findViewById(R.id.existingBatchInput);
        receiptModeGroup = findViewById(R.id.receiptModeGroup);

        quantityLayout = findViewById(R.id.quantityLayout);

        receivedDateLayout = findViewById(R.id.receivedDateLayout);

        expiryDateLayout = findViewById(R.id.expiryDateLayout);

        batchNumberInput = findViewById(R.id.batchNumberInput);

        quantityInput = findViewById(R.id.quantityInput);

        receivedDateInput = findViewById(R.id.receivedDateInput);

        expiryDateInput = findViewById(R.id.expiryDateInput);

        formErrorText = findViewById(R.id.formErrorText);

        receiptProgress = findViewById(R.id.receiptProgress);

        reviewReceiptButton = findViewById(R.id.reviewReceiptButton);

        receiptFormScroll = findViewById(R.id.receiptFormScroll);

        pendingStateContainer = findViewById(R.id.pendingStateContainer);

        pendingMessageText = findViewById(R.id.pendingMessageText);

        pendingReferenceText = findViewById(R.id.pendingReferenceText);

        backToMedicineButton = findViewById(R.id.backToMedicineButton);
        scanReceiptBarcodeButton = findViewById(R.id.scanReceiptBarcodeButton);
        receiptScanStatusText = findViewById(R.id.receiptScanStatusText);
    }


    private void setupViewModel(String medicineId)
    {
        MedicationInventoryApplication application = (MedicationInventoryApplication)
                getApplication();

        medicineRepository = application.getAppContainer().getMedicineRepository();

        ReceiptService receiptService = application.getAppContainer().getReceiptService();

        ReceiveStockViewModelFactory factory =
                new ReceiveStockViewModelFactory(medicineId, medicineRepository,
                        application.getAppContainer().getBatchRepository(), receiptService);

        viewModel = new ViewModelProvider(this, factory).get(ReceiveStockViewModel.class);
    }


    private void setupActions()
    {
        ImageButton backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(view -> finish());

        receivedDateInput.setOnClickListener(view -> showDatePicker(true));

        expiryDateInput.setOnClickListener(view -> showDatePicker(false));

        reviewReceiptButton.setOnClickListener(view -> validateBeforeConfirmation());
        existingBatchInput.setOnClickListener(view -> showBatchPicker());
        receiptModeGroup.setOnCheckedChangeListener((group, id) -> {
            boolean newExistingMode = id == R.id.receiveExistingBatchRadio;
            if (newExistingMode != existingMode) {
                existingMode = newExistingMode;
                selectedExistingBatchId = null;
                selectedExistingBatch = null;
                existingBatchInput.setText("");
                batchNumberInput.setText("");
                selectedExpiryDate = 0L;
                expiryDateInput.setText("");
                clearErrors();
            }
            renderReceiptMode();
        });
        receiptModeGroup.check(existingMode
                ? R.id.receiveExistingBatchRadio : R.id.createNewBatchRadio);
        renderReceiptMode();

        backToMedicineButton.setOnClickListener(view -> finish());
        scanReceiptBarcodeButton.setOnClickListener(view ->
                receiptScanLauncher.launch(new Intent(this, BarcodeScannerActivity.class)));
    }


    private void observeData()
    {
        viewModel.getMedicine().observe(this, this::showMedicine);
        viewModel.getBatches().observe(this, batches -> {
            availableBatches = batches == null ? new ArrayList<>() : batches;
            if (selectedExistingBatchId != null) {
                selectedExistingBatch = null;
                for (Batch batch : availableBatches) {
                    if (selectedExistingBatchId.equals(batch.getBatchId())) {
                        selectedExistingBatch = batch;
                        existingBatchInput.setText(batch.getBatchNumber());
                        batchNumberInput.setText(batch.getBatchNumber());
                        selectedExpiryDate = batch.getExpiryDate();
                        expiryDateInput.setText(formatDate(selectedExpiryDate));
                        break;
                    }
                }
                if (selectedExistingBatch == null) {
                    selectedExistingBatchId = null;
                    existingBatchInput.setText("");
                }
            }
        });

        viewModel.getReceiptState().observe(this, this::handleReceiptState);
    }


    private void showMedicine(Medicine medicine)
    {
        if (medicine == null)
        {
            return;
        }

        medicineNameText.setText(medicine.getMedicineName());

        medicineCodeText.setText(medicine.getMedicineCode());
    }


    private void renderReceiptMode() {
        batchNumberLayout.setVisibility(existingMode ? View.GONE : View.VISIBLE);
        existingBatchLayout.setVisibility(existingMode ? View.VISIBLE : View.GONE);
        expiryDateInput.setEnabled(!existingMode);
        expiryDateLayout.setHelperText(existingMode
                ? "Expiry is inherited from the selected batch." : null);
    }


    private void showBatchPicker() {
        List<Batch> eligible = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        for (Batch batch : availableBatches) {
            if (batch == null || (batch.getStatus() != BatchStatus.ACTIVE
                    && batch.getStatus() != BatchStatus.DEPLETED)) continue;
            if (Instant.ofEpochMilli(batch.getExpiryDate())
                    .atZone(ZoneId.of("Africa/Johannesburg"))
                    .toLocalDate().isBefore(today)) continue;
            eligible.add(batch);
        }
        if (eligible.isEmpty()) {
            showError("No eligible existing batches are available on this device. Synchronize inventory first.");
            return;
        }
        String[] choices = new String[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) {
            Batch batch = eligible.get(i);
            choices[i] = batch.getBatchNumber() + " (" + batch.getQuantityAvailable()
                    + " in stock, expires " + formatDate(batch.getExpiryDate()) + ")";
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select existing batch")
                .setItems(choices, (dialog, which) -> {
                    Batch batch = eligible.get(which);
                    selectedExistingBatch = batch;
                    selectedExistingBatchId = batch.getBatchId();
                    existingBatchInput.setText(batch.getBatchNumber());
                    batchNumberInput.setText(batch.getBatchNumber());
                    selectedExpiryDate = batch.getExpiryDate();
                    expiryDateInput.setText(formatDate(selectedExpiryDate));
                    existingBatchLayout.setError(null);
                    clearErrors();
                })
                .show();
    }


    private void verifyReceiptScan(String scanned) {
        scanReceiptBarcodeButton.setEnabled(false);
        receiptScanStatusText.setText("Verifying scanned medicine…");
        receiptScanStatusText.setVisibility(View.VISIBLE);
        medicineRepository.verifyScanForMedicine(medicineId, scanned,
                new MedicineRepository.ScanVerificationCallback() {
                    @Override

                    public void onResult(MedicineRepository.ScanVerificationResult result) {
                        scanReceiptBarcodeButton.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        if (!result.isMatch()) {
                            receiptScanStatusText.setText("Scan not verified. The receipt remains unchanged.");
                            String explanation = result.getStatus()
                                    == MedicineRepository.ScanVerificationResult.Status.DIFFERENT_MEDICINE
                                    ? "This barcode belongs to " + result.getMatchedMedicineName()
                                      + ", not the selected medicine."
                                    : result.getStatus() == MedicineRepository.ScanVerificationResult.Status.INACTIVE
                                      ? "This barcode belongs to an inactive medicine."
                                      : "The barcode is not linked to a medicine on this device. "
                                        + "Add it in Medicine Details first.";
                            new MaterialAlertDialogBuilder(ReceiveStockActivity.this)
                                    .setTitle("Medicine not verified")
                                    .setMessage(explanation + " No receipt fields were modified.")
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        onReceiptMedicineVerified(result);
                    }
                    @Override
                    public void onError(Exception error) {
                        scanReceiptBarcodeButton.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        receiptScanStatusText.setText("Scan could not be verified; no values changed.");
                        new MaterialAlertDialogBuilder(ReceiveStockActivity.this)
                                .setTitle("Unable to verify barcode")
                                .setMessage(error.getMessage() == null
                                        ? "Try another code or enter the details manually."
                                        : error.getMessage())
                                .setPositiveButton("OK", null).show();
                    }
                });
    }


    private void onReceiptMedicineVerified(MedicineRepository.ScanVerificationResult result) {
        receiptScanStatusText.setText("Medicine verified: " + result.getIdentifier());
        if (existingMode) {
            Toast.makeText(this, "Medicine verified. Select an existing batch explicitly; scan details will not replace it.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String lot = result.getBatchNumber();
        LocalDate expiry = result.getExpiryDate();
        if (lot == null && expiry == null) {
            Toast.makeText(this, "Medicine verified. Continue entering receipt details.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String message = "The code matches the selected medicine.\n\n"
                + "Scanned batch: " + (lot == null ? "Not included" : lot)
                + "\nScanned expiry: " + (expiry == null ? "Not included"
                : expiry.format(displayDateFormat))
                + "\n\nReview the packaging before applying these values. "
                + "Existing batch/expiry fields will be replaced. "
                + "Quantity and received date will not change.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("Verify scanned batch details")
                .setMessage(message)
                .setNegativeButton("Keep current values", null)
                .setPositiveButton("Apply scanned details", (dialog, which) -> {
                    if (lot != null) {
                        batchNumberInput.setText(lot);
                        batchNumberLayout.setError(null);
                    }
                    if (expiry != null) {
                        selectedExpiryDate = expiry.atStartOfDay(ZoneId.systemDefault())
                                .toInstant().toEpochMilli();
                        expiryDateInput.setText(formatDate(selectedExpiryDate));
                        expiryDateLayout.setError(null);
                    }
                }).show();
    }


    private void showDatePicker(boolean receivedDate)
    {

        long selected = receivedDate ? selectedReceivedDate : selectedExpiryDate;

        LocalDate initialDate;

        if (selected > 0L)
        {
            initialDate = toLocalDate(selected);
        }
        else
        {
            initialDate = LocalDate.now();
        }

        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) ->
                {

                    LocalDate localDate = LocalDate.of(year, month + 1, dayOfMonth);

                    long millis = localDate.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli();

                    if (receivedDate)
                    {
                        selectedReceivedDate = millis;

                        receivedDateInput.setText(formatDate(millis));

                        receivedDateLayout.setError(null);

                    }
                    else
                    {
                        selectedExpiryDate = millis;

                        expiryDateInput.setText(formatDate(millis));

                        expiryDateLayout.setError(null);
                    }
                },
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        );

        dialog.show();
    }


    private void validateBeforeConfirmation()
    {
        clearErrors();
        if (existingMode && selectedExistingBatch == null) {
            existingBatchLayout.setError("Select a valid existing batch.");
            return;
        }

        ReceiptValidationService.ValidationResult validation =
                viewModel.validateReceipt(textOf(batchNumberInput), textOf(quantityInput),
                        selectedReceivedDate, selectedExpiryDate);

        if (!validation.isValid())
        {
            showValidationErrors(validation);

            return;
        }

        showConfirmationDialog(validation);
    }


    private void showConfirmationDialog(ReceiptValidationService.ValidationResult validation)
    {
        String medicineName = medicineNameText.getText().toString();

        String message = (existingMode
                ? "Mode: Receive into existing batch\n\n"
                : "Mode: Create new batch\n\n") + "Medicine: "
                + medicineName
                + "\n\nBatch: "
                + validation.getNormalizedBatchNumber()
                + "\nQuantity: "
                + validation.getQuantity()
                + "\nReceived: "
                + formatDate(validation.getReceivedDate())
                + "\nExpiry: "
                + formatDate(
                validation.getExpiryDate())
                + "\n\nThis receipt will be recorded locally and queued for synchronization.";

        new MaterialAlertDialogBuilder(this).setTitle("Confirm Stock Receipt")
                .setMessage(message).setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm Receipt", (dialog, which)
                        -> submitReceipt()).show();
    }


    private void submitReceipt()
    {
        if (existingMode) {
            viewModel.submitExistingBatchReceipt(selectedExistingBatchId,
                    textOf(quantityInput), selectedReceivedDate);
        } else {
            viewModel.submitReceipt(textOf(batchNumberInput), textOf(quantityInput),
                    selectedReceivedDate, selectedExpiryDate);
        }
    }


    private void handleReceiptState(ReceiveStockViewModel.ReceiptState state)
    {
        if (state == null)
        {
            return;
        }

        switch (state.getStatus())
        {
            case IDLE:
                showIdle();

                break;

            case LOADING:
                showLoading();

                break;

            case VALIDATION_ERROR:
                showIdle();
                showValidationErrors(state.getValidationResult());

                break;

            case DUPLICATE_BATCH:
                showIdle();
                batchNumberLayout.setError(state.getMessage());

                break;

            case PENDING:
                showPending(state.getCommitResult());

                break;

            case ERROR:
                showIdle();
                showError(state.getMessage());

                break;
        }
    }


    private void showIdle()
    {
        receiptProgress.setVisibility(View.GONE);

        setFormEnabled(true);
    }


    private void showLoading()
    {
        clearErrors();

        receiptProgress.setVisibility(View.VISIBLE);

        setFormEnabled(false);
    }


    private void showPending(ReceiptService.ReceiptCommitResult result)
    {
        receiptProgress.setVisibility(View.GONE);

        receiptFormScroll.setVisibility(View.GONE);

        pendingStateContainer.setVisibility(View.VISIBLE);

        pendingMessageText.setText("The stock receipt was recorded locally. "
                + "It is waiting for synchronization and has not yet been cloud-confirmed.");

        if (result != null)
        {
            pendingReferenceText.setText("Operation reference: " + result.getOperationId());
        }
    }


    private void showValidationErrors(ReceiptValidationService.ValidationResult result)
    {
        if (result == null)
        {
            return;
        }

        batchNumberLayout.setError(result.getBatchNumberError());

        quantityLayout.setError(result.getQuantityError());

        receivedDateLayout.setError(result.getReceivedDateError());

        expiryDateLayout.setError(result.getExpiryDateError());
    }


    private void showError(String message)
    {
        formErrorText.setText(message == null ? "Unable to record stock receipt." : message);

        formErrorText.setVisibility(View.VISIBLE);
    }


    private void clearErrors()
    {
        batchNumberLayout.setError(null);
        existingBatchLayout.setError(null);
        quantityLayout.setError(null);
        receivedDateLayout.setError(null);
        expiryDateLayout.setError(null);

        formErrorText.setVisibility(View.GONE);
    }

    private void setFormEnabled(boolean enabled)
    {
        batchNumberInput.setEnabled(enabled && !existingMode);
        existingBatchInput.setEnabled(enabled);
        receiptModeGroup.setEnabled(enabled);
        receiptModeGroup.findViewById(R.id.createNewBatchRadio).setEnabled(enabled);
        receiptModeGroup.findViewById(R.id.receiveExistingBatchRadio).setEnabled(enabled);

        quantityInput.setEnabled(enabled);

        receivedDateInput.setEnabled(enabled);

        expiryDateInput.setEnabled(enabled && !existingMode);

        reviewReceiptButton.setEnabled(enabled);
        scanReceiptBarcodeButton.setEnabled(enabled);
    }


    private String textOf(TextInputEditText input)
    {
        if (input.getText() == null)
        {
            return "";
        }

        return input.getText().toString();
    }


    private String formatDate(long millis)
    {
        if (millis <= 0L)
        {
            return "";
        }

        return toLocalDate(millis).format(displayDateFormat);
    }


    private LocalDate toLocalDate(long millis)
    {

        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }


    private void restoreDateText()
    {
        if (selectedReceivedDate > 0L)
        {
            receivedDateInput.setText(formatDate(selectedReceivedDate));
        }

        if (selectedExpiryDate > 0L)
        {
            expiryDateInput.setText(formatDate(selectedExpiryDate));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putLong(STATE_RECEIVED_DATE, selectedReceivedDate);

        outState.putLong(STATE_EXPIRY_DATE, selectedExpiryDate);
        outState.putBoolean(STATE_RECEIPT_MODE, existingMode);
        outState.putString(STATE_EXISTING_BATCH_ID, selectedExistingBatchId);
    }
}
