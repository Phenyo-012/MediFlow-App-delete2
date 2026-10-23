package com.medicationinventory.ui.audit;

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

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.AuditRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;




/**
 * Implements the Android screen for Audit Log and connects user actions to application state.
 */
public class AuditLogActivity
        extends AppCompatActivity
{
    private AuditLogViewModel
            viewModel;


    private AuditLogAdapter
            adapter;


    private RecyclerView
            recyclerView;


    private ProgressBar
            progress;


    private TextView
            emptyText;


    private TextView
            refreshErrorText;


    private MaterialButton
            refreshButton;


    private boolean permissionGranted =
            false;


    private boolean initialRefreshStarted =
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
                R.layout.activity_audit_log
        );


        bindViews();

        setupRecyclerView();


        AuditRepository auditRepository =
                application
                        .getAppContainer()
                        .getAuditRepository();


        AuditLogViewModelFactory factory =
                new AuditLogViewModelFactory(
                        auditRepository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                AuditLogViewModel.class
                        );


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null)
        {
            goToLogin();

            return;
        }


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        userRepository
                .observeUser(
                        uid
                )
                .observe(
                        this,
                        this::applyPermission
                );


        setupActions();
    }



    private void bindViews()
    {
        recyclerView =
                findViewById(
                        R.id.auditRecyclerView
                );


        progress =
                findViewById(
                        R.id.auditProgress
                );


        emptyText =
                findViewById(
                        R.id.auditEmptyText
                );


        refreshErrorText =
                findViewById(
                        R.id.auditRefreshErrorText
                );


        refreshButton =
                findViewById(
                        R.id.auditRefreshButton
                );
    }



    private void setupRecyclerView()
    {
        adapter =
                new AuditLogAdapter();


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
                        R.id.auditBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        refreshButton.setOnClickListener(
                view ->
                {
                    if (permissionGranted)
                    {
                        viewModel.refresh();
                    }
                }
        );
    }



    private void applyPermission(
            UserProfile profile)
    {
        if (profile == null)
        {
            return;
        }


        if (!profile.isActive()
                ||
                !profile.can(
                        Permission.VIEW_AUDIT
                ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to view audit history.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        if (permissionGranted)
        {
            return;
        }


        permissionGranted =
                true;


        observeAuditData();


        if (!initialRefreshStarted)
        {
            initialRefreshStarted =
                    true;


            viewModel.refresh();
        }
    }



    private void observeAuditData()
    {
        viewModel
                .getAuditRecords()
                .observe(
                        this,
                        records ->
                        {
                            if (!permissionGranted)
                            {
                                return;
                            }


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
                .getRefreshing()
                .observe(
                        this,
                        refreshing ->
                        {
                            boolean active =
                                    Boolean.TRUE.equals(
                                            refreshing
                                    );


                            progress.setVisibility(
                                    active
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            refreshButton.setEnabled(
                                    !active
                            );
                        }
                );


        viewModel
                .getRefreshError()
                .observe(
                        this,
                        message ->
                        {
                            boolean hasMessage =
                                    message != null
                                            &&
                                            !message.trim()
                                                    .isEmpty();


                            refreshErrorText.setVisibility(
                                    hasMessage
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            refreshErrorText.setText(
                                    hasMessage
                                            ? message
                                            : ""
                            );
                        }
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