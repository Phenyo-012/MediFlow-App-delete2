package com.medicationinventory.ui.issue;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.model.domain.IssueFefoDraft;

import com.medicationinventory.service.IssueService;

import com.medicationinventory.ui.medicine.MedicineDetailsActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;



/**
 * Implements the Android screen for Fefo Review and connects user actions to application state.
 */
public class FefoReviewActivity
        extends AppCompatActivity
{
    public static final String EXTRA_DRAFT =
            "issue_fefo_draft";


    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy"
            );


    private final ZoneId businessZone =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final Map<String, TextInputEditText>
            overrideQuantityInputs =
            new LinkedHashMap<>();


    private IssueFefoDraft draft;

    private FefoReviewViewModel viewModel;


    private TextView medicineNameText;
    private TextView requestedQuantityText;
    private TextView eligibleStockText;
    private TextView reasonText;

    private LinearLayout allocationContainer;

    private MaterialButton confirmIssueButton;

    private ProgressBar confirmIssueProgress;


    private MaterialButton overrideFefoButton;

    private View overrideSection;

    private LinearLayout overrideAllocationContainer;

    private TextInputEditText overrideReasonInput;

    private MaterialButton confirmOverrideButton;

    private MaterialButton cancelOverrideButton;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        draft =
                readDraft();


        if (draft == null)
        {
            finish();

            return;
        }


        setContentView(
                R.layout.activity_fefo_review
        );


        bindViews();

        setupViewModel();

        setupActions();

        observeState();

        renderDraft();

        renderOverrideBatches();



        viewModel.loadOverridePermission();
    }



    private void bindViews()
    {
        medicineNameText =
                findViewById(
                        R.id.fefoMedicineNameText
                );


        requestedQuantityText =
                findViewById(
                        R.id.fefoRequestedQuantityText
                );


        eligibleStockText =
                findViewById(
                        R.id.fefoEligibleStockText
                );


        reasonText =
                findViewById(
                        R.id.fefoReasonText
                );


        allocationContainer =
                findViewById(
                        R.id.fefoAllocationContainer
                );


        confirmIssueButton =
                findViewById(
                        R.id.confirmIssueButton
                );


        confirmIssueProgress =
                findViewById(
                        R.id.confirmIssueProgress
                );


        overrideFefoButton =
                findViewById(
                        R.id.overrideFefoButton
                );


        overrideSection =
                findViewById(
                        R.id.overrideSection
                );


        overrideAllocationContainer =
                findViewById(
                        R.id.overrideAllocationContainer
                );


        overrideReasonInput =
                findViewById(
                        R.id.overrideReasonInput
                );


        confirmOverrideButton =
                findViewById(
                        R.id.confirmOverrideButton
                );


        cancelOverrideButton =
                findViewById(
                        R.id.cancelOverrideButton
                );
    }



    private void setupViewModel()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        IssueService issueService =
                application
                        .getAppContainer()
                        .getIssueService();


        FefoReviewViewModelFactory factory =
                new FefoReviewViewModelFactory(
                        issueService
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                FefoReviewViewModel.class
                        );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.fefoBackButton
                );


        backButton.setOnClickListener(
                view -> finish()
        );


        confirmIssueButton.setOnClickListener(
                view ->
                        viewModel.confirmIssue(
                                draft
                        )
        );


        overrideFefoButton.setOnClickListener(
                view ->
                        showOverrideMode()
        );


        cancelOverrideButton.setOnClickListener(
                view ->
                        hideOverrideMode()
        );


        confirmOverrideButton.setOnClickListener(
                view ->
                        submitOverride()
        );
    }



    private void observeState()
    {
        viewModel
                .getState()
                .observe(
                        this,
                        this::renderState
                );


        viewModel
                .getCanOverrideFefo()
                .observe(
                        this,
                        allowed ->
                        {
                            boolean canOverride =
                                    Boolean.TRUE.equals(
                                            allowed
                                    );


                            overrideFefoButton.setVisibility(
                                    canOverride
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            if (!canOverride)
                            {
                                hideOverrideMode();
                            }
                        }
                );
    }



    private void renderState(
            FefoReviewViewModel.State state)
    {
        if (state == null)
        {
            return;
        }


        switch (state.getStatus())
        {
            case IDLE:

                confirmIssueProgress.setVisibility(
                        View.GONE
                );


                confirmIssueButton.setEnabled(
                        true
                );


                confirmIssueButton.setText(
                        "Confirm Issue"
                );


                setOverrideControlsEnabled(
                        true
                );

                break;


            case LOADING:

                confirmIssueButton.setEnabled(
                        false
                );


                confirmIssueButton.setText(
                        "Recording Issue..."
                );


                confirmIssueProgress.setVisibility(
                        View.VISIBLE
                );


                setOverrideControlsEnabled(
                        false
                );

                break;


            case SUCCESS:

                confirmIssueProgress.setVisibility(
                        View.GONE
                );


                confirmIssueButton.setEnabled(
                        false
                );


                setOverrideControlsEnabled(
                        false
                );


                viewModel.markSuccessHandled();


                Toast.makeText(
                                this,
                                "Stock issue recorded locally. Pending synchronisation.",
                                Toast.LENGTH_LONG
                        )
                        .show();


                returnToMedicineDetails();

                break;


            case ERROR:

                confirmIssueProgress.setVisibility(
                        View.GONE
                );


                confirmIssueButton.setEnabled(
                        true
                );


                confirmIssueButton.setText(
                        "Confirm Issue"
                );


                setOverrideControlsEnabled(
                        true
                );


                Toast.makeText(
                                this,
                                state.getMessage() == null
                                        ? "Unable to record stock issue."
                                        : state.getMessage(),
                                Toast.LENGTH_LONG
                        )
                        .show();

                break;
        }
    }



    private void showOverrideMode()
    {
        if (draft.getEligibleBatches() == null
                ||
                draft.getEligibleBatches()
                        .isEmpty())
        {
            Toast.makeText(
                            this,
                            "Eligible batch information is unavailable. Recalculate the issue.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        overrideSection.setVisibility(
                View.VISIBLE
        );


        confirmIssueButton.setVisibility(
                View.GONE
        );


        overrideFefoButton.setVisibility(
                View.GONE
        );
    }



    private void hideOverrideMode()
    {
        overrideSection.setVisibility(
                View.GONE
        );


        confirmIssueButton.setVisibility(
                View.VISIBLE
        );


        Boolean allowed =
                viewModel
                        .getCanOverrideFefo()
                        .getValue();


        overrideFefoButton.setVisibility(
                Boolean.TRUE.equals(
                        allowed
                )
                        ? View.VISIBLE
                        : View.GONE
        );
    }



    private void submitOverride()
    {
        String overrideReason =
                overrideReasonInput
                        .getText() == null
                        ? ""
                        : overrideReasonInput
                        .getText()
                        .toString()
                        .trim();


        if (overrideReason.isEmpty())
        {
            overrideReasonInput.setError(
                    "Override reason is required."
            );

            overrideReasonInput.requestFocus();

            return;
        }


        ArrayList<IssueFefoDraft.Allocation>
                overrideAllocations =
                new ArrayList<>();


        long total =
                0;


        for (IssueFefoDraft.EligibleBatch batch :
                draft.getEligibleBatches())
        {
            TextInputEditText input =
                    overrideQuantityInputs.get(
                            batch.getBatchId()
                    );


            if (input == null)
            {
                Toast.makeText(
                                this,
                                "Override allocation controls are incomplete.",
                                Toast.LENGTH_LONG
                        )
                        .show();

                return;
            }


            String quantityText =
                    input.getText() == null
                            ? ""
                            : input.getText()
                            .toString()
                            .trim();


            int quantity =
                    0;


            if (!quantityText.isEmpty())
            {
                try
                {
                    quantity =
                            Integer.parseInt(
                                    quantityText
                            );
                }
                catch (NumberFormatException exception)
                {
                    input.setError(
                            "Enter a valid whole number."
                    );

                    input.requestFocus();

                    return;
                }
            }


            if (quantity < 0)
            {
                input.setError(
                        "Quantity cannot be negative."
                );

                input.requestFocus();

                return;
            }


            if (quantity >
                    batch.getAvailableQuantity())
            {
                input.setError(
                        "Maximum available: "
                                + batch.getAvailableQuantity()
                );

                input.requestFocus();

                return;
            }


            if (quantity == 0)
            {
                continue;
            }


            total +=
                    quantity;


            overrideAllocations.add(
                    new IssueFefoDraft.Allocation(
                            batch.getBatchId(),
                            batch.getBatchNumber(),
                            batch.getExpiryDate(),
                            batch.getReceivedDate(),
                            batch.getAvailableQuantity(),
                            quantity
                    )
            );
        }


        if (total !=
                draft.getRequestedQuantity())
        {
            Toast.makeText(
                            this,
                            "Override allocations must total exactly "
                                    + draft.getRequestedQuantity()
                                    + ". Current total: "
                                    + total,
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        viewModel.confirmOverrideIssue(
                draft,
                overrideAllocations,
                overrideReason
        );
    }


    private void setOverrideControlsEnabled(
            boolean enabled)
    {
        overrideFefoButton.setEnabled(
                enabled
        );


        confirmOverrideButton.setEnabled(
                enabled
        );


        cancelOverrideButton.setEnabled(
                enabled
        );


        overrideReasonInput.setEnabled(
                enabled
        );


        for (TextInputEditText input :
                overrideQuantityInputs.values())
        {
            input.setEnabled(
                    enabled
            );
        }
    }



    private void returnToMedicineDetails()
    {
        Intent intent =
                new Intent(
                        this,
                        MedicineDetailsActivity.class
                );


        intent.putExtra(
                MedicineDetailsActivity.EXTRA_MEDICINE_ID,
                draft.getMedicineId()
        );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        startActivity(
                intent
        );


        finish();
    }



    private void renderDraft()
    {
        medicineNameText.setText(
                draft.getMedicineName() == null
                        ? "Selected medicine"
                        : draft.getMedicineName()
        );


        requestedQuantityText.setText(
                "Requested quantity: "
                        + draft.getRequestedQuantity()
        );


        eligibleStockText.setText(
                "Eligible stock before issue: "
                        + draft.getTotalEligibleStock()
        );


        String reason =
                draft.getReason();


        if (reason == null
                || reason.trim().isEmpty())
        {
            reasonText.setText(
                    "Reason / destination: Not specified"
            );
        }
        else
        {
            reasonText.setText(
                    "Reason / destination: "
                            + reason
            );
        }


        allocationContainer.removeAllViews();


        int allocationNumber =
                1;


        for (
                IssueFefoDraft.Allocation allocation :
                draft.getAllocations())
        {
            View row =
                    getLayoutInflater()
                            .inflate(
                                    R.layout.item_fefo_allocation,
                                    allocationContainer,
                                    false
                            );


            TextView sequenceText =
                    row.findViewById(
                            R.id.fefoSequenceText
                    );


            TextView batchText =
                    row.findViewById(
                            R.id.fefoBatchNumberText
                    );


            TextView expiryText =
                    row.findViewById(
                            R.id.fefoExpiryDateText
                    );


            TextView availableText =
                    row.findViewById(
                            R.id.fefoAvailableText
                    );


            TextView allocatedText =
                    row.findViewById(
                            R.id.fefoAllocatedText
                    );


            sequenceText.setText(
                    "FEFO "
                            + allocationNumber
            );


            batchText.setText(
                    allocation.getBatchNumber()
            );


            expiryText.setText(
                    "Expiry: "
                            + formatDate(
                            allocation.getExpiryDate()
                    )
            );


            availableText.setText(
                    "Available before issue: "
                            + allocation
                            .getAvailableBeforeIssue()
            );


            allocatedText.setText(
                    "Allocate: "
                            + allocation
                            .getAllocatedQuantity()
            );


            allocationContainer.addView(
                    row
            );


            allocationNumber++;
        }
    }


    private void renderOverrideBatches()
    {
        overrideAllocationContainer.removeAllViews();

        overrideQuantityInputs.clear();


        for (IssueFefoDraft.EligibleBatch batch :
                draft.getEligibleBatches())
        {
            View row =
                    getLayoutInflater()
                            .inflate(
                                    R.layout.item_fefo_override_batch,
                                    overrideAllocationContainer,
                                    false
                            );


            TextView batchNumberText =
                    row.findViewById(
                            R.id.overrideBatchNumberText
                    );


            TextView expiryDateText =
                    row.findViewById(
                            R.id.overrideExpiryDateText
                    );


            TextView availableText =
                    row.findViewById(
                            R.id.overrideAvailableText
                    );


            TextView recommendedText =
                    row.findViewById(
                            R.id.overrideRecommendedText
                    );


            TextInputEditText quantityInput =
                    row.findViewById(
                            R.id.overrideQuantityInput
                    );


            int recommendedQuantity =
                    getRecommendedQuantity(
                            batch.getBatchId()
                    );


            batchNumberText.setText(
                    batch.getBatchNumber()
            );


            expiryDateText.setText(
                    "Expiry: "
                            + formatDate(
                            batch.getExpiryDate()
                    )
            );


            availableText.setText(
                    "Available: "
                            + batch.getAvailableQuantity()
            );


            recommendedText.setText(
                    "FEFO recommendation: "
                            + recommendedQuantity
            );


            quantityInput.setText(
                    String.valueOf(
                            recommendedQuantity
                    )
            );


            overrideQuantityInputs.put(
                    batch.getBatchId(),
                    quantityInput
            );


            overrideAllocationContainer.addView(
                    row
            );
        }
    }


    private int getRecommendedQuantity(
            String batchId)
    {
        for (IssueFefoDraft.Allocation allocation :
                draft.getAllocations())
        {
            if (allocation
                    .getBatchId()
                    .equals(
                            batchId
                    ))
            {
                return allocation
                        .getAllocatedQuantity();
            }
        }


        return 0;
    }



    private String formatDate(
            long epochMillis)
    {
        return Instant
                .ofEpochMilli(
                        epochMillis
                )
                .atZone(
                        businessZone
                )
                .toLocalDate()
                .format(
                        dateFormatter
                );
    }



    private IssueFefoDraft readDraft()
    {
        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.TIRAMISU)
        {
            return getIntent()
                    .getSerializableExtra(
                            EXTRA_DRAFT,
                            IssueFefoDraft.class
                    );
        }


        @SuppressWarnings("deprecation")
        IssueFefoDraft result =
                (IssueFefoDraft)
                        getIntent()
                                .getSerializableExtra(
                                        EXTRA_DRAFT
                                );


        return result;
    }
}