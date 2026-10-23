package com.medicationinventory.ui.medicine;

import android.content.Intent;
import android.app.Activity;
import android.text.InputType;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.os.Bundle;

import android.view.View;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.medicationinventory.MedicationInventoryApplication;

import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import com.medicationinventory.service.BarcodeCodePolicy;
import com.medicationinventory.ui.scanner.BarcodeScannerActivity;
import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.domain.MedicineClassification;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.batch.BatchAdapter;
import com.medicationinventory.ui.issue.IssueStockActivity;
import com.medicationinventory.ui.receive.ReceiveStockActivity;
import com.medicationinventory.ui.adjustment.AdjustmentActivity;
import com.medicationinventory.ui.correction.CorrectionActivity;
import com.medicationinventory.ui.movement.MovementHistoryActivity;

import com.medicationinventory.service.ReorderRecommendationService;

import java.util.List;


/**
 * Implements the Medicine Details Activity behavior for its Android screen and the application state it presents.
 */
public class MedicineDetailsActivity
        extends AppCompatActivity {

    public static final String EXTRA_MEDICINE_ID =
            "medicine_id";


    private TextView medicineNameText;
    private TextView genericNameText;
    private TextView activeStatusText;
    private TextView medicineCodeText;
    private TextView strengthText;
    private TextView dosageFormText;
    private TextView categoryText;
    private TextView unitOfMeasureText;
    private TextView reorderLevelText;
    private TextView targetStockLevelText;
    private MaterialCardView abcVedClassificationCard;
    private TextView abcClassificationText;
    private TextView vedClassificationText;
    private TextView combinedClassificationText;
    private MedicineClassification currentMedicineClassification;
    private MaterialButton manageAbcVedButton;
    private ProgressBar abcVedActionProgress;
    private MaterialButton editMedicineButton;
    private MaterialButton deactivateMedicineButton;

    private MaterialButton receiveStockButton;
    private MaterialButton issueStockButton;
    private MaterialCardView reorderRecommendationCard;
    private TextView reorderStatusText;
    private TextView usableStockText;
    private TextView nearExpiryStockText;
    private TextView recommendationReorderLevelText;
    private TextView recommendationTargetStockText;
    private TextView recommendedQuantityText;
    private ReorderRecommendation currentReorderRecommendation;
    private ProgressBar medicineActionProgress;
    private MaterialCardView batchesCard;
    private TextView batchCountText;
    private RecyclerView batchRecyclerView;
    private View emptyBatchState;
    private BatchAdapter batchAdapter;
    private MedicineDetailsViewModel viewModel;
    private String medicineId;
    private UserProfile currentUserProfile;
    private Medicine currentMedicine;
    private MaterialButton movementHistoryButton;
    private MaterialButton adjustStockButton;
    private MaterialButton correctStockButton;

    private MedicineRepository barcodeMedicineRepository;
    private MaterialCardView medicineBarcodesCard;
    private LinearLayout medicineBarcodesList;
    private MaterialButton addMedicineBarcodeButton;
    private boolean barcodeWriteInProgress;
    private List<MedicineBarcodeEntity> displayedBarcodes = java.util.Collections.emptyList();

    private final ActivityResultLauncher<Intent> medicineBarcodeScannerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null)
                    return;
                String code = result.getData().getStringExtra(
                        BarcodeScannerActivity.EXTRA_SCANNED_VALUE);
                if (code != null) confirmBarcodeLink(code);
            });



    @Override

    protected void onCreate(
            Bundle savedInstanceState
    )
    {

        super.onCreate(
                savedInstanceState
        );


        medicineId =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_ID
                        );


        if (medicineId == null
                || medicineId
                .trim()
                .isEmpty()) {

            finish();

            return;
        }


        setContentView(
                R.layout.activity_medicine_details
        );


        bindViews();
        bindBarcodeViews();

        setupBatchList();

        setupViewModel(
                medicineId
        );

        setupActions();

        observeData();

        viewModel.refreshMedicineClassification();
    }


    private void bindViews()
    {

        medicineNameText =
                findViewById(
                        R.id.medicineNameText
                );

        genericNameText =
                findViewById(
                        R.id.genericNameText
                );

        activeStatusText =
                findViewById(
                        R.id.activeStatusText
                );

        medicineCodeText =
                findViewById(
                        R.id.medicineCodeText
                );

        categoryText =
                findViewById(
                        R.id.categoryText
                );

        strengthText =
                findViewById(
                        R.id.strengthText
                );

        dosageFormText =
                findViewById(
                        R.id.dosageFormText
                );

        unitOfMeasureText =
                findViewById(
                        R.id.unitOfMeasureText
                );

        reorderLevelText =
                findViewById(
                        R.id.reorderLevelText
                );

        targetStockLevelText =
                findViewById(
                        R.id.targetStockLevelText
                );

        editMedicineButton =
                findViewById(
                        R.id.editMedicineButton
                );

        deactivateMedicineButton =
                findViewById(
                        R.id.deactivateMedicineButton
                );

        medicineActionProgress =
                findViewById(
                        R.id.medicineActionProgress
                );

        batchesCard =
                findViewById(
                        R.id.batchesCard
                );

        batchCountText =
                findViewById(
                        R.id.batchCountText
                );

        batchRecyclerView =
                findViewById(
                        R.id.batchRecyclerView
                );

        emptyBatchState =
                findViewById(
                        R.id.emptyBatchState
                );

        receiveStockButton =
                findViewById(
                        R.id.receiveStockButton
                );

        issueStockButton =
                findViewById(
                        R.id.issueStockButton
                );

        movementHistoryButton =
                findViewById(
                        R.id.movementHistoryButton
                );

        adjustStockButton =
                findViewById(
                        R.id.adjustStockButton
                );

        correctStockButton =
                findViewById(
                        R.id.correctStockButton
                );

        reorderRecommendationCard =
                findViewById(
                        R.id.reorderRecommendationCard
                );


        reorderStatusText =
                findViewById(
                        R.id.reorderStatusText
                );


        usableStockText =
                findViewById(
                        R.id.usableStockText
                );


        nearExpiryStockText =
                findViewById(
                        R.id.nearExpiryStockText
                );


        recommendationReorderLevelText =
                findViewById(
                        R.id.recommendationReorderLevelText
                );


        recommendationTargetStockText =
                findViewById(
                        R.id.recommendationTargetStockText
                );


        recommendedQuantityText =
                findViewById(
                        R.id.recommendedQuantityText
                );

        abcVedClassificationCard =
                findViewById(
                        R.id.abcVedClassificationCard
                );

        abcClassificationText =
                findViewById(
                        R.id.abcClassificationText
                );

        vedClassificationText =
                findViewById(
                        R.id.vedClassificationText
                );

        combinedClassificationText =
                findViewById(
                        R.id.combinedClassificationText
                );

        manageAbcVedButton =
                findViewById(
                        R.id.manageAbcVedButton
                );


        abcVedActionProgress =
                findViewById(
                        R.id.abcVedActionProgress
                );
    }


    private void bindBarcodeViews() {
        medicineBarcodesCard = findViewById(R.id.medicineBarcodesCard);
        medicineBarcodesList = findViewById(R.id.medicineBarcodesList);
        addMedicineBarcodeButton = findViewById(R.id.addMedicineBarcodeButton);
    }


    private void setupViewModel(
            String medicineId
    )
    {

        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MedicineRepository medicineRepository =
                application
                        .getAppContainer()
                        .getMedicineRepository();
        barcodeMedicineRepository = medicineRepository;


        BatchRepository batchRepository =
                application
                        .getAppContainer()
                        .getBatchRepository();

        MedicineClassificationRepository
                medicineClassificationRepository =
                application
                        .getAppContainer()
                        .getMedicineClassificationRepository();

        ReorderRecommendationService
                reorderRecommendationService =
                application
                        .getAppContainer()
                        .getReorderRecommendationService();


        InventorySettingsRepository inventorySettingsRepository =
                application
                        .getAppContainer()
                        .getInventorySettingsRepository();


        MedicineDetailsViewModelFactory factory =
                new MedicineDetailsViewModelFactory(
                        medicineId,
                        authenticationRepository,
                        userRepository,
                        medicineRepository,
                        batchRepository,
                        medicineClassificationRepository,
                        reorderRecommendationService,
                        inventorySettingsRepository
                );



        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                MedicineDetailsViewModel.class
                        );

        movementHistoryButton.setOnClickListener(
                view ->
                {
                    Intent intent =
                            new Intent(
                                    this,
                                    MovementHistoryActivity.class
                            );


                    intent.putExtra(
                            MovementHistoryActivity.EXTRA_MEDICINE_ID,
                            medicineId
                    );


                    if (currentMedicine != null)
                    {
                        intent.putExtra(
                                MovementHistoryActivity.EXTRA_MEDICINE_NAME,
                                currentMedicine.getMedicineName()
                        );
                    }


                    startActivity(
                            intent
                    );
                }
        );
    }


    private boolean canEditBarcodeLinks() {
        return currentUserProfile != null && currentUserProfile.isActive()
                && currentUserProfile.can(Permission.VIEW_MEDICINES)
                && currentUserProfile.can(Permission.UPDATE_MEDICINE)
                && currentMedicine != null && currentMedicine.isActive();
    }

    private void showBarcodeEntryDialog() {
        if (!canEditBarcodeLinks() || barcodeWriteInProgress) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint("Scan or enter GTIN / QR identifier");
        LinearLayout container = new LinearLayout(this);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(this)
                .setTitle("Link a barcode")
                .setMessage("Identifiers are stored on this device only. "
                        + "One identifier can belong to only one medicine.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Scan", (dialog, which) -> {
                    if (canEditBarcodeLinks())
                        medicineBarcodeScannerLauncher.launch(
                                new Intent(this, BarcodeScannerActivity.class));
                })
                .setPositiveButton("Link", (dialog, which) ->
                        confirmBarcodeLink(input.getText().toString()))
                .show();
    }


    private void confirmBarcodeLink(String raw) {
        if (!canEditBarcodeLinks() || barcodeWriteInProgress) return;
        final String code;
        try {
            code = BarcodeCodePolicy.requireCode(raw);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Link identifier to medicine?")
                .setMessage(code + "\n\n" + currentMedicine.getMedicineName()
                        + "\n\nThis mapping is local to this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Link", (dialog, which) -> {
                    if (!canEditBarcodeLinks()) return;
                    barcodeWriteInProgress = true;
                    addMedicineBarcodeButton.setEnabled(false);
                    barcodeMedicineRepository.linkBarcode(medicineId, code,
                            new MedicineRepository.OperationCallback() {
                                @Override public void onSuccess() {
                                    barcodeWriteInProgress = false;
                                    updateBarcodeAccess();
                                    Toast.makeText(MedicineDetailsActivity.this,
                                            "Barcode linked to this medicine.",
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(Exception error) {
                                    barcodeWriteInProgress = false;
                                    updateBarcodeAccess();
                                    Toast.makeText(MedicineDetailsActivity.this,
                                            error.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                }).show();
    }


    private void confirmRemoveBarcode(String code) {
        if (!canEditBarcodeLinks() || barcodeWriteInProgress) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove linked barcode?")
                .setMessage(code + "\n\nThis will not delete or deactivate the medicine.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    if (!canEditBarcodeLinks()) return;
                    barcodeWriteInProgress = true;
                    updateBarcodeAccess();
                    barcodeMedicineRepository.unlinkBarcode(medicineId, code,
                            new MedicineRepository.OperationCallback() {
                                @Override public void onSuccess() {
                                    barcodeWriteInProgress = false;
                                    updateBarcodeAccess();
                                    Toast.makeText(MedicineDetailsActivity.this,
                                            "Barcode removed.", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(Exception error) {
                                    barcodeWriteInProgress = false;
                                    updateBarcodeAccess();
                                    Toast.makeText(MedicineDetailsActivity.this,
                                            error.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                }).show();
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }


    private void showLinkedBarcodes(List<MedicineBarcodeEntity> rows) {
        displayedBarcodes = rows == null ? java.util.Collections.emptyList() : rows;
        medicineBarcodesList.removeAllViews();
        if (displayedBarcodes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No packaging barcodes linked on this device.");
            empty.setTextColor(Color.parseColor("#738096"));
            medicineBarcodesList.addView(empty);
        } else {
            for (MedicineBarcodeEntity row : displayedBarcodes) {
                if (row == null) continue;
                LinearLayout line = new LinearLayout(this);
                line.setGravity(android.view.Gravity.CENTER_VERTICAL);
                TextView codeText = new TextView(this);
                codeText.setText(row.getBarcode());
                codeText.setTextColor(Color.parseColor("#172033"));
                codeText.setTextSize(14);
                codeText.setTextIsSelectable(true);
                line.addView(codeText, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton remove = new MaterialButton(this, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                remove.setText("Remove");
                remove.setTextSize(11);
                remove.setVisibility(canEditBarcodeLinks() ? View.VISIBLE : View.GONE);
                remove.setEnabled(!barcodeWriteInProgress);
                remove.setOnClickListener(v -> confirmRemoveBarcode(row.getBarcode()));
                line.addView(remove);
                medicineBarcodesList.addView(line);
            }
        }
        updateBarcodeAccess();
    }

    private void updateBarcodeAccess() {
        boolean mayView = currentUserProfile != null && currentUserProfile.isActive()
                && currentUserProfile.can(Permission.VIEW_MEDICINES);
        medicineBarcodesCard.setVisibility(mayView ? View.VISIBLE : View.GONE);
        addMedicineBarcodeButton.setVisibility(canEditBarcodeLinks() ? View.VISIBLE : View.GONE);
        addMedicineBarcodeButton.setEnabled(canEditBarcodeLinks() && !barcodeWriteInProgress);
        for (int i = 0; i < medicineBarcodesList.getChildCount(); i++) {
            View line = medicineBarcodesList.getChildAt(i);
            if (line instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) line;
                if (row.getChildCount() > 1 && row.getChildAt(1) instanceof MaterialButton) {
                    View button = row.getChildAt(1);
                    button.setVisibility(canEditBarcodeLinks() ? View.VISIBLE : View.GONE);
                    button.setEnabled(canEditBarcodeLinks() && !barcodeWriteInProgress);
                }
            }
        }
    }


    private void setupActions()
    {
        addMedicineBarcodeButton.setOnClickListener(v -> showBarcodeEntryDialog());

        ImageButton backButton =
                findViewById(
                        R.id.backButton
                );


        backButton.setOnClickListener(
                view -> finish()
        );


        editMedicineButton
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            this,
                                            AddMedicineActivity.class
                                    );


                            intent.putExtra(
                                    AddMedicineActivity.EXTRA_MEDICINE_ID,
                                    medicineId
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        deactivateMedicineButton
                .setOnClickListener(
                        view ->
                                showDeactivateConfirmation()
                );


        receiveStockButton
                .setOnClickListener(
                        view -> {

                            Intent intent =
                                    new Intent(
                                            this,
                                            ReceiveStockActivity.class
                                    );


                            intent.putExtra(
                                    ReceiveStockActivity.EXTRA_MEDICINE_ID,
                                    medicineId
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        issueStockButton
                .setOnClickListener(
                        view -> {

                            if (currentMedicine == null) {
                                return;
                            }


                            Intent intent =
                                    new Intent(
                                            this,
                                            IssueStockActivity.class
                                    );


                            intent.putExtra(
                                    IssueStockActivity.EXTRA_MEDICINE_ID,
                                    medicineId
                            );


                            intent.putExtra(
                                    IssueStockActivity.EXTRA_MEDICINE_NAME,
                                    currentMedicine.getMedicineName()
                            );


                            startActivity(
                                    intent
                            );
                        }
                );

        adjustStockButton
                .setOnClickListener(
                        view ->
                        {
                            if (currentMedicine == null)
                            {
                                return;
                            }


                            Intent intent =
                                    new Intent(
                                            this,
                                            AdjustmentActivity.class
                                    );


                            intent.putExtra(
                                    AdjustmentActivity.EXTRA_MEDICINE_ID,
                                    medicineId
                            );


                            intent.putExtra(
                                    AdjustmentActivity.EXTRA_MEDICINE_NAME,
                                    currentMedicine.getMedicineName()
                            );


                            startActivity(
                                    intent
                            );
                        }
                );

        correctStockButton
                .setOnClickListener(
                        view ->
                        {
                            if (currentMedicine == null)
                            {
                                return;
                            }


                            Intent intent =
                                    new Intent(
                                            this,
                                            CorrectionActivity.class
                                    );


                            intent.putExtra(
                                    CorrectionActivity.EXTRA_MEDICINE_ID,
                                    medicineId
                            );


                            intent.putExtra(
                                    CorrectionActivity.EXTRA_MEDICINE_NAME,
                                    currentMedicine.getMedicineName()
                            );


                            startActivity(
                                    intent
                            );
                        }
                );

        manageAbcVedButton
                .setOnClickListener(
                        view ->
                                showManageAbcVedDialog()
                );
    }



    private void setupBatchList()
    {

        batchAdapter =
                new BatchAdapter(
                        batch ->
                                Toast.makeText(
                                                this,
                                                "Batch "
                                                        + batch
                                                        .getBatchNumber(),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                );


        batchRecyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );


        batchRecyclerView.setAdapter(
                batchAdapter
        );
    }


    private void observeData()
    {
        barcodeMedicineRepository.observeBarcodesForMedicine(medicineId)
                .observe(this, this::showLinkedBarcodes);

        viewModel
                .getMedicine()
                .observe(
                        this,
                        this::showMedicine
                );


        viewModel
                .getUserProfile()
                .observe(
                        this,
                        this::applyPermissions
                );


        viewModel
                .getActionState()
                .observe(
                        this,
                        this::handleActionState
                );


        viewModel
                .getBatches()
                .observe(
                        this,
                        this::showBatches
                );

        viewModel
                .getReorderRecommendation()
                .observe(
                        this,
                        this::showReorderRecommendation
                );

        viewModel
                .getMedicineClassification()
                .observe(
                        this,
                        this::showMedicineClassification
                );

        viewModel
                .getClassificationActionState()
                .observe(
                        this,
                        this::handleClassificationActionState
                );
    }


    private void showMedicine(
            Medicine medicine
    )
    {

        currentMedicine =
                medicine;


        if (medicine == null) {
            return;
        }


        medicineNameText.setText(
                medicine.getMedicineName()
        );


        String genericName =
                medicine.getGenericName();


        genericNameText.setText(
                genericName == null
                        || genericName
                        .trim()
                        .isEmpty()
                        ? "No generic name recorded"
                        : genericName
        );


        activeStatusText.setText(
                medicine.isActive()
                        ? "Active"
                        : "Inactive"
        );


        medicineCodeText.setText(
                "Medicine Code: "
                        + medicine.getMedicineCode()
        );

        categoryText.setText(
                "Category: "
                        + optionalValue(
                        medicine.getCategory()
                )
        );

        strengthText.setText(
                "Strength: "
                        + optionalValue(
                        medicine.getStrength()
                )
        );


        dosageFormText.setText(
                "Dosage Form: "
                        + medicine.getDosageForm()
        );


        unitOfMeasureText.setText(
                "Unit of Measure: "
                        + medicine.getUnitOfMeasure()
        );


        reorderLevelText.setText(
                "Reorder Level: "
                        + medicine.getReorderLevel()
        );


        targetStockLevelText.setText(
                "Target Stock Level: "
                        + medicine.getTargetStockLevel()
        );


        applyPermissions(
                currentUserProfile
        );
    }

    private void showReorderRecommendation(
            ReorderRecommendation recommendation)
    {
        currentReorderRecommendation =
                recommendation;


        if (recommendation == null)
        {
            reorderRecommendationCard
                    .setVisibility(
                            View.GONE
                    );

            return;
        }


        usableStockText.setText(
                "Usable Stock: "
                        + recommendation
                        .getUsableStock()
        );


        nearExpiryStockText.setText(
                "Near Expiry Stock: "
                        + recommendation
                        .getNearExpiryStock()
        );


        recommendationReorderLevelText.setText(
                "Reorder Level: "
                        + recommendation
                        .getReorderLevel()
        );


        recommendationTargetStockText.setText(
                "Target Stock Level: "
                        + recommendation
                        .getTargetStockLevel()
        );


        if (recommendation.isReorderRequired())
        {
            reorderStatusText.setText(
                    "REORDER REQUIRED"
            );


            recommendedQuantityText.setText(
                    "Recommended Quantity: "
                            + recommendation
                            .getRecommendedQuantity()
            );
        }
        else
        {
            reorderStatusText.setText(
                    "Stock Level OK"
            );


            recommendedQuantityText.setText(
                    "Recommended Quantity: 0"
            );
        }


        updateReorderRecommendationVisibility();
    }

    private void updateReorderRecommendationVisibility()
    {
        boolean validUser =
                currentUserProfile != null
                        &&
                        currentUserProfile.isActive();


        boolean mayViewStock =
                validUser
                        &&
                        currentUserProfile.can(
                                Permission.VIEW_BATCHES
                        );


        boolean activeMedicine =
                currentMedicine != null
                        &&
                        currentMedicine.isActive();


        reorderRecommendationCard.setVisibility(
                currentReorderRecommendation != null
                        &&
                        mayViewStock
                        &&
                        activeMedicine
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void applyPermissions(
            UserProfile userProfile
    )
    {

        currentUserProfile =
                userProfile;


        boolean validUser =
                userProfile != null
                        && userProfile.isActive();


        if (!validUser)
        {

            editMedicineButton.setVisibility(
                    View.GONE
            );

            deactivateMedicineButton
                    .setVisibility(
                            View.GONE
                    );

            receiveStockButton.setVisibility(
                    View.GONE
            );

            issueStockButton.setVisibility(
                    View.GONE
            );

            batchesCard.setVisibility(
                    View.GONE
            );

            movementHistoryButton.setVisibility(
                    View.GONE
            );

            adjustStockButton.setVisibility(
                    View.GONE
            );

            correctStockButton.setVisibility(
                    View.GONE
            );

            reorderRecommendationCard.setVisibility(
                    View.GONE
            );

            abcVedClassificationCard.setVisibility(
                    View.GONE
            );

            updateBarcodeAccess();
            return;
        }


        editMedicineButton.setVisibility(
                userProfile.can(
                        Permission.UPDATE_MEDICINE
                )
                        ? View.VISIBLE
                        : View.GONE
        );


        deactivateMedicineButton.setVisibility(
                userProfile.can(
                        Permission.DEACTIVATE_MEDICINE
                )
                        ? View.VISIBLE
                        : View.GONE
        );


        boolean canViewBatches =
                userProfile.can(
                        Permission.VIEW_BATCHES
                );


        batchesCard.setVisibility(
                canViewBatches
                        ? View.VISIBLE
                        : View.GONE
        );


        boolean medicineIsActive =
                currentMedicine != null
                        && currentMedicine
                        .isActive();


        boolean canReceiveStock =
                userProfile.can(
                        Permission.RECEIVE_STOCK
                );


        boolean canIssueStock =
                userProfile.can(
                        Permission.CREATE_ISSUE
                );

        boolean canAdjustStock =
                userProfile.can(
                        Permission.CREATE_ADJUSTMENT
                );

        boolean canCorrectStock =
                userProfile.can(
                        Permission.CREATE_CORRECTION
                );

        boolean canViewHistory =
                userProfile.can(
                        Permission.VIEW_INVENTORY_HISTORY
                );

        movementHistoryButton.setVisibility(
                canViewHistory
                        ? View.VISIBLE
                        : View.GONE
        );

        receiveStockButton.setVisibility(
                canViewBatches
                        && canReceiveStock
                        && medicineIsActive
                        ? View.VISIBLE
                        : View.GONE
        );


        issueStockButton.setVisibility(
                canViewBatches
                        && canIssueStock
                        && medicineIsActive
                        ? View.VISIBLE
                        : View.GONE
        );

        adjustStockButton.setVisibility(
                canViewBatches
                        &&
                        canAdjustStock
                        &&
                        medicineIsActive
                        ? View.VISIBLE
                        : View.GONE
        );

        correctStockButton.setVisibility(
                canViewBatches
                        &&
                        canCorrectStock
                        &&
                        medicineIsActive
                        ? View.VISIBLE
                        : View.GONE
        );

        manageAbcVedButton.setVisibility(
                View.GONE
        );


        abcVedActionProgress.setVisibility(
                View.GONE
        );

        updateAbcVedVisibility();
        updateReorderRecommendationVisibility();
        updateBarcodeAccess();
    }


    private String optionalValue(
            String value
    ) {

        if (value == null
                || value
                .trim()
                .isEmpty()) {

            return "Not specified";
        }


        return value;
    }

    private void showManageAbcVedDialog()
    {
        if (currentUserProfile == null
                ||
                !currentUserProfile.isActive()
                ||
                !currentUserProfile.can(
                        Permission.VIEW_ABC_VED
                )
                ||
                !currentUserProfile.can(
                        Permission.MANAGE_ABC_VED
                ))
        {
            Toast.makeText(
                            this,
                            "You do not have permission to manage ABC-VED classifications.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        if (currentMedicine == null
                ||
                !currentMedicine.isActive())
        {
            Toast.makeText(
                            this,
                            "ABC-VED classification cannot be changed for an inactive medicine.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        View dialogView =
                getLayoutInflater()
                        .inflate(
                                R.layout.dialog_manage_abc_ved,
                                null
                        );


        RadioGroup abcRadioGroup =
                dialogView.findViewById(
                        R.id.abcRadioGroup
                );


        RadioGroup vedRadioGroup =
                dialogView.findViewById(
                        R.id.vedRadioGroup
                );


        /*
         * Pre-select the existing authoritative value
         * when editing an existing classification.
         */
        if (currentMedicineClassification != null)
        {
            String abc =
                    currentMedicineClassification
                            .getAbcClassification();


            if ("A".equals(abc))
            {
                abcRadioGroup.check(
                        R.id.abcARadio
                );
            }
            else if ("B".equals(abc))
            {
                abcRadioGroup.check(
                        R.id.abcBRadio
                );
            }
            else if ("C".equals(abc))
            {
                abcRadioGroup.check(
                        R.id.abcCRadio
                );
            }


            String ved =
                    currentMedicineClassification
                            .getVedClassification();


            if ("V".equals(ved))
            {
                vedRadioGroup.check(
                        R.id.vedVRadio
                );
            }
            else if ("E".equals(ved))
            {
                vedRadioGroup.check(
                        R.id.vedERadio
                );
            }
            else if ("D".equals(ved))
            {
                vedRadioGroup.check(
                        R.id.vedDRadio
                );
            }
        }


        String title =
                currentMedicineClassification == null
                        ? "Assign ABC-VED Classification"
                        : "Update ABC-VED Classification";


        AlertDialog dialog =
                new MaterialAlertDialogBuilder(
                        this
                )
                        .setTitle(
                                title
                        )
                        .setView(
                                dialogView
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();


        dialog.setOnShowListener(
                ignored ->
                        dialog
                                .getButton(
                                        AlertDialog.BUTTON_POSITIVE
                                )
                                .setOnClickListener(
                                        view ->
                                        {
                                            int abcId =
                                                    abcRadioGroup
                                                            .getCheckedRadioButtonId();


                                            int vedId =
                                                    vedRadioGroup
                                                            .getCheckedRadioButtonId();


                                            if (abcId == -1)
                                            {
                                                Toast.makeText(
                                                                this,
                                                                "Select an ABC classification.",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show();

                                                return;
                                            }


                                            if (vedId == -1)
                                            {
                                                Toast.makeText(
                                                                this,
                                                                "Select a VED classification.",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show();

                                                return;
                                            }


                                            // Persist the classification codes,
                                            // not the readable radio button labels.
                                            String abc =
                                                    abcId == R.id.abcARadio ? "A" :
                                                            abcId == R.id.abcBRadio ? "B" :
                                                            abcId == R.id.abcCRadio ? "C" : null;

                                            String ved =
                                                    vedId == R.id.vedVRadio ? "V" :
                                                            vedId == R.id.vedERadio ? "E" :
                                                            vedId == R.id.vedDRadio ? "D" : null;

                                            if (abc == null || ved == null)
                                            {
                                                Toast.makeText(
                                                                this,
                                                                "Select valid ABC and VED classifications.",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show();
                                                return;
                                            }


                                            viewModel
                                                    .saveMedicineClassification(
                                                            abc,
                                                            ved
                                                    );


                                            dialog.dismiss();
                                        }
                                )
        );


        dialog.show();
    }

    private void showDeactivateConfirmation()
    {

        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Deactivate Medicine?"
                )
                .setMessage(
                        "This medicine will be removed from the active inventory list. "
                                + "Its historical record will be preserved."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Deactivate",
                        (dialog, which) ->
                                viewModel
                                        .deactivateMedicine(
                                                medicineId
                                        )
                )
                .show();
    }

    private void handleClassificationActionState(
            MedicineDetailsViewModel.ClassificationActionState state)
    {
        if (state == null)
        {
            return;
        }


        switch (state.getStatus())
        {
            case IDLE:

                abcVedActionProgress.setVisibility(
                        View.GONE
                );


                manageAbcVedButton.setEnabled(
                        true
                );


                updateAbcVedVisibility();

                break;


            case LOADING:

                abcVedActionProgress.setVisibility(
                        View.VISIBLE
                );


                manageAbcVedButton.setEnabled(
                        false
                );

                break;


            case SUCCESS:

                abcVedActionProgress.setVisibility(
                        View.GONE
                );


                manageAbcVedButton.setEnabled(
                        true
                );


                updateAbcVedVisibility();


                String successMessage;


                if (!state.isChanged())
                {
                    successMessage =
                            "ABC-VED classification is already up to date.";
                }
                else if (state.isCreated())
                {
                    successMessage =
                            "ABC-VED classification assigned.";
                }
                else
                {
                    successMessage =
                            "ABC-VED classification updated.";
                }


                Toast.makeText(
                                this,
                                successMessage,
                                Toast.LENGTH_SHORT
                        )
                        .show();


                viewModel
                        .clearClassificationActionState();

                break;


            case COMMITTED_REFRESH_ERROR:

                abcVedActionProgress.setVisibility(
                        View.GONE
                );


                manageAbcVedButton.setEnabled(
                        true
                );


                updateAbcVedVisibility();


                Toast.makeText(
                                this,
                                state.getMessage() == null
                                        ? "ABC-VED classification was saved, "
                                          + "but the latest server copy could not be refreshed."
                                        : state.getMessage(),
                                Toast.LENGTH_LONG
                        )
                        .show();


                viewModel
                        .clearClassificationActionState();

                break;


            case ERROR:

                abcVedActionProgress.setVisibility(
                        View.GONE
                );


                manageAbcVedButton.setEnabled(
                        true
                );


                updateAbcVedVisibility();


                Toast.makeText(
                                this,
                                state.getMessage() == null
                                        ? "Unable to save the ABC-VED classification."
                                        : state.getMessage(),
                                Toast.LENGTH_LONG
                        )
                        .show();


                viewModel
                        .clearClassificationActionState();

                break;
        }
    }

    private void showMedicineClassification(
            MedicineClassification classification)
    {
        currentMedicineClassification =
                classification;


        if (classification == null)
        {
            abcClassificationText.setText(
                    "ABC Class: Not assigned"
            );


            vedClassificationText.setText(
                    "VED Class: Not assigned"
            );


            combinedClassificationText.setText(
                    "No ABC-VED classification is currently available."
            );


            updateAbcVedVisibility();

            return;
        }


        abcClassificationText.setText(
                "ABC Class: "
                        + classification
                        .getAbcClassification()
        );


        vedClassificationText.setText(
                "VED Class: "
                        + classification
                        .getVedClassification()
        );


        combinedClassificationText.setText(
                "Combined Classification: "
                        + classification
                        .getDisplayClassification()
        );


        updateAbcVedVisibility();
    }

    private void updateAbcVedVisibility()
    {
        boolean validUser =
                currentUserProfile != null
                        &&
                        currentUserProfile.isActive();


        boolean canView =
                validUser
                        &&
                        currentUserProfile.can(
                                Permission.VIEW_ABC_VED
                        );


        boolean canManage =
                validUser
                        &&
                        currentUserProfile.can(
                                Permission.MANAGE_ABC_VED
                        );


        boolean activeMedicine =
                currentMedicine != null
                        &&
                        currentMedicine.isActive();


        boolean showCard =
                canView
                        &&
                        (
                                currentMedicineClassification != null
                                        ||
                                        (
                                                canManage
                                                        &&
                                                        activeMedicine
                                        )
                        );


        abcVedClassificationCard.setVisibility(
                showCard
                        ? View.VISIBLE
                        : View.GONE
        );


        manageAbcVedButton.setVisibility(
                canView
                        &&
                        canManage
                        &&
                        activeMedicine
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    private void showBatches(
            List<Batch> batches
    ) {

        batchAdapter.setBatches(
                batches
        );


        int count =
                batches == null
                        ? 0
                        : batches.size();


        batchCountText.setText(
                count == 1
                        ? "1 batch"
                        : count + " batches"
        );


        boolean empty =
                count == 0;


        emptyBatchState.setVisibility(
                empty
                        ? View.VISIBLE
                        : View.GONE
        );


        batchRecyclerView.setVisibility(
                empty
                        ? View.GONE
                        : View.VISIBLE
        );
    }


    private void handleActionState(
            MedicineDetailsViewModel.ActionState state
    ) {

        if (state == null) {
            return;
        }


        switch (state.getStatus()) {

            case IDLE:

                medicineActionProgress
                        .setVisibility(
                                View.GONE
                        );

                break;


            case LOADING:

                medicineActionProgress
                        .setVisibility(
                                View.VISIBLE
                        );


                editMedicineButton.setEnabled(
                        false
                );


                deactivateMedicineButton.setEnabled(
                        false
                );

                break;


            case SUCCESS:

                medicineActionProgress
                        .setVisibility(
                                View.GONE
                        );


                Toast.makeText(
                                this,
                                "Medicine deactivated.",
                                Toast.LENGTH_SHORT
                        )
                        .show();


                finish();

                break;


            case ERROR:

                medicineActionProgress
                        .setVisibility(
                                View.GONE
                        );


                editMedicineButton.setEnabled(
                        true
                );


                deactivateMedicineButton.setEnabled(
                        true
                );


                applyPermissions(
                        currentUserProfile
                );


                Toast.makeText(
                                this,
                                state.getMessage() == null
                                        ? "Unable to deactivate medicine."
                                        : state.getMessage(),
                                Toast.LENGTH_LONG
                        )
                        .show();

                break;
        }
    }
}
