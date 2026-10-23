package com.medicationinventory.ui.sync;

import android.content.Intent;

import android.os.Bundle;

import android.view.View;

import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.data.local.model.SyncStatusSummary;

import com.medicationinventory.data.repository.AuthenticationRepository;

import com.medicationinventory.ui.auth.LoginActivity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;



/**
 * Displays the signed-in user sync summary, recent operations, and manual synchronization controls.
 */
public class SyncStatusActivity
        extends AppCompatActivity
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private static final DateTimeFormatter
            DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, HH:mm"
            );


    private SyncStatusViewModel
            viewModel;


    private SyncStatusAdapter
            adapter;


    private TextView
            pendingCountText;


    private TextView
            syncedCountText;


    private TextView
            attentionCountText;


    private TextView
            retryingText;


    private TextView
            lastSyncedText;


    private RecyclerView
            recyclerView;


    private TextView
            emptyText;


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


        String userId =
                authenticationRepository
                        .getCurrentUserId();


        if (userId == null
                ||
                userId.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_sync_status
        );


        bindViews();

        setupList();


        SyncStatusViewModelFactory factory =
                new SyncStatusViewModelFactory(
                        userId,
                        application
                                .getAppContainer()
                                .getSyncStatusRepository(),
                        application
                                .getAppContainer()
                                .getSyncMetadataRepository(),
                        application
                                .getAppContainer()
                                .getInventorySyncScheduler()
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                SyncStatusViewModel.class
                        );


        observeData();

        setupActions();
    }



    private void bindViews()
    {
        pendingCountText =
                findViewById(
                        R.id.pendingCountText
                );


        syncedCountText =
                findViewById(
                        R.id.syncedCountText
                );


        attentionCountText =
                findViewById(
                        R.id.attentionCountText
                );


        retryingText =
                findViewById(
                        R.id.retryingText
                );


        lastSyncedText =
                findViewById(
                        R.id.lastSyncedText
                );


        recyclerView =
                findViewById(
                        R.id.syncRecyclerView
                );


        emptyText =
                findViewById(
                        R.id.syncEmptyText
                );
    }



    private void setupList()
    {
        adapter =
                new SyncStatusAdapter(
                        record ->
                        {
                            if (record == null
                                    ||
                                    record.operationId == null
                                    ||
                                    record.operationId
                                            .trim()
                                            .isEmpty())
                            {
                                return;
                            }


                            Intent intent =
                                    new Intent(
                                            this,
                                            SyncDetailActivity.class
                                    );


                            intent.putExtra(
                                    SyncDetailActivity.EXTRA_OPERATION_ID,
                                    record.operationId
                            );


                            startActivity(
                                    intent
                            );
                        }
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



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.syncBackButton
                );


        MaterialButton syncNowButton =
                findViewById(
                        R.id.syncNowButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        syncNowButton.setOnClickListener(
                view ->
                {
                    viewModel.requestSync();


                    Toast.makeText(
                            this,
                            "Synchronisation requested.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );
    }



    private void observeData()
    {
        viewModel
                .getSummary()
                .observe(
                        this,
                        this::renderSummary
                );


        viewModel
                .getRecentOperations()
                .observe(
                        this,
                        records ->
                        {
                            adapter.setItems(
                                    records
                            );


                            int count =
                                    records == null
                                            ? 0
                                            : records.size();


                            recyclerView.setVisibility(
                                    count > 0
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            emptyText.setVisibility(
                                    count == 0
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        }
                );


        viewModel
                .getSyncMetadata()
                .observe(
                        this,
                        this::renderLastSync
                );
    }



    private void renderSummary(
            SyncStatusSummary summary)
    {
        int pending =
                summary == null
                        ? 0
                        : summary.pendingCount;


        int synced =
                summary == null
                        ? 0
                        : summary.syncedCount;


        int attention =
                summary == null
                        ? 0
                        : summary.requiresAttentionCount;


        int retrying =
                summary == null
                        ? 0
                        : summary.retryingCount;


        pendingCountText.setText(
                String.valueOf(
                        pending
                )
        );


        syncedCountText.setText(
                String.valueOf(
                        synced
                )
        );


        attentionCountText.setText(
                String.valueOf(
                        attention
                )
        );


        if (retrying > 0)
        {
            retryingText.setText(
                    retrying
                            + (
                            retrying == 1
                                    ? " operation is waiting to retry."
                                    : " operations are waiting to retry."
                    )
            );


            retryingText.setVisibility(
                    View.VISIBLE
            );
        }
        else
        {
            retryingText.setVisibility(
                    View.GONE
            );
        }
    }



    private void renderLastSync(
            SyncMetadataEntity metadata)
    {
        if (metadata == null
                ||
                metadata.getLastSuccessfulSyncAt() <= 0)
        {
            lastSyncedText.setText(
                    "Last synced: Not yet"
            );

            return;
        }


        String formatted =
                Instant
                        .ofEpochMilli(
                                metadata
                                        .getLastSuccessfulSyncAt()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .format(
                                DATE_FORMAT
                        );


        lastSyncedText.setText(
                "Last synced: "
                        + formatted
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