package com.medicationinventory.ui.correction;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.model.enums.CorrectionDirection;

import com.medicationinventory.service.CorrectionService;

import com.medicationinventory.ui.movement.MovementHistoryActivity;



/**
 * Implements the Android screen for Correction and connects user actions to application state.
 */
public class CorrectionActivity
        extends AppCompatActivity
{
    public static final String EXTRA_MEDICINE_ID =
            "medicine_id";

    public static final String EXTRA_MEDICINE_NAME =
            "medicine_name";


    private TextView medicineNameText;

    private MaterialButton selectOriginalButton;

    private TextView originalMovementDetailsText;

    private RadioGroup correctionDirectionGroup;

    private TextInputEditText quantityInput;

    private TextInputEditText reasonInput;

    private MaterialButton confirmCorrectionButton;

    private View correctionProgress;


    private CorrectionService correctionService;


    private String medicineId;

    private String medicineName;



    private String selectedOriginalOperationId;

    private String selectedOriginalTransactionId;

    private String selectedBatchId;

    private String selectedBatchNumber;

    private String selectedTransactionType;

    private int selectedOriginalQuantityChange;


    private boolean submitting =
            false;



    private final ActivityResultLauncher<Intent>
            originalMovementLauncher =
            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),
                    result ->
                    {
                        if (result.getResultCode()
                                != RESULT_OK)
                        {
                            return;
                        }


                        Intent data =
                                result.getData();


                        if (data == null)
                        {
                            return;
                        }


                        String operationId =
                                data.getStringExtra(
                                        MovementHistoryActivity
                                                .RESULT_OPERATION_ID
                                );


                        String transactionId =
                                data.getStringExtra(
                                        MovementHistoryActivity
                                                .RESULT_TRANSACTION_ID
                                );


                        String batchId =
                                data.getStringExtra(
                                        MovementHistoryActivity
                                                .RESULT_BATCH_ID
                                );


                        String batchNumber =
                                data.getStringExtra(
                                        MovementHistoryActivity
                                                .RESULT_BATCH_NUMBER
                                );


                        String transactionType =
                                data.getStringExtra(
                                        MovementHistoryActivity
                                                .RESULT_TRANSACTION_TYPE
                                );


                        int quantityChange =
                                data.getIntExtra(
                                        MovementHistoryActivity
                                                .RESULT_QUANTITY_CHANGE,
                                        0
                                );



                        if (!hasText(
                                operationId
                        ))
                        {
                            Toast.makeText(
                                            CorrectionActivity.this,
                                            "The selected movement has no original operation reference.",
                                            Toast.LENGTH_LONG
                                    )
                                    .show();

                            return;
                        }


                        if (!hasText(
                                batchId
                        ))
                        {
                            Toast.makeText(
                                            CorrectionActivity.this,
                                            "The selected movement has no batch reference.",
                                            Toast.LENGTH_LONG
                                    )
                                    .show();

                            return;
                        }


                        selectedOriginalOperationId =
                                operationId.trim();

                        selectedOriginalTransactionId =
                                normalizeOptional(
                                        transactionId
                                );

                        selectedBatchId =
                                batchId.trim();

                        selectedBatchNumber =
                                normalizeOptional(
                                        batchNumber
                                );

                        selectedTransactionType =
                                normalizeOptional(
                                        transactionType
                                );

                        selectedOriginalQuantityChange =
                                quantityChange;


                        showSelectedOriginalMovement();

                        updateConfirmState();
                    }
            );


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
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


        if (!hasText(
                medicineId
        ))
        {
            finish();

            return;
        }


        medicineId =
                medicineId.trim();


        setContentView(
                R.layout.activity_correction
        );


        bindViews();

        setupDependencies();

        setupActions();
    }



    private void bindViews()
    {
        medicineNameText =
                findViewById(
                        R.id.correctionMedicineNameText
                );


        selectOriginalButton =
                findViewById(
                        R.id.selectCorrectionOriginalButton
                );


        originalMovementDetailsText =
                findViewById(
                        R.id.correctionOriginalDetailsText
                );


        correctionDirectionGroup =
                findViewById(
                        R.id.correctionDirectionGroup
                );


        quantityInput =
                findViewById(
                        R.id.correctionQuantityInput
                );


        reasonInput =
                findViewById(
                        R.id.correctionReasonInput
                );


        confirmCorrectionButton =
                findViewById(
                        R.id.confirmCorrectionButton
                );


        correctionProgress =
                findViewById(
                        R.id.correctionProgress
                );


        medicineNameText.setText(
                hasText(
                        medicineName
                )
                        ? medicineName.trim()
                        : "Selected medicine"
        );


        updateConfirmState();
    }



    private void setupDependencies()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        correctionService =
                application
                        .getAppContainer()
                        .getCorrectionService();
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.correctionBackButton
                );


        backButton.setOnClickListener(
                view ->
                {
                    if (!submitting)
                    {
                        finish();
                    }
                }
        );


        selectOriginalButton.setOnClickListener(
                view ->
                        launchOriginalMovementSelection()
        );


        confirmCorrectionButton.setOnClickListener(
                view ->
                        submitCorrection()
        );
    }



    private void launchOriginalMovementSelection()
    {
        if (submitting)
        {
            return;
        }


        Intent intent =
                new Intent(
                        this,
                        MovementHistoryActivity.class
                );


        intent.putExtra(
                MovementHistoryActivity.EXTRA_MEDICINE_ID,
                medicineId
        );


        if (hasText(
                medicineName
        ))
        {
            intent.putExtra(
                    MovementHistoryActivity.EXTRA_MEDICINE_NAME,
                    medicineName.trim()
            );
        }



        intent.putExtra(
                MovementHistoryActivity
                        .EXTRA_SELECT_FOR_CORRECTION,
                true
        );


        originalMovementLauncher.launch(
                intent
        );
    }



    private void showSelectedOriginalMovement()
    {
        String batchLabel =
                hasText(
                        selectedBatchNumber
                )
                        ? selectedBatchNumber
                        : selectedBatchId;


        String typeLabel =
                hasText(
                        selectedTransactionType
                )
                        ? selectedTransactionType
                        : "Inventory movement";


        String quantityLabel =
                selectedOriginalQuantityChange > 0
                        ? "+"
                          + selectedOriginalQuantityChange
                        : String.valueOf(
                        selectedOriginalQuantityChange
                );


        String details =
                "Original movement: "
                        + typeLabel
                        + "  "
                        + quantityLabel
                        + "\nBatch: "
                        + batchLabel
                        + "\nOriginal operation: "
                        + selectedOriginalOperationId;


        originalMovementDetailsText.setText(
                details
        );


        originalMovementDetailsText.setVisibility(
                View.VISIBLE
        );


        selectOriginalButton.setText(
                "Change Original Movement"
        );
    }



    private void submitCorrection()
    {
        if (submitting)
        {
            return;
        }


        if (!hasText(
                selectedOriginalOperationId
        )
                ||
                !hasText(
                        selectedBatchId
                ))
        {
            Toast.makeText(
                            this,
                            "Select the original confirmed movement first.",
                            Toast.LENGTH_SHORT
                    )
                    .show();

            return;
        }


        CorrectionDirection direction;


        int checkedDirection =
                correctionDirectionGroup
                        .getCheckedRadioButtonId();


        if (checkedDirection ==
                R.id.correctionIncreaseRadio)
        {
            direction =
                    CorrectionDirection.IN;
        }
        else if (checkedDirection ==
                R.id.correctionDecreaseRadio)
        {
            direction =
                    CorrectionDirection.OUT;
        }
        else
        {
            Toast.makeText(
                            this,
                            "Select Increase or Decrease.",
                            Toast.LENGTH_SHORT
                    )
                    .show();

            return;
        }


        String quantity =
                quantityInput.getText() == null
                        ? ""
                        : quantityInput
                        .getText()
                        .toString()
                        .trim();


        String reason =
                reasonInput.getText() == null
                        ? ""
                        : reasonInput
                        .getText()
                        .toString()
                        .trim();


        if (quantity.isEmpty())
        {
            quantityInput.setError(
                    "Quantity is required."
            );

            return;
        }


        int parsedQuantity;


        try
        {
            parsedQuantity =
                    Integer.parseInt(
                            quantity
                    );
        }
        catch (NumberFormatException exception)
        {
            quantityInput.setError(
                    "Enter a whole number."
            );

            return;
        }


        if (parsedQuantity <= 0)
        {
            quantityInput.setError(
                    "Quantity must be greater than zero."
            );

            return;
        }


        if (reason.isEmpty())
        {
            reasonInput.setError(
                    "An explanatory reason is required."
            );

            return;
        }


        setSubmitting(
                true
        );


        correctionService
                .commitCorrection(
                        medicineId,
                        selectedBatchId,
                        selectedOriginalOperationId,
                        direction,
                        quantity,
                        reason,
                        new CorrectionService.CorrectionCallback()
                        {
                            @Override
                            public void onSuccess(
                                    CorrectionService.CorrectionCommitResult result)
                            {
                                Toast.makeText(
                                                CorrectionActivity.this,
                                                "Correction recorded locally. Synchronisation pending.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show();



                                finish();
                            }


                            @Override
                            public void onError(
                                    String message)
                            {
                                setSubmitting(
                                        false
                                );


                                Toast.makeText(
                                                CorrectionActivity.this,
                                                message == null
                                                        ? "Unable to record correction."
                                                        : message,
                                                Toast.LENGTH_LONG
                                        )
                                        .show();
                            }
                        }
                );
    }



    private void setSubmitting(
            boolean submitting)
    {
        this.submitting =
                submitting;


        selectOriginalButton.setEnabled(
                !submitting
        );


        correctionDirectionGroup.setEnabled(
                !submitting
        );


        quantityInput.setEnabled(
                !submitting
        );


        reasonInput.setEnabled(
                !submitting
        );


        correctionProgress.setVisibility(
                submitting
                        ? View.VISIBLE
                        : View.GONE
        );


        updateConfirmState();
    }



    private void updateConfirmState()
    {
        confirmCorrectionButton.setEnabled(
                !submitting
                        &&
                        hasText(
                                selectedOriginalOperationId
                        )
                        &&
                        hasText(
                                selectedBatchId
                        )
        );
    }



    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }



    private String normalizeOptional(
            String value)
    {
        if (!hasText(
                value
        ))
        {
            return null;
        }


        return value.trim();
    }
}