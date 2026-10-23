package com.medicationinventory.ui.issue;

import android.app.Activity;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;
import com.medicationinventory.data.repository.IssueStockRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.ui.scanner.BarcodeScannerActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.format.DateTimeFormatter;
import com.medicationinventory.model.domain.IssueFefoDraft;
import com.medicationinventory.service.FefoAllocationService;



/**
 * Implements the Android screen for Issue Stock and connects user actions to application state.
 */
public class IssueStockActivity
        extends AppCompatActivity {

    public static final String EXTRA_MEDICINE_ID =
            "medicine_id";

    public static final String EXTRA_MEDICINE_NAME =
            "medicine_name";


    private String medicineId;
    private String medicineName;


    private TextView medicineNameText;

    private TextInputLayout
            quantityInputLayout;

    private TextInputEditText
            quantityInput;

    private TextInputEditText
            reasonInput;

    private TextView
            insufficientStockText;

    private ProgressBar
            issueProgress;

    private MaterialButton
            reviewAllocationButton;


    private IssueStockViewModel viewModel;
    private MedicineRepository medicineRepository;
    private MaterialButton scanIssueBarcodeButton;
    private TextView issueScanStatusText;

    private final ActivityResultLauncher<Intent> issueScanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                verifyIssueScan(result.getData().getStringExtra(
                        BarcodeScannerActivity.EXTRA_SCANNED_VALUE));
            });


    @Override

    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        medicineId =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_ID
                        );


        medicineName =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_NAME
                        );


        if (medicineId == null
                || medicineId
                .trim()
                .isEmpty()) {

            finish();

            return;
        }


        setContentView(
                R.layout.activity_issue_stock
        );


        bindViews();

        setupViewModel();

        setupActions();

        observeState();


        medicineNameText.setText(
                medicineName == null
                        || medicineName
                        .trim()
                        .isEmpty()
                        ? "Selected medicine"
                        : medicineName
        );
    }



    private void bindViews() {

        medicineNameText =
                findViewById(
                        R.id.issueMedicineNameText
                );


        quantityInputLayout =
                findViewById(
                        R.id.issueQuantityInputLayout
                );


        quantityInput =
                findViewById(
                        R.id.issueQuantityInput
                );


        reasonInput =
                findViewById(
                        R.id.issueReasonInput
                );


        insufficientStockText =
                findViewById(
                        R.id.insufficientStockText
                );


        issueProgress =
                findViewById(
                        R.id.issueProgress
                );


        reviewAllocationButton =
                findViewById(R.id.reviewAllocationButton);
        scanIssueBarcodeButton = findViewById(R.id.scanIssueBarcodeButton);
        issueScanStatusText = findViewById(R.id.issueScanStatusText);
    }



    private void setupViewModel() {

        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        medicineRepository = application.getAppContainer().getMedicineRepository();
        IssueStockRepository repository =
                application
                        .getAppContainer()
                        .getIssueStockRepository();


        IssueStockViewModelFactory factory =
                new IssueStockViewModelFactory(
                        repository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                IssueStockViewModel.class
                        );
    }



    private void setupActions() {

        ImageButton backButton =
                findViewById(
                        R.id.issueBackButton
                );


        backButton.setOnClickListener(
                view -> finish()
        );


        scanIssueBarcodeButton.setOnClickListener(view ->
                issueScanLauncher.launch(new Intent(this, BarcodeScannerActivity.class)));

        reviewAllocationButton
                .setOnClickListener(
                        view ->
                                calculateAllocation()
                );
    }



    private void observeState() {

        viewModel
                .getState()
                .observe(
                        this,
                        this::renderState
                );
    }


    private void verifyIssueScan(String scanned) {
        scanIssueBarcodeButton.setEnabled(false);
        issueScanStatusText.setVisibility(View.VISIBLE);
        issueScanStatusText.setText("Verifying barcode…");
        medicineRepository.verifyScanForMedicine(medicineId, scanned,
                new MedicineRepository.ScanVerificationCallback() {
                    @Override

                    public void onResult(MedicineRepository.ScanVerificationResult result) {
                        scanIssueBarcodeButton.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        if (!result.isMatch()) {
                            issueScanStatusText.setText("Scan not verified. Selected medicine unchanged.");
                            String description = result.getStatus()
                                    == MedicineRepository.ScanVerificationResult.Status.DIFFERENT_MEDICINE
                                    ? "This barcode identifies " + result.getMatchedMedicineName()
                                      + ", not the selected medicine."
                                    : result.getStatus() == MedicineRepository.ScanVerificationResult.Status.INACTIVE
                                      ? "The linked medicine is inactive."
                                      : "The scanned code is not linked on this device.";
                            new MaterialAlertDialogBuilder(IssueStockActivity.this)
                                    .setTitle("Medicine mismatch")
                                    .setMessage(description + " No stock has been changed.")
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        String extra = "";
                        if (result.getBatchNumber() != null)
                            extra += "\nBatch printed on package: " + result.getBatchNumber();
                        if (result.getExpiryDate() != null)
                            extra += "\nPackage expiry: " + result.getExpiryDate()
                                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
                        issueScanStatusText.setText("Medicine verified: " + result.getIdentifier()
                                + "\nFEFO will still select eligible batches.");
                        new MaterialAlertDialogBuilder(IssueStockActivity.this)
                                .setTitle("Medicine verified")
                                .setMessage("The scanned identifier matches the selected medicine."
                                        + extra + "\n\nPackage batch information is for reference only. "
                                        + "The FEFO calculation remains authoritative; the scan "
                                        + "does not override batch selection or deduct stock.")
                                .setPositiveButton("Continue", null).show();
                    }
                    @Override
                    public void onError(Exception error) {
                        scanIssueBarcodeButton.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        issueScanStatusText.setText("Scan could not be verified.");
                        new MaterialAlertDialogBuilder(IssueStockActivity.this)
                                .setTitle("Unable to verify scan")
                                .setMessage(error.getMessage() == null
                                        ? "Try another code or continue without scanning."
                                        : error.getMessage())
                                .setPositiveButton("OK", null).show();
                    }
                });
    }


    private void calculateAllocation() {

        quantityInputLayout.setError(
                null
        );


        insufficientStockText
                .setVisibility(
                        View.GONE
                );


        String quantityText =
                quantityInput
                        .getText() == null
                        ? ""
                        : quantityInput
                        .getText()
                        .toString()
                        .trim();


        if (quantityText.isEmpty()) {

            quantityInputLayout.setError(
                    "Enter the quantity to issue."
            );

            return;
        }


        int requestedQuantity;


        try {

            requestedQuantity =
                    Integer.parseInt(
                            quantityText
                    );

        } catch (NumberFormatException exception) {

            quantityInputLayout.setError(
                    "Enter a valid whole number."
            );

            return;
        }


        if (requestedQuantity <= 0) {

            quantityInputLayout.setError(
                    "Quantity must be greater than zero."
            );

            return;
        }


        viewModel.calculateFefo(
                medicineId,
                requestedQuantity
        );
    }



    private void renderState(
            IssueStockViewModel.State state
    ) {

        if (state == null) {
            return;
        }


        switch (state.getStatus()) {

            case IDLE:

                issueProgress.setVisibility(
                        View.GONE
                );

                reviewAllocationButton
                        .setEnabled(
                                true
                        );

                break;


            case LOADING:

                issueProgress.setVisibility(
                        View.VISIBLE
                );

                reviewAllocationButton
                        .setEnabled(
                                false
                        );

                insufficientStockText
                        .setVisibility(
                                View.GONE
                        );

                break;


            case SUCCESS:

                issueProgress.setVisibility(
                        View.GONE
                );

                reviewAllocationButton
                        .setEnabled(
                                true
                        );


                openFefoReview(
                        state.getResult()
                );

                break;


            case INSUFFICIENT:

                issueProgress.setVisibility(
                        View.GONE
                );

                reviewAllocationButton
                        .setEnabled(
                                true
                        );


                showInsufficientStock(
                        state.getResult()
                );

                break;


            case ERROR:

                issueProgress.setVisibility(
                        View.GONE
                );

                reviewAllocationButton
                        .setEnabled(
                                true
                        );


                Toast.makeText(
                                this,
                                state.getMessage() == null
                                        ? "Unable to calculate allocation."
                                        : state.getMessage(),
                                Toast.LENGTH_LONG
                        )
                        .show();

                break;
        }
    }



    private void showInsufficientStock(
            FefoAllocationService.Result result
    ) {

        if (result == null) {
            return;
        }


        String message =
                "Insufficient stock.\n\n"
                        + "Requested: "
                        + result.getRequestedQuantity()
                        + "\n"
                        + "Eligible stock: "
                        + result.getTotalEligibleStock()
                        + "\n\n"
                        + "Partial fulfilment is not permitted.";


        insufficientStockText.setText(
                message
        );


        insufficientStockText
                .setVisibility(
                        View.VISIBLE
                );
    }


    private void openFefoReview(
            FefoAllocationService.Result result
    ) {

        if (result == null) {
            return;
        }


        String reason =
                reasonInput.getText() == null
                        ? null
                        : reasonInput
                        .getText()
                        .toString()
                        .trim();


        if (reason != null
                && reason.isEmpty()) {

            reason = null;
        }


        IssueFefoDraft draft =
                IssueFefoDraft.from(
                        medicineId,
                        medicineName,
                        reason,
                        result
                );



        viewModel.reset();


        Intent intent =
                new Intent(
                        this,
                        FefoReviewActivity.class
                );


        intent.putExtra(
                FefoReviewActivity.EXTRA_DRAFT,
                draft
        );


        startActivity(
                intent
        );
    }
}
