package com.medicationinventory.ui.reports;


import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.BottomNavigationPermissionHelper;
import com.medicationinventory.ui.alerts.AlertsActivity;
import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.dashboard.DashboardActivity;
import com.medicationinventory.ui.inventory.InventoryActivity;
import com.medicationinventory.ui.more.MoreActivity;
import com.medicationinventory.ui.reports.alert.AlertReportActivity;
import com.medicationinventory.ui.reports.movement.MovementReportActivity;
import com.medicationinventory.ui.reports.stock.StockLevelReportActivity;
import com.medicationinventory.ui.reports.analytics.AnalyticsReportActivity;


/**
 * Implements the Android screen for Reports and connects user actions to application state.
 */
public class ReportsActivity
        extends AppCompatActivity
{
    private AuthenticationRepository
            authenticationRepository;


    private UserRepository
            userRepository;


    private BottomNavigationView
            bottomNavigation;


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


        authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        if (!authenticationRepository
                .isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_reports
        );


        setupBottomNavigation();

        setupReportCards();

        observePermission();
    }


    private void observePermission()
    {
        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        userRepository
                .observeUser(
                        uid.trim()
                )
                .observe(
                        this,
                        this::applyPermission
                );
    }


    private void applyPermission(
            UserProfile profile)
    {
        BottomNavigationPermissionHelper
                .apply(
                        bottomNavigation,
                        profile
                );


        if (profile == null)
        {
            return;
        }


        if (!profile.can(
                Permission.GENERATE_REPORTS
        ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to generate reports.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();
        }
    }


    private void setupReportCards()
    {
        findViewById(
                R.id.stockLevelReportCard
        )
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                StockLevelReportActivity.class
                                        )
                                )
                );


        findViewById(
                R.id.alertReportCard
        )
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                AlertReportActivity.class
                                        )
                                )
                );


        findViewById(
                R.id.movementReportCard
        )
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                MovementReportActivity.class
                                        )
                                )
                );

        findViewById(
                R.id.analyticsReportCard
        )
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                AnalyticsReportActivity.class
                                        )
                                )
                );
    }


    private void setupBottomNavigation()
    {
        bottomNavigation =
                findViewById(
                        R.id.bottomNavigation
                );


        BottomNavigationPermissionHelper
                .hideProtectedItems(
                        bottomNavigation
                );


        bottomNavigation
                .setSelectedItemId(
                        R.id.nav_reports
                );


        bottomNavigation
                .setOnItemSelectedListener(
                        item ->
                        {
                            int itemId =
                                    item.getItemId();


                            if (itemId
                                    == R.id.nav_reports)
                            {
                                return true;
                            }


                            if (itemId
                                    == R.id.nav_dashboard)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                DashboardActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_inventory)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                InventoryActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_alerts)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                AlertsActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_more)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                MoreActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            return false;
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