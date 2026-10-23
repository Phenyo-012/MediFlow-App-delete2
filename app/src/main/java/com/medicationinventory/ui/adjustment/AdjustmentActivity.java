package com.medicationinventory.ui.adjustment;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.entity.BatchEntity;

import com.medicationinventory.model.enums.AdjustmentDirection;

import com.medicationinventory.service.AdjustmentService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;


/**
 * Presents a medicine's local batches and collects an explicit increase/decrease adjustment with a
 * quantity and reason. The selected batch and current business date are displayed for context, but
 * {@link AdjustmentService} revalidates the request, current profile permission, and stock before
 * the local Room transaction is recorded and queued for trusted processing. A screen-level
 * submission guard prevents duplicate taps while the asynchronous service callback is pending.
 */
public class AdjustmentActivity
        extends AppCompatActivity
{
    public static final String EXTRA_MEDICINE_ID =
            "medicine_id";

    public static final String EXTRA_MEDICINE_NAME =
            "medicine_name";


    private final List<BatchEntity> batches =
            new ArrayList<>();


    private final ZoneId businessZone =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy"
            );


    private TextView medicineNameText;
    private TextView selectedBatchDetailsText;
    private TextView emptyBatchText;

    private Spinner batchSpinner;

    private RadioGroup adjustmentDirectionGroup;

    private TextInputEditText quantityInput;
    private TextInputEditText reasonInput;

    private MaterialButton confirmAdjustmentButton;

    private View adjustmentProgress;


    private AdjustmentService adjustmentService;

    private String medicineId;
    private String medicineName;

    private BatchEntity selectedBatch;

    private boolean submitting = false;


    /** Binds the adjustment form to the selected medicine, local batches, and asynchronous service workflow. */
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


        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            finish();

            return;
        }


        setContentView(
                R.layout.activity_adjustment
        );


        bindViews();

        setupDependencies();

        setupActions();

        observeBatches();
    }


    /**
     * Prepares views for the current screen state. Presentation updates should not secretly change stock records.
     */
    private void bindViews()
    {
        medicineNameText =
                findViewById(
                        R.id.adjustmentMedicineNameText
                );


        batchSpinner =
                findViewById(
                        R.id.adjustmentBatchSpinner
                );


        selectedBatchDetailsText =
                findViewById(
                        R.id.adjustmentBatchDetailsText
                );


        emptyBatchText =
                findViewById(
                        R.id.adjustmentEmptyBatchText
                );


        adjustmentDirectionGroup =
                findViewById(
                        R.id.adjustmentDirectionGroup
                );


        quantityInput =
                findViewById(
                        R.id.adjustmentQuantityInput
                );


        reasonInput =
                findViewById(
                        R.id.adjustmentReasonInput
                );


        confirmAdjustmentButton =
                findViewById(
                        R.id.confirmAdjustmentButton
                );


        adjustmentProgress =
                findViewById(
                        R.id.adjustmentProgress
                );


        medicineNameText.setText(
                medicineName == null
                        ||
                        medicineName.trim().isEmpty()
                        ? "Selected medicine"
                        : medicineName
        );
    }


    /**
     * Prepares dependencies for the current screen state. Presentation updates should not secretly change stock
     * records.
     */
    private void setupDependencies()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        adjustmentService =
                application
                        .getAppContainer()
                        .getAdjustmentService();
    }


    /**
     * Prepares actions for the current screen state. Presentation updates should not secretly change stock
     * records.
     */
    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.adjustmentBackButton
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


        batchSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener()
                {
                    @Override
                    /**
                     * Keeps the on item selected decision in one place, rather than repeating it in several callers.
                     */
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id)
                    {
                        if (position < 0
                                ||
                                position >= batches.size())
                        {
                            selectedBatch =
                                    null;

                            selectedBatchDetailsText
                                    .setVisibility(
                                            View.GONE
                                    );

                            return;
                        }


                        selectedBatch =
                                batches.get(
                                        position
                                );


                        showSelectedBatch(
                                selectedBatch
                        );
                    }


                    @Override
                    /**
                     * Keeps the on nothing selected decision in one place, rather than repeating it in several callers.
                     */
                    public void onNothingSelected(
                            AdapterView<?> parent)
                    {
                        selectedBatch =
                                null;


                        selectedBatchDetailsText
                                .setVisibility(
                                        View.GONE
                                );
                    }
                }
        );


        confirmAdjustmentButton
                .setOnClickListener(
                        view ->
                                submitAdjustment()
                );
    }


    /**
     * Provides an observable view of batches. Callers receive fresh values when the underlying local data
     * changes.
     */
    private void observeBatches()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        BatchDao batchDao =
                application
                        .getAppContainer()
                        .getAppDatabase()
                        .batchDao();


        /*
         * Room remains the local source of truth.
         *
         * We intentionally load ALL batches for the
         * medicine because adjustment is not the same
         * workflow as FEFO issue.
         *
         * A depleted or expired batch may still require
         * a controlled inventory adjustment.
         */
        batchDao
                .observeForMedicine(
                        medicineId
                )
                .observe(
                        this,
                        this::showBatches
                );
    }


    /**
     * Prepares batches for the current screen state. Presentation updates should not secretly change stock
     * records.
     */
    private void showBatches(
            List<BatchEntity> result)
    {
        batches.clear();


        if (result != null)
        {
            batches.addAll(
                    result
            );
        }


        if (batches.isEmpty())
        {
            selectedBatch =
                    null;


            batchSpinner.setVisibility(
                    View.GONE
            );


            selectedBatchDetailsText
                    .setVisibility(
                            View.GONE
                    );


            emptyBatchText.setVisibility(
                    View.VISIBLE
            );


            confirmAdjustmentButton
                    .setEnabled(
                            false
                    );


            return;
        }


        emptyBatchText.setVisibility(
                View.GONE
        );


        batchSpinner.setVisibility(
                View.VISIBLE
        );


        ArrayList<String> labels =
                new ArrayList<>();


        for (BatchEntity batch :
                batches)
        {
            labels.add(
                    batch.getBatchNumber()
                            + "  •  "
                            + batch.getQuantityAvailable()
                            + " available  •  "
                            + batch.getStatus().name()
            );
        }


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        labels
                );


        adapter.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item
        );


        batchSpinner.setAdapter(
                adapter
        );


        if (!submitting)
        {
            confirmAdjustmentButton
                    .setEnabled(
                            true
                    );
        }
    }


    /**
     * Prepares selected batch for the current screen state. Presentation updates should not secretly change stock
     * records.
     */
    private void showSelectedBatch(
            BatchEntity batch)
    {
        LocalDate expiryDate =
                Instant
                        .ofEpochMilli(
                                batch.getExpiryDate()
                        )
                        .atZone(
                                businessZone
                        )
                        .toLocalDate();


        String details =
                "Current quantity: "
                        + batch.getQuantityAvailable()
                        + "\nStatus: "
                        + batch.getStatus().name()
                        + "\nExpiry: "
                        + dateFormatter.format(
                        expiryDate
                );


        selectedBatchDetailsText
                .setText(
                        details
                );


        selectedBatchDetailsText
                .setVisibility(
                        View.VISIBLE
                );
    }


    /**
     * Converts the selected direction, batch, quantity, and reason into a service request. The
     * service owns validation and authorization; this activity locks the form during the callback
     * and presents whether the operation was saved locally or failed.
     */
    private void submitAdjustment()
    {
        if (submitting)
        {
            return;
        }


        if (selectedBatch == null)
        {
            Toast.makeText(
                    this,
                    "Select a batch.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        AdjustmentDirection direction;


        int checkedDirection =
                adjustmentDirectionGroup
                        .getCheckedRadioButtonId();


        if (checkedDirection ==
                R.id.adjustmentIncreaseRadio)
        {
            direction =
                    AdjustmentDirection.IN;
        }
        else if (checkedDirection ==
                R.id.adjustmentDecreaseRadio)
        {
            direction =
                    AdjustmentDirection.OUT;
        }
        else
        {
            Toast.makeText(
                    this,
                    "Select Increase or Decrease.",
                    Toast.LENGTH_SHORT
            ).show();

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
                    "A reason is required."
            );

            return;
        }


        /*
         * The first valid submission disables the
         * control immediately.
         *
         * This prevents a second tap from generating a
         * second business operation.
         */
        setSubmitting(
                true
        );


        adjustmentService
                .commitAdjustment(
                        medicineId,
                        selectedBatch.getBatchId(),
                        direction,
                        quantity,
                        reason,
                        new AdjustmentService.AdjustmentCallback()
                        {
                            @Override
                            public void onSuccess(
                                    AdjustmentService.AdjustmentCommitResult result)
                            {
                                Toast.makeText(
                                        AdjustmentActivity.this,
                                        "Adjustment recorded locally. Synchronisation pending.",
                                        Toast.LENGTH_LONG
                                ).show();


                                /*
                                 * Return to Medicine Details.
                                 *
                                 * Because it reads Room, the
                                 * provisional quantity will already
                                 * be visible there.
                                 */
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
                                        AdjustmentActivity.this,
                                        message == null
                                                ? "Unable to record adjustment."
                                                : message,
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                );
    }


    private void setSubmitting(
            boolean submitting)
    {
        this.submitting =
                submitting;


        confirmAdjustmentButton
                .setEnabled(
                        !submitting
                                &&
                                !batches.isEmpty()
                );


        batchSpinner.setEnabled(
                !submitting
        );


        adjustmentDirectionGroup
                .setEnabled(
                        !submitting
                );


        quantityInput.setEnabled(
                !submitting
        );


        reasonInput.setEnabled(
                !submitting
        );


        adjustmentProgress
                .setVisibility(
                        submitting
                                ? View.VISIBLE
                                : View.GONE
                );
    }
}