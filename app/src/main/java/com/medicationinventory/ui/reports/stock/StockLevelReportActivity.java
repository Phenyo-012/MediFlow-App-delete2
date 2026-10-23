package com.medicationinventory.ui.reports.stock;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.StockLevelReportRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.StockLevelReportItem;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;

import java.util.Collections;
import java.util.List;

/**
 * Implements the Android screen for Stock Level Report and connects user actions to application state.
 */
public class StockLevelReportActivity
        extends AppCompatActivity
{
    private RecyclerView
            recyclerView;

    private View
            emptyState;

    private View
            errorState;

    private ProgressBar
            progress;

    private TextView
            emptyTitle;

    private TextView
            emptyMessage;

    private TextView
            errorText;

    private MaterialButton
            retryButton;

    private MaterialButtonToggleGroup
            filterGroup;


    private StockLevelReportAdapter
            adapter;

    private StockLevelReportViewModel
            viewModel;


    private boolean permissionGranted =
            false;

    private boolean dataReceived =
            false;


    private List<StockLevelReportItem>
            latestItems =
            Collections.emptyList();


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
                R.layout.activity_stock_level_report
        );


        bindViews();

        setupList();

        setupViewModel(
                application,
                authenticationRepository
        );

        setupActions();

        setupFilters();

        observeData();
    }



    private void bindViews()
    {
        recyclerView =
                findViewById(
                        R.id.stockReportRecyclerView
                );


        emptyState =
                findViewById(
                        R.id.stockReportEmptyState
                );


        errorState =
                findViewById(
                        R.id.stockReportErrorState
                );


        progress =
                findViewById(
                        R.id.stockReportProgress
                );


        emptyTitle =
                findViewById(
                        R.id.stockReportEmptyTitle
                );


        emptyMessage =
                findViewById(
                        R.id.stockReportEmptyMessage
                );


        errorText =
                findViewById(
                        R.id.stockReportErrorText
                );


        retryButton =
                findViewById(
                        R.id.stockReportRetryButton
                );


        filterGroup =
                findViewById(
                        R.id.stockReportFilterGroup
                );
    }


    private void setupList()
    {
        adapter =
                new StockLevelReportAdapter(
                        this::openMedicine
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



    private void setupViewModel(
            MedicationInventoryApplication application,
            AuthenticationRepository authenticationRepository)
    {
        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        StockLevelReportRepository repository =
                application
                        .getAppContainer()
                        .getStockLevelReportRepository();


        StockLevelReportViewModelFactory factory =
                new StockLevelReportViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        repository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                StockLevelReportViewModel.class
                        );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.stockReportBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        retryButton.setOnClickListener(
                view ->
                {
                    showLoading();

                    viewModel.retry();
                }
        );
    }


    private void setupFilters()
    {
        filterGroup.addOnButtonCheckedListener(
                (
                        group,
                        checkedId,
                        isChecked
                ) ->
                {
                    if (!isChecked)
                    {
                        return;
                    }


                    if (checkedId
                            == R.id.stockReportReorderOnlyButton)
                    {
                        viewModel.setFilter(
                                StockLevelReportViewModel.Filter.REORDER_ONLY
                        );
                    }
                    else
                    {
                        viewModel.setFilter(
                                StockLevelReportViewModel.Filter.ALL
                        );
                    }
                }
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
                .getVisibleItems()
                .observe(
                        this,
                        items ->
                        {
                            latestItems =
                                    items == null
                                            ? Collections.emptyList()
                                            : items;


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
                .getError()
                .observe(
                        this,
                        this::renderError
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


        if (dataReceived)
        {
            renderItems(
                    latestItems
            );
        }
    }



    private void renderItems(
            List<StockLevelReportItem> items)
    {
        adapter.submitList(
                items
        );


        progress.setVisibility(
                View.GONE
        );


        errorState.setVisibility(
                View.GONE
        );


        int count =
                items == null
                        ? 0
                        : items.size();


        if (count == 0)
        {
            recyclerView.setVisibility(
                    View.GONE
            );


            emptyState.setVisibility(
                    View.VISIBLE
            );


            StockLevelReportViewModel.Filter filter =
                    viewModel
                            .getSelectedFilter()
                            .getValue();


            if (filter
                    == StockLevelReportViewModel.Filter.REORDER_ONLY)
            {
                emptyTitle.setText(
                        "No medicines require reorder"
                );


                emptyMessage.setText(
                        "Current usable stock is above the reorder level for all active medicines."
                );
            }
            else
            {
                emptyTitle.setText(
                        "No stock records"
                );


                emptyMessage.setText(
                        "No active medicines are available for this report."
                );
            }
        }
        else
        {
            emptyState.setVisibility(
                    View.GONE
            );


            recyclerView.setVisibility(
                    View.VISIBLE
            );
        }
    }



    private void renderError(
            String message)
    {
        if (message == null
                ||
                message.trim().isEmpty())
        {
            return;
        }


        progress.setVisibility(
                View.GONE
        );


        recyclerView.setVisibility(
                View.GONE
        );


        emptyState.setVisibility(
                View.GONE
        );


        errorState.setVisibility(
                View.VISIBLE
        );


        errorText.setText(
                message
        );
    }



    private void showLoading()
    {
        recyclerView.setVisibility(
                View.GONE
        );


        emptyState.setVisibility(
                View.GONE
        );


        errorState.setVisibility(
                View.GONE
        );


        progress.setVisibility(
                View.VISIBLE
        );
    }


    private void openMedicine(
            StockLevelReportItem item)
    {
        if (item == null
                ||
                item.getMedicineId() == null)
        {
            return;
        }


        Intent intent =
                new Intent(
                        this,
                        MedicineDetailsActivity.class
                );


        intent.putExtra(
                MedicineDetailsActivity.EXTRA_MEDICINE_ID,
                item.getMedicineId()
        );


        startActivity(
                intent
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
}