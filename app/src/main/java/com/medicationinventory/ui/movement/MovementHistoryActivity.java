package com.medicationinventory.ui.movement;

import android.content.Intent;

import android.os.Bundle;
import android.view.View;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.DatePickerDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementBatchOption;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;



/**
 * Implements the Android screen for Movement History and connects user actions to application state.
 */
public class MovementHistoryActivity
        extends AppCompatActivity
{
    public static final String EXTRA_MEDICINE_ID =
            "movement_medicine_id";


    public static final String EXTRA_MEDICINE_NAME =
            "movement_medicine_name";

    public static final String EXTRA_SELECT_FOR_CORRECTION =
            "movement_select_for_correction";


    public static final String RESULT_OPERATION_ID =
            "selected_original_operation_id";


    public static final String RESULT_TRANSACTION_ID =
            "selected_original_transaction_id";


    public static final String RESULT_BATCH_ID =
            "selected_original_batch_id";


    public static final String RESULT_BATCH_NUMBER =
            "selected_original_batch_number";


    public static final String RESULT_TRANSACTION_TYPE =
            "selected_original_transaction_type";


    public static final String RESULT_QUANTITY_CHANGE =
            "selected_original_quantity_change";

    private final ZoneId businessZone =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy"
            );


    private final List<MovementBatchOption>
            batchOptions =
            new ArrayList<>();


    private MaterialButton batchFilterButton;

    private MaterialButton startDateButton;

    private MaterialButton endDateButton;

    private MaterialButton clearFiltersButton;

    private String selectedBatchId;

    private LocalDate selectedStartDate;

    private LocalDate selectedEndDate;

    private String medicineId;

    private MovementHistoryViewModel
            viewModel;

    private MovementHistoryAdapter
            adapter;

    private RecyclerView recyclerView;

    private View emptyState;

    private ProgressBar progressBar;

    private MaterialButton loadMoreButton;

    private boolean correctionSelectionMode;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );

        correctionSelectionMode =
                getIntent()
                        .getBooleanExtra(
                                EXTRA_SELECT_FOR_CORRECTION,
                                false
                        );


        medicineId =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_ID
                        );


        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            finish();

            return;
        }


        setContentView(
                R.layout.activity_movement_history
        );


        bindViews();

        setupRecyclerView();

        setupViewModel();

        setupActions();

        observeData();

        viewModel.checkAccess();
    }



    private void bindViews()
    {
        recyclerView =
                findViewById(
                        R.id.movementHistoryRecyclerView
                );


        emptyState =
                findViewById(
                        R.id.movementHistoryEmptyState
                );


        progressBar =
                findViewById(
                        R.id.movementHistoryProgress
                );


        TextView medicineText =
                findViewById(
                        R.id.movementHistoryMedicineText
                );


        String medicineName =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_NAME
                        );

        batchFilterButton =
                findViewById(
                        R.id.movementBatchFilterButton
                );


        startDateButton =
                findViewById(
                        R.id.movementStartDateButton
                );


        endDateButton =
                findViewById(
                        R.id.movementEndDateButton
                );


        clearFiltersButton =
                findViewById(
                        R.id.movementClearFiltersButton
                );

        loadMoreButton =
                findViewById(
                        R.id.movementLoadMoreButton
                );


        String displayMedicineName =
                medicineName == null
                        ||
                        medicineName.trim().isEmpty()
                        ? "Selected medicine"
                        : medicineName;


        if (correctionSelectionMode)
        {
            medicineText.setText(
                    "Select confirmed movement • "
                            + displayMedicineName
            );
        }
        else
        {
            medicineText.setText(
                    displayMedicineName
            );
        }
    }



    private void setupRecyclerView()
    {
        adapter =
                new MovementHistoryAdapter();

        adapter.setCorrectionSelectionMode(
                correctionSelectionMode
        );


        adapter.setOnMovementSelectedListener(
                this::selectOriginalMovement
        );


        recyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );


        recyclerView.setAdapter(
                adapter
        );
    }


    private void setupViewModel()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository
                authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MovementHistoryRepository
                movementHistoryRepository =
                application
                        .getAppContainer()
                        .getMovementHistoryRepository();


        MovementHistoryViewModelFactory factory =
                new MovementHistoryViewModelFactory(
                        medicineId,
                        authenticationRepository,
                        userRepository,
                        movementHistoryRepository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                MovementHistoryViewModel.class
                        );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.movementHistoryBackButton
                );


        backButton.setOnClickListener(
                view -> finish()
        );


        ChipGroup filterGroup =
                findViewById(
                        R.id.movementTypeChipGroup
                );

        batchFilterButton.setOnClickListener(
                view -> showBatchFilter()
        );


        startDateButton.setOnClickListener(
                view -> selectStartDate()
        );


        endDateButton.setOnClickListener(
                view -> selectEndDate()
        );


        clearFiltersButton.setOnClickListener(
                view -> clearFilters()
        );

        loadMoreButton.setOnClickListener(
                view -> viewModel.loadMore()
        );


        filterGroup.setOnCheckedChangeListener(
                (group, checkedId) ->
                {
                    if (checkedId ==
                            R.id.movementReceiptChip)
                    {
                        viewModel
                                .setTransactionTypeFilter(
                                        "RECEIPT"
                                );

                        return;
                    }


                    if (checkedId ==
                            R.id.movementIssueChip)
                    {
                        viewModel
                                .setTransactionTypeFilter(
                                        "ISSUE"
                                );

                        return;
                    }


                    if (checkedId ==
                            R.id.movementAdjustmentChip)
                    {
                        viewModel
                                .setTransactionTypeFilter(
                                        "ADJUSTMENT"
                                );

                        return;
                    }


                    if (checkedId ==
                            R.id.movementCorrectionChip)
                    {
                        viewModel
                                .setTransactionTypeFilter(
                                        "CORRECTION"
                                );

                        return;
                    }


                    viewModel
                            .setTransactionTypeFilter(
                                    null
                            );
                }
        );
    }


    private void observeData()
    {
        viewModel
                .getCanViewHistory()
                .observe(
                        this,
                        allowed ->
                        {
                            if (allowed == null)
                            {
                                return;
                            }


                            if (!allowed)
                            {
                                Toast.makeText(
                                                this,
                                                "You do not have permission to view inventory history.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show();


                                finish();

                                return;
                            }


                            viewModel.startHistory();
                        }
                );


        viewModel
                .getHistory()
                .observe(
                        this,
                        this::showHistory
                );

        viewModel
                .getBatchOptions()
                .observe(
                        this,
                        options ->
                        {
                            batchOptions.clear();


                            if (options != null)
                            {
                                batchOptions.addAll(
                                        options
                                );
                            }
                        }
                );

        viewModel
                .getCanLoadMore()
                .observe(
                        this,
                        canLoadMore ->
                                loadMoreButton.setVisibility(
                                        Boolean.TRUE.equals(
                                                canLoadMore
                                        )
                                                ? View.VISIBLE
                                                : View.GONE
                                )
                );
    }


    private void showHistory(
            List<MovementHistoryRecord> records)
    {
        progressBar.setVisibility(
                View.GONE
        );


        adapter.setItems(
                records
        );


        boolean empty =
                records == null
                        ||
                        records.isEmpty();


        emptyState.setVisibility(
                empty
                        ? View.VISIBLE
                        : View.GONE
        );


        recyclerView.setVisibility(
                empty
                        ? View.GONE
                        : View.VISIBLE
        );
    }

    private void showBatchFilter()
    {
        CharSequence[] labels =
                new CharSequence[
                        batchOptions.size() + 1
                        ];


        labels[0] =
                "All batches";


        int checkedIndex =
                0;


        for (int index = 0;
             index < batchOptions.size();
             index++)
        {
            MovementBatchOption option =
                    batchOptions.get(
                            index
                    );


            labels[index + 1] =
                    option.batchNumber;


            if (option.batchId != null
                    && option.batchId.equals(
                    selectedBatchId
            ))
            {
                checkedIndex =
                        index + 1;
            }
        }


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Filter by batch"
                )
                .setSingleChoiceItems(
                        labels,
                        checkedIndex,
                        (dialog, which) ->
                        {
                            if (which == 0)
                            {
                                selectedBatchId =
                                        null;


                                batchFilterButton.setText(
                                        "Batch: All batches"
                                );


                                viewModel.setBatchFilter(
                                        null
                                );
                            }
                            else
                            {
                                MovementBatchOption option =
                                        batchOptions.get(
                                                which - 1
                                        );


                                selectedBatchId =
                                        option.batchId;


                                batchFilterButton.setText(
                                        "Batch: "
                                                + option.batchNumber
                                );


                                viewModel.setBatchFilter(
                                        option.batchId
                                );
                            }


                            dialog.dismiss();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }


    private void selectStartDate()
    {
        LocalDate initial =
                selectedStartDate == null
                        ? LocalDate.now(
                        businessZone
                )
                        : selectedStartDate;


        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) ->
                        {
                            LocalDate selected =
                                    LocalDate.of(
                                            year,
                                            month + 1,
                                            dayOfMonth
                                    );


                            if (selectedEndDate != null
                                    &&
                                    selected.isAfter(
                                            selectedEndDate
                                    ))
                            {
                                Toast.makeText(
                                                this,
                                                "Start date cannot be after end date.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show();

                                return;
                            }


                            selectedStartDate =
                                    selected;


                            startDateButton.setText(
                                    "From: "
                                            + selected.format(
                                            dateFormatter
                                    )
                            );


                            applyDateFilter();
                        },
                        initial.getYear(),
                        initial.getMonthValue() - 1,
                        initial.getDayOfMonth()
                );


        dialog.show();
    }


    private void selectEndDate()
    {
        LocalDate initial =
                selectedEndDate == null
                        ? LocalDate.now(
                        businessZone
                )
                        : selectedEndDate;


        DatePickerDialog dialog =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) ->
                        {
                            LocalDate selected =
                                    LocalDate.of(
                                            year,
                                            month + 1,
                                            dayOfMonth
                                    );


                            if (selectedStartDate != null
                                    &&
                                    selected.isBefore(
                                            selectedStartDate
                                    ))
                            {
                                Toast.makeText(
                                                this,
                                                "End date cannot be before start date.",
                                                Toast.LENGTH_LONG
                                        )
                                        .show();

                                return;
                            }


                            selectedEndDate =
                                    selected;


                            endDateButton.setText(
                                    "To: "
                                            + selected.format(
                                            dateFormatter
                                    )
                            );


                            applyDateFilter();
                        },
                        initial.getYear(),
                        initial.getMonthValue() - 1,
                        initial.getDayOfMonth()
                );


        dialog.show();
    }


    private void applyDateFilter()
    {
        long startInclusive =
                selectedStartDate == null
                        ? 0L
                        : selectedStartDate
                        .atStartOfDay(
                                businessZone
                        )
                        .toInstant()
                        .toEpochMilli();


        long endExclusive =
                selectedEndDate == null
                        ? 0L
                        : selectedEndDate
                        .plusDays(
                                1
                        )
                        .atStartOfDay(
                                businessZone
                        )
                        .toInstant()
                        .toEpochMilli();


        viewModel.setDateRange(
                startInclusive,
                endExclusive
        );
    }


    private void clearFilters()
    {
        selectedBatchId =
                null;

        selectedStartDate =
                null;

        selectedEndDate =
                null;


        batchFilterButton.setText(
                "Batch: All batches"
        );


        startDateButton.setText(
                "From: Any"
        );


        endDateButton.setText(
                "To: Any"
        );


        ChipGroup filterGroup =
                findViewById(
                        R.id.movementTypeChipGroup
                );


        filterGroup.check(
                R.id.movementAllChip
        );


        viewModel.clearFilters();
    }


    private void selectOriginalMovement(
            MovementHistoryRecord record)
    {
        if (!correctionSelectionMode)
        {
            return;
        }


        if (record == null)
        {
            return;
        }



        if (record.provisional
                ||
                !"CONFIRMED".equals(
                        record.operationStatus
                ))
        {
            Toast.makeText(
                            this,
                            "Only confirmed inventory movements can be corrected.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        if (!hasText(
                record.operationId
        ))
        {
            Toast.makeText(
                            this,
                            "The original operation reference is unavailable.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        if (!hasText(
                record.batchId
        ))
        {
            Toast.makeText(
                            this,
                            "The original batch reference is unavailable.",
                            Toast.LENGTH_LONG
                    )
                    .show();

            return;
        }


        Intent result =
                new Intent();


        result.putExtra(
                RESULT_OPERATION_ID,
                record.operationId
        );


        result.putExtra(
                RESULT_TRANSACTION_ID,
                record.transactionId
        );


        result.putExtra(
                RESULT_BATCH_ID,
                record.batchId
        );


        result.putExtra(
                RESULT_BATCH_NUMBER,
                record.batchNumber
        );


        result.putExtra(
                RESULT_TRANSACTION_TYPE,
                record.transactionType
        );


        result.putExtra(
                RESULT_QUANTITY_CHANGE,
                record.quantityChange
        );


        setResult(
                RESULT_OK,
                result
        );


        finish();
    }


    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }
}