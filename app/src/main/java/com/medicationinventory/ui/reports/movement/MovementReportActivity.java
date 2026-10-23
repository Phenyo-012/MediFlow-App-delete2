package com.medicationinventory.ui.reports.movement;

import android.app.DatePickerDialog;

import android.content.Intent;

import android.os.Bundle;

import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementReportBatchOption;
import com.medicationinventory.data.local.model.MovementReportMedicineOption;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementReportRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReportDateRange;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.service.ReportDateRangeService;

import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.movement.MovementHistoryAdapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Implements the Movement Report Activity behavior for its Android screen and the application state it presents.
 */
public class MovementReportActivity
        extends AppCompatActivity
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy"
            );


    private RecyclerView recyclerView;

    private ProgressBar progress;

    private TextView emptyText;

    private TextView periodText;

    private MaterialButton loadMoreButton;

    private MaterialButton startDateButton;

    private MaterialButton endDateButton;


    private Spinner periodSpinner;

    private Spinner medicineSpinner;

    private Spinner batchSpinner;

    private Spinner typeSpinner;


    private View customDateContainer;


    private MovementHistoryAdapter adapter;

    private MovementReportViewModel viewModel;


    private List<MovementHistoryRecord>
            latestItems =
            Collections.emptyList();


    private List<MovementReportMedicineOption>
            medicineOptions =
            Collections.emptyList();


    private List<MovementReportBatchOption>
            batchOptions =
            Collections.emptyList();


    private LocalDate customStart;

    private LocalDate customEnd;


    private boolean permissionGranted =
            false;

    private boolean dataReceived =
            false;

    private boolean updatingMedicineSpinner =
            false;

    private boolean updatingBatchSpinner =
            false;


    @Override
    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        if (!authenticationRepository.isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_movement_report
        );


        bindViews();

        setupList();

        setupViewModel(
                application,
                authenticationRepository
        );

        setupSpinners();

        setupActions();

        observeData();
    }


    private void bindViews()
    {
        recyclerView =
                findViewById(
                        R.id.movementReportRecyclerView
                );


        progress =
                findViewById(
                        R.id.movementReportProgress
                );


        emptyText =
                findViewById(
                        R.id.movementReportEmptyText
                );


        periodText =
                findViewById(
                        R.id.movementReportPeriodText
                );


        loadMoreButton =
                findViewById(
                        R.id.movementReportLoadMoreButton
                );


        startDateButton =
                findViewById(
                        R.id.movementReportStartDateButton
                );


        endDateButton =
                findViewById(
                        R.id.movementReportEndDateButton
                );


        periodSpinner =
                findViewById(
                        R.id.movementReportPeriodSpinner
                );


        medicineSpinner =
                findViewById(
                        R.id.movementReportMedicineSpinner
                );


        batchSpinner =
                findViewById(
                        R.id.movementReportBatchSpinner
                );


        typeSpinner =
                findViewById(
                        R.id.movementReportTypeSpinner
                );


        customDateContainer =
                findViewById(
                        R.id.movementReportCustomDateContainer
                );
    }


    private void setupList()
    {

        adapter =
                new MovementHistoryAdapter();


        recyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );


        recyclerView.setAdapter(
                adapter
        );
    }


    private void setupViewModel(
            MedicationInventoryApplication application,
            AuthenticationRepository authenticationRepository)
    {
        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MovementReportRepository repository =
                application
                        .getAppContainer()
                        .getMovementReportRepository();


        ReportDateRangeService dateRangeService =
                application
                        .getAppContainer()
                        .getReportDateRangeService();


        MovementReportViewModelFactory factory =
                new MovementReportViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        repository,
                        dateRangeService
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                MovementReportViewModel.class
                        );
    }


    private void setupSpinners()
    {
        setupSpinner(
                periodSpinner,
                Arrays.asList(
                        "Last 30 Days",
                        "Today",
                        "Yesterday",
                        "Last 7 Days",
                        "This Month",
                        "Previous Month",
                        "Custom Range"
                )
        );


        setupSpinner(
                medicineSpinner,
                Collections.singletonList(
                        "All Medicines"
                )
        );


        setupSpinner(
                batchSpinner,
                Collections.singletonList(
                        "All Batches"
                )
        );



        setupSpinner(
                typeSpinner,
                Arrays.asList(
                        "All Movement Types",
                        "Receipt",
                        "Issue",
                        "Adjustment",
                        "Correction"
                )
        );


        periodSpinner.setSelection(
                0,
                false
        );
    }



    private void setupSpinner(
            Spinner spinner,
            List<String> values)
    {
        ArrayAdapter<String> spinnerAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        values
                );


        spinnerAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        spinner.setAdapter(
                spinnerAdapter
        );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.movementReportBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        periodSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            customDateContainer.setVisibility(
                                    position == 6
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            switch (position)
                            {
                                case 1:
                                    viewModel.useToday();
                                    break;

                                case 2:
                                    viewModel.useYesterday();
                                    break;

                                case 3:
                                    viewModel.useLast7Days();
                                    break;

                                case 4:
                                    viewModel.useThisMonth();
                                    break;

                                case 5:
                                    viewModel.usePreviousMonth();
                                    break;

                                case 6:
                                    break;

                                case 0:
                                default:
                                    viewModel.useLast30Days();
                                    break;
                            }


                            updatePeriodText();
                        }
                )
        );


        medicineSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            if (updatingMedicineSpinner)
                            {
                                return;
                            }


                            if (position <= 0)
                            {
                                viewModel.setMedicine(
                                        null
                                );

                                return;
                            }


                            int index =
                                    position - 1;


                            if (index >= 0
                                    &&
                                    index < medicineOptions.size())
                            {
                                viewModel.setMedicine(
                                        medicineOptions
                                                .get(index)
                                                .medicineId
                                );
                            }
                        }
                )
        );


        batchSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            if (updatingBatchSpinner)
                            {
                                return;
                            }


                            if (position <= 0)
                            {
                                viewModel.setBatch(
                                        null
                                );

                                return;
                            }


                            int index =
                                    position - 1;


                            if (index >= 0
                                    &&
                                    index < batchOptions.size())
                            {
                                viewModel.setBatch(
                                        batchOptions
                                                .get(index)
                                                .batchId
                                );
                            }
                        }
                )
        );


        typeSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            String type =
                                    null;


                            if (position == 1)
                            {
                                type =
                                        "RECEIPT";
                            }
                            else if (position == 2)
                            {
                                type =
                                        "ISSUE";
                            }
                            else if (position == 3)
                            {
                                type =
                                        "ADJUSTMENT";
                            }
                            else if (position == 4)
                            {
                                type =
                                        "CORRECTION";
                            }


                            viewModel.setTransactionType(
                                    type
                            );
                        }
                )
        );


        startDateButton.setOnClickListener(
                view ->
                        selectStartDate()
        );


        endDateButton.setOnClickListener(
                view ->
                        selectEndDate()
        );


        loadMoreButton.setOnClickListener(
                view ->
                        viewModel.loadMore()
        );
    }


    private void observeData()
    {
        viewModel
                .getUserProfile()
                .observe(
                        this,
                        this::applyPermission
                );


        viewModel
                .getReportItems()
                .observe(
                        this,
                        items ->
                        {
                            if (items == null)
                            {
                                showLoading();

                                return;
                            }


                            latestItems =
                                    items;


                            dataReceived =
                                    true;


                            if (permissionGranted)
                            {
                                renderItems(
                                        latestItems
                                );
                            }
                        }
                );


        viewModel
                .getMedicineOptions()
                .observe(
                        this,
                        options ->
                        {
                            medicineOptions =
                                    options == null
                                            ? Collections.emptyList()
                                            : options;


                            if (permissionGranted)
                            {
                                renderMedicineOptions();
                            }
                        }
                );


        viewModel
                .getBatchOptions()
                .observe(
                        this,
                        options ->
                        {
                            batchOptions =
                                    options == null
                                            ? Collections.emptyList()
                                            : options;


                            if (permissionGranted)
                            {
                                renderBatchOptions();
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
                                                &&
                                                permissionGranted
                                                ? View.VISIBLE
                                                : View.GONE
                                )
                );
    }


    private void applyPermission(
            UserProfile profile)
    {
        if (profile == null
                ||
                !profile.can(
                        Permission.GENERATE_REPORTS
                ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to generate reports.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        permissionGranted =
                true;


        renderMedicineOptions();

        renderBatchOptions();


        if (dataReceived)
        {
            renderItems(
                    latestItems
            );
        }


        Boolean canLoadMore =
                viewModel
                        .getCanLoadMore()
                        .getValue();


        loadMoreButton.setVisibility(
                Boolean.TRUE.equals(
                        canLoadMore
                )
                        ? View.VISIBLE
                        : View.GONE
        );
    }



    private void renderItems(
            List<MovementHistoryRecord> items)
    {
        progress.setVisibility(
                View.GONE
        );


        adapter.setItems(
                items
        );


        int count =
                items == null
                        ? 0
                        : items.size();


        recyclerView.setVisibility(
                count == 0
                        ? View.GONE
                        : View.VISIBLE
        );


        emptyText.setVisibility(
                count == 0
                        ? View.VISIBLE
                        : View.GONE
        );


        updatePeriodText();
    }


    private void renderMedicineOptions()
    {
        List<String> labels =
                new ArrayList<>();


        labels.add(
                "All Medicines"
        );


        int selectedPosition =
                0;


        String selectedId =
                viewModel
                        .getCurrentMedicineId();


        for (int index = 0;
             index < medicineOptions.size();
             index++)
        {
            MovementReportMedicineOption option =
                    medicineOptions.get(
                            index
                    );


            String label =
                    option.medicineName == null
                            ? ""
                            : option.medicineName;


            if (option.medicineCode != null
                    &&
                    !option.medicineCode
                            .trim()
                            .isEmpty())
            {
                label +=
                        " · "
                                + option.medicineCode;
            }


            labels.add(
                    label
            );


            if (selectedId != null
                    &&
                    selectedId.equals(
                            option.medicineId
                    ))
            {
                selectedPosition =
                        index + 1;
            }
        }


        updatingMedicineSpinner =
                true;


        setupSpinner(
                medicineSpinner,
                labels
        );


        medicineSpinner.setSelection(
                selectedPosition,
                false
        );


        updatingMedicineSpinner =
                false;
    }


    private void renderBatchOptions()
    {
        List<String> labels =
                new ArrayList<>();


        labels.add(
                "All Batches"
        );


        int selectedPosition =
                0;


        String selectedId =
                viewModel
                        .getCurrentBatchId();


        for (int index = 0;
             index < batchOptions.size();
             index++)
        {
            MovementReportBatchOption option =
                    batchOptions.get(
                            index
                    );


            String label =
                    option.batchNumber == null
                            ||
                            option.batchNumber
                                    .trim()
                                    .isEmpty()
                            ? option.batchId
                            : option.batchNumber;


            labels.add(
                    label
            );


            if (selectedId != null
                    &&
                    selectedId.equals(
                            option.batchId
                    ))
            {
                selectedPosition =
                        index + 1;
            }
        }


        updatingBatchSpinner =
                true;


        setupSpinner(
                batchSpinner,
                labels
        );


        batchSpinner.setSelection(
                selectedPosition,
                false
        );


        updatingBatchSpinner =
                false;
    }


    private void selectStartDate()
    {
        LocalDate initial =
                customStart == null
                        ? LocalDate.now(
                        BUSINESS_ZONE
                )
                        : customStart;


        new DatePickerDialog(
                this,
                (
                        picker,
                        year,
                        month,
                        day
                ) ->
                {
                    customStart =
                            LocalDate.of(
                                    year,
                                    month + 1,
                                    day
                            );


                    startDateButton.setText(
                            customStart.format(
                                    DATE_FORMATTER
                            )
                    );


                    applyCustomRangeIfReady();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }


    private void selectEndDate()
    {
        LocalDate initial =
                customEnd == null
                        ? LocalDate.now(
                        BUSINESS_ZONE
                )
                        : customEnd;


        new DatePickerDialog(
                this,
                (
                        picker,
                        year,
                        month,
                        day
                ) ->
                {
                    customEnd =
                            LocalDate.of(
                                    year,
                                    month + 1,
                                    day
                            );


                    endDateButton.setText(
                            customEnd.format(
                                    DATE_FORMATTER
                            )
                    );


                    applyCustomRangeIfReady();
                },
                initial.getYear(),
                initial.getMonthValue() - 1,
                initial.getDayOfMonth()
        ).show();
    }


    private void applyCustomRangeIfReady()
    {
        if (customStart == null
                ||
                customEnd == null)
        {
            return;
        }


        try
        {
            viewModel.useCustomRange(
                    customStart,
                    customEnd
            );


            updatePeriodText();
        }
        catch (IllegalArgumentException exception)
        {
            Toast.makeText(
                    this,
                    exception.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }



    private void updatePeriodText()
    {
        if (viewModel == null)
        {
            return;
        }


        ReportDateRange range =
                viewModel
                        .getCurrentDateRange();


        if (range == null)
        {
            return;
        }


        LocalDate start =
                Instant
                        .ofEpochMilli(
                                range.getStartInclusive()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .toLocalDate();


        LocalDate end =
                Instant
                        .ofEpochMilli(
                                range.getEndExclusive()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .toLocalDate()
                        .minusDays(
                                1
                        );


        periodText.setText(
                "Report period: "
                        + start.format(
                        DATE_FORMATTER
                )
                        + " – "
                        + end.format(
                        DATE_FORMATTER
                )
        );
    }


    private void showLoading()
    {
        recyclerView.setVisibility(
                View.GONE
        );


        emptyText.setVisibility(
                View.GONE
        );


        loadMoreButton.setVisibility(
                View.GONE
        );


        progress.setVisibility(
                View.VISIBLE
        );
    }


    private void goToLogin()
    {
        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }


    private static class SimpleItemSelectedListener
            implements AdapterView.OnItemSelectedListener
    {
        interface Handler
        {
            void onSelected(
                    int position
            );
        }


        private final Handler
                handler;


        SimpleItemSelectedListener(
                Handler handler)
        {
            this.handler =
                    handler;
        }


        @Override
        public void onItemSelected(
                AdapterView<?> parent,
                View view,
                int position,
                long id)
        {
            if (handler != null)
            {
                handler.onSelected(
                        position
                );
            }
        }


        @Override
        public void onNothingSelected(
                AdapterView<?> parent)
        {
            // No action.
        }
    }
}