package com.medicationinventory.ui.reports.alert;

import android.app.DatePickerDialog;

import android.content.Intent;

import android.os.Bundle;

import android.view.View;

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

import com.medicationinventory.data.repository.AlertReportRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReportDateRange;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.service.ReportDateRangeService;

import com.medicationinventory.ui.auth.LoginActivity;

import com.medicationinventory.data.local.model.AlertReportBatchOption;
import com.medicationinventory.data.local.model.AlertReportMedicineOption;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;



/**
 * Implements the Alert Report Activity behavior for its Android screen and the application state it presents.
 */
public class AlertReportActivity
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

    private Spinner periodSpinner;
    private Spinner typeSpinner;
    private Spinner statusSpinner;

    private View customDateContainer;

    private MaterialButton startDateButton;
    private MaterialButton endDateButton;


    private AlertReportAdapter adapter;

    private AlertReportViewModel viewModel;


    private LocalDate customStart;
    private LocalDate customEnd;

    private Spinner medicineSpinner;

    private Spinner batchSpinner;


    private List<AlertReportMedicineOption>
            medicineOptions =
            Collections.emptyList();


    private List<AlertReportBatchOption>
            batchOptions =
            Collections.emptyList();


    private boolean updatingMedicineSpinner =
            false;

    private boolean updatingBatchSpinner =
            false;

    private boolean permissionGranted =
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
                R.layout.activity_alert_report
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
                        R.id.alertReportRecyclerView
                );

        progress =
                findViewById(
                        R.id.alertReportProgress
                );

        emptyText =
                findViewById(
                        R.id.alertReportEmptyText
                );

        periodText =
                findViewById(
                        R.id.alertReportPeriodText
                );

        periodSpinner =
                findViewById(
                        R.id.alertReportPeriodSpinner
                );

        typeSpinner =
                findViewById(
                        R.id.alertReportTypeSpinner
                );

        statusSpinner =
                findViewById(
                        R.id.alertReportStatusSpinner
                );

        customDateContainer =
                findViewById(
                        R.id.alertReportCustomDateContainer
                );

        startDateButton =
                findViewById(
                        R.id.alertReportStartDateButton
                );

        endDateButton =
                findViewById(
                        R.id.alertReportEndDateButton
                );

        medicineSpinner =
                findViewById(
                        R.id.alertReportMedicineSpinner
                );


        batchSpinner =
                findViewById(
                        R.id.alertReportBatchSpinner
                );
    }


    private void setupList()
    {
        adapter =
                new AlertReportAdapter();


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


        AlertReportRepository repository =
                application
                        .getAppContainer()
                        .getAlertReportRepository();


        ReportDateRangeService dateRangeService =
                application
                        .getAppContainer()
                        .getReportDateRangeService();


        AlertReportViewModelFactory factory =
                new AlertReportViewModelFactory(
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
                                AlertReportViewModel.class
                        );
    }


    private void setupSpinners()
    {
        List<String> periods =
                Arrays.asList(
                        "Last 30 Days",
                        "Today",
                        "Yesterday",
                        "Last 7 Days",
                        "This Month",
                        "Previous Month",
                        "Custom Range"
                );


        setupSpinner(
                periodSpinner,
                periods
        );


        setupSpinner(
                typeSpinner,
                Arrays.asList(
                        "All Alert Types",
                        "Low Stock",
                        "Near Expiry",
                        "Expired"
                )
        );


        setupSpinner(
                statusSpinner,
                Arrays.asList(
                        "All Statuses",
                        "Active",
                        "Acknowledged",
                        "Resolved"
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
                        R.id.alertReportBackButton
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
                                    /*
                                     * Wait for both custom dates.
                                     */
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


        typeSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            AlertType type =
                                    null;


                            if (position == 1)
                            {
                                type =
                                        AlertType.LOW_STOCK;
                            }
                            else if (position == 2)
                            {
                                type =
                                        AlertType.NEAR_EXPIRY;
                            }
                            else if (position == 3)
                            {
                                type =
                                        AlertType.EXPIRED;
                            }


                            viewModel.setAlertType(
                                    type
                            );
                        }
                )
        );


        statusSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener(
                        position ->
                        {
                            AlertStatus status =
                                    null;


                            if (position == 1)
                            {
                                status =
                                        AlertStatus.ACTIVE;
                            }
                            else if (position == 2)
                            {
                                status =
                                        AlertStatus.ACKNOWLEDGED;
                            }
                            else if (position == 3)
                            {
                                status =
                                        AlertStatus.RESOLVED;
                            }


                            viewModel.setStatus(
                                    status
                            );
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


                            int optionIndex =
                                    position - 1;


                            if (optionIndex >= 0
                                    &&
                                    optionIndex
                                            < medicineOptions.size())
                            {
                                viewModel.setMedicine(
                                        medicineOptions
                                                .get(
                                                        optionIndex
                                                )
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


                            int optionIndex =
                                    position - 1;


                            if (optionIndex >= 0
                                    &&
                                    optionIndex
                                            < batchOptions.size())
                            {
                                viewModel.setBatch(
                                        batchOptions
                                                .get(
                                                        optionIndex
                                                )
                                                .batchId
                                );
                            }
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
                        this::renderItems
                );

        viewModel
                .getMedicineOptions()
                .observe(
                        this,
                        this::renderMedicineOptions
                );


        viewModel
                .getBatchOptions()
                .observe(
                        this,
                        this::renderBatchOptions
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
    }


    private void renderItems(
            List<com.medicationinventory.data.local.model.AlertReportRecord>
                    items)
    {
        if (!permissionGranted)
        {
            return;
        }


        progress.setVisibility(
                View.GONE
        );


        adapter.submitList(
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

    private void renderMedicineOptions(
            List<AlertReportMedicineOption> options)
    {
        medicineOptions =
                options == null
                        ? Collections.emptyList()
                        : options;


        List<String> labels =
                new ArrayList<>();


        labels.add(
                "All Medicines"
        );


        int selectedPosition =
                0;


        String selectedMedicineId =
                viewModel
                        .getCurrentMedicineId();


        for (int index = 0;
             index < medicineOptions.size();
             index++)
        {
            AlertReportMedicineOption option =
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


            if (selectedMedicineId != null
                    &&
                    selectedMedicineId.equals(
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


    private void renderBatchOptions(
            List<AlertReportBatchOption> options)
    {
        batchOptions =
                options == null
                        ? Collections.emptyList()
                        : options;


        List<String> labels =
                new ArrayList<>();


        labels.add(
                "All Batches"
        );


        int selectedPosition =
                0;


        String selectedBatchId =
                viewModel
                        .getCurrentBatchId();


        for (int index = 0;
             index < batchOptions.size();
             index++)
        {
            AlertReportBatchOption option =
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


            if (selectedBatchId != null
                    &&
                    selectedBatchId.equals(
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
}