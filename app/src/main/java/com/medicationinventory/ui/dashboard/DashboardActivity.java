package com.medicationinventory.ui.dashboard;


import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.local.model.MovementHistoryRecord;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.BottomNavigationPermissionHelper;
import com.medicationinventory.ui.alerts.AlertsActivity;
import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.inventory.InventoryActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;
import com.medicationinventory.ui.more.MoreActivity;
import com.medicationinventory.ui.reorder.ReorderRecommendationsActivity;
import com.medicationinventory.ui.reports.ReportsActivity;
import com.medicationinventory.ui.sync.SyncStatusActivity;

import java.util.List;
import java.util.Locale;




/**
 * Implements the Android screen for Dashboard and connects user actions to application state.
 */
public class DashboardActivity
        extends AppCompatActivity
{
    private TextView greetingText;

    private TextView profileInitials;

    private View searchButton;

    private View alertsButton;


    private View totalMedicinesCard;

    private View lowStockCard;

    private View nearExpiryCard;

    private View activeAlertsCard;

    private View pendingSyncCard;

    private View syncAttentionCard;


    private TextView totalMedicinesValue;

    private TextView lowStockValue;

    private TextView nearExpiryValue;

    private TextView activeAlertsValue;

    private TextView pendingSyncValue;

    private TextView syncAttentionValue;


    private View syncFreshnessCard;

    private TextView lastSyncText;


    private View stockOverviewSection;

    private View stockNearExpiryRow;

    private View stockExpiredRow;

    private TextView stockLowText;

    private TextView stockNearExpiryText;

    private TextView stockExpiredText;

    private View viewAllStock;


    private View criticalExpirySection;

    private View criticalExpiryCard;

    private TextView criticalExpiryTitle;

    private TextView criticalExpiryDescription;


    private View recentMovementSection;

    private LinearLayout recentMovementContainer;

    private TextView recentMovementEmpty;


    private BottomNavigationView
            bottomNavigation;


    private DashboardViewModel
            dashboardViewModel;


    private UserProfile
            currentUserProfile;


    private int latestLowStockCount = 0;

    private int latestNearExpiryCount = 0;

    private int latestExpiredCount = 0;


    private List<MovementHistoryRecord>
            latestMovements;


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


        if (!authenticationRepository
                .isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_dashboard
        );


        bindViews();



        setupBottomNavigation();


        setupViewModel(
                application,
                authenticationRepository
        );


        setupActions();
    }



    private void bindViews()
    {
        greetingText =
                findViewById(
                        R.id.greetingText
                );


        profileInitials =
                findViewById(
                        R.id.profileInitials
                );


        searchButton =
                findViewById(
                        R.id.searchButton
                );


        alertsButton =
                findViewById(
                        R.id.alertsButton
                );


        totalMedicinesCard =
                findViewById(
                        R.id.totalMedicinesCard
                );


        lowStockCard =
                findViewById(
                        R.id.lowStockCard
                );


        nearExpiryCard =
                findViewById(
                        R.id.nearExpiryCard
                );


        activeAlertsCard =
                findViewById(
                        R.id.activeAlertsCard
                );


        pendingSyncCard =
                findViewById(
                        R.id.pendingSyncCard
                );


        syncAttentionCard =
                findViewById(
                        R.id.syncAttentionCard
                );


        totalMedicinesValue =
                findViewById(
                        R.id.totalMedicinesValue
                );


        lowStockValue =
                findViewById(
                        R.id.lowStockValue
                );


        nearExpiryValue =
                findViewById(
                        R.id.nearExpiryValue
                );


        activeAlertsValue =
                findViewById(
                        R.id.activeAlertsValue
                );


        pendingSyncValue =
                findViewById(
                        R.id.pendingSyncValue
                );


        syncAttentionValue =
                findViewById(
                        R.id.syncAttentionValue
                );


        syncFreshnessCard =
                findViewById(
                        R.id.syncFreshnessCard
                );


        lastSyncText =
                findViewById(
                        R.id.lastSyncText
                );


        stockOverviewSection =
                findViewById(
                        R.id.stockOverviewSection
                );


        stockNearExpiryRow =
                findViewById(
                        R.id.stockNearExpiryRow
                );


        stockExpiredRow =
                findViewById(
                        R.id.stockExpiredRow
                );


        stockLowText =
                findViewById(
                        R.id.stockLowText
                );


        stockNearExpiryText =
                findViewById(
                        R.id.stockNearExpiryText
                );


        stockExpiredText =
                findViewById(
                        R.id.stockExpiredText
                );


        viewAllStock =
                findViewById(
                        R.id.viewAllStock
                );


        criticalExpirySection =
                findViewById(
                        R.id.criticalExpirySection
                );


        criticalExpiryCard =
                findViewById(
                        R.id.criticalExpiryCard
                );


        criticalExpiryTitle =
                findViewById(
                        R.id.criticalExpiryTitle
                );


        criticalExpiryDescription =
                findViewById(
                        R.id.criticalExpiryDescription
                );


        recentMovementSection =
                findViewById(
                        R.id.recentMovementSection
                );


        recentMovementContainer =
                findViewById(
                        R.id.recentMovementContainer
                );


        recentMovementEmpty =
                findViewById(
                        R.id.recentMovementEmpty
                );


        hidePermissionControlledContent();
    }



    private void setupViewModel(
            MedicationInventoryApplication application,
            AuthenticationRepository authenticationRepository)
    {
        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MedicineRepository medicineRepository =
                application
                        .getAppContainer()
                        .getMedicineRepository();


        DashboardViewModelFactory factory =
                new DashboardViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        medicineRepository,

                        application
                                .getAppContainer()
                                .getReorderRecommendationsRepository(),

                        application
                                .getAppContainer()
                                .getAlertRepository(),

                        application
                                .getAppContainer()
                                .getSyncStatusRepository(),

                        application
                                .getAppContainer()
                                .getSyncMetadataRepository(),

                        application
                                .getAppContainer()
                                .getMovementHistoryRepository()
                );


        dashboardViewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                DashboardViewModel.class
                        );


        dashboardViewModel
                .getUserProfile()
                .observe(
                        this,
                        this::showUserProfile
                );


        dashboardViewModel
                .getTotalMedicines()
                .observe(
                        this,
                        count ->
                                totalMedicinesValue
                                        .setText(
                                                String.valueOf(
                                                        safeCount(
                                                                count
                                                        )
                                                )
                                        )
                );


        dashboardViewModel
                .getLowStockCount()
                .observe(
                        this,
                        count ->
                        {
                            latestLowStockCount =
                                    safeCount(
                                            count
                                    );


                            lowStockValue.setText(
                                    String.valueOf(
                                            latestLowStockCount
                                    )
                            );


                            renderStockOverview();
                        }
                );


        dashboardViewModel
                .getNearExpiryCount()
                .observe(
                        this,
                        count ->
                        {
                            latestNearExpiryCount =
                                    safeCount(
                                            count
                                    );


                            nearExpiryValue.setText(
                                    String.valueOf(
                                            latestNearExpiryCount
                                    )
                            );


                            renderStockOverview();

                            renderCriticalExpiry();
                        }
                );


        dashboardViewModel
                .getActiveAlertCount()
                .observe(
                        this,
                        count ->
                                activeAlertsValue
                                        .setText(
                                                String.valueOf(
                                                        safeCount(
                                                                count
                                                        )
                                                )
                                        )
                );


        dashboardViewModel
                .getExpiredAlertCount()
                .observe(
                        this,
                        count ->
                        {
                            latestExpiredCount =
                                    safeCount(
                                            count
                                    );


                            renderStockOverview();

                            renderCriticalExpiry();
                        }
                );


        dashboardViewModel
                .getPendingSyncCount()
                .observe(
                        this,
                        count ->
                                pendingSyncValue
                                        .setText(
                                                String.valueOf(
                                                        safeCount(
                                                                count
                                                        )
                                                )
                                        )
                );


        dashboardViewModel
                .getSyncAttentionCount()
                .observe(
                        this,
                        count ->
                                syncAttentionValue
                                        .setText(
                                                String.valueOf(
                                                        safeCount(
                                                                count
                                                        )
                                                )
                                        )
                );


        dashboardViewModel
                .getLastSuccessfulSyncAt()
                .observe(
                        this,
                        this::renderLastSync
                );


        dashboardViewModel
                .getRecentMovements()
                .observe(
                        this,
                        movements ->
                        {
                            latestMovements =
                                    movements;


                            renderRecentMovements(
                                    movements
                            );
                        }
                );
    }



    private void setupActions()
    {
        searchButton
                .setOnClickListener(
                        view ->
                                openInventory()
                );


        alertsButton
                .setOnClickListener(
                        view ->
                                openAlerts()
                );


        totalMedicinesCard
                .setOnClickListener(
                        view ->
                                openInventory()
                );


        lowStockCard
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                ReorderRecommendationsActivity.class
                                        )
                                )
                );


        nearExpiryCard
                .setOnClickListener(
                        view ->
                                openAlerts()
                );


        activeAlertsCard
                .setOnClickListener(
                        view ->
                                openAlerts()
                );


        pendingSyncCard
                .setOnClickListener(
                        view ->
                                openSyncStatus()
                );


        syncAttentionCard
                .setOnClickListener(
                        view ->
                                openSyncStatus()
                );


        syncFreshnessCard
                .setOnClickListener(
                        view ->
                                openSyncStatus()
                );


        viewAllStock
                .setOnClickListener(
                        view ->
                                openInventory()
                );


        criticalExpiryCard
                .setOnClickListener(
                        view ->
                                openAlerts()
                );
    }



    private void showUserProfile(
            UserProfile profile)
    {
        currentUserProfile =
                profile;


        BottomNavigationPermissionHelper
                .apply(
                        bottomNavigation,
                        profile
                );


        if (profile == null
                ||
                !profile.isActive())
        {
            greetingText.setText(
                    "Welcome"
            );


            profileInitials.setText(
                    "--"
            );


            hidePermissionControlledContent();

            return;
        }


        String firstName =
                profile.getFirstName();


        if (firstName == null
                ||
                firstName.trim().isEmpty())
        {
            firstName =
                    "there";
        }


        greetingText.setText(
                getGreeting()
                        + ", "
                        + firstName
        );


        profileInitials.setText(
                buildInitials(
                        profile
                )
        );


        applyPermissions(
                profile
        );
    }



    private void applyPermissions(
            UserProfile profile)
    {
        boolean canViewMedicines =
                profile.can(
                        Permission.VIEW_MEDICINES
                );


        boolean canViewStockLevels =
                profile.can(
                        Permission.VIEW_STOCK_LEVELS
                );


        boolean canViewAlerts =
                profile.can(
                        Permission.VIEW_ALERTS
                );


        boolean canViewHistory =
                profile.can(
                        Permission.VIEW_INVENTORY_HISTORY
                );


        setVisible(
                searchButton,
                canViewMedicines
        );


        setVisible(
                totalMedicinesCard,
                canViewMedicines
        );


        setVisible(
                lowStockCard,
                canViewStockLevels
        );


        setVisible(
                stockOverviewSection,
                canViewStockLevels
                        ||
                        canViewAlerts
        );


        setVisible(
                viewAllStock,
                canViewMedicines
        );


        stockLowText.setVisibility(
                canViewStockLevels
                        ? View.VISIBLE
                        : View.GONE
        );


        setVisible(
                stockNearExpiryRow,
                canViewAlerts
        );


        setVisible(
                stockExpiredRow,
                canViewAlerts
        );


        setVisible(
                alertsButton,
                canViewAlerts
        );


        setVisible(
                nearExpiryCard,
                canViewAlerts
        );


        setVisible(
                activeAlertsCard,
                canViewAlerts
        );


        setVisible(
                criticalExpirySection,
                canViewAlerts
        );


        setVisible(
                recentMovementSection,
                canViewHistory
        );


        setVisible(
                pendingSyncCard,
                true
        );


        setVisible(
                syncAttentionCard,
                true
        );


        setVisible(
                syncFreshnessCard,
                true
        );


        renderRecentMovements(
                latestMovements
        );
    }


    private void hidePermissionControlledContent()
    {
        setVisible(
                searchButton,
                false
        );


        setVisible(
                alertsButton,
                false
        );


        setVisible(
                totalMedicinesCard,
                false
        );


        setVisible(
                lowStockCard,
                false
        );


        setVisible(
                nearExpiryCard,
                false
        );


        setVisible(
                activeAlertsCard,
                false
        );


        setVisible(
                pendingSyncCard,
                false
        );


        setVisible(
                syncAttentionCard,
                false
        );


        setVisible(
                syncFreshnessCard,
                false
        );


        setVisible(
                stockOverviewSection,
                false
        );


        setVisible(
                criticalExpirySection,
                false
        );


        setVisible(
                recentMovementSection,
                false
        );
    }



    private void renderStockOverview()
    {
        stockLowText.setText(
                latestLowStockCount
                        + (
                        latestLowStockCount == 1
                                ? " medicine requires reorder"
                                : " medicines require reorder"
                )
        );


        stockNearExpiryText.setText(
                latestNearExpiryCount
                        + (
                        latestNearExpiryCount == 1
                                ? " near-expiry alert"
                                : " near-expiry alerts"
                )
        );


        stockExpiredText.setText(
                latestExpiredCount
                        + (
                        latestExpiredCount == 1
                                ? " expired-stock alert"
                                : " expired-stock alerts"
                )
        );
    }



    private void renderCriticalExpiry()
    {
        if (latestExpiredCount > 0)
        {
            criticalExpiryTitle.setText(
                    latestExpiredCount
                            + (
                            latestExpiredCount == 1
                                    ? " expired batch requires attention"
                                    : " expired batches require attention"
                    )
            );


            if (latestNearExpiryCount > 0)
            {
                criticalExpiryDescription.setText(
                        latestNearExpiryCount
                                + (
                                latestNearExpiryCount == 1
                                        ? " additional batch is near expiry."
                                        : " additional batches are near expiry."
                        )
                                + " Open Alerts to review affected inventory."
                );
            }
            else
            {
                criticalExpiryDescription.setText(
                        "Expired stock remains open in Alerts and should be reviewed."
                );
            }


            return;
        }


        if (latestNearExpiryCount > 0)
        {
            criticalExpiryTitle.setText(
                    latestNearExpiryCount
                            + (
                            latestNearExpiryCount == 1
                                    ? " batch is near expiry"
                                    : " batches are near expiry"
                    )
            );


            criticalExpiryDescription.setText(
                    "Review these batches before their expiry dates."
            );


            return;
        }


        criticalExpiryTitle.setText(
                "No critical expiry alerts"
        );


        criticalExpiryDescription.setText(
                "There are currently no unresolved near-expiry or expired-stock alerts."
        );
    }



    private void renderLastSync(
            Long timestamp)
    {
        if (timestamp == null
                ||
                timestamp <= 0L)
        {
            lastSyncText.setText(
                    "No successful inventory sync recorded yet"
            );

            return;
        }


        CharSequence relative =
                DateUtils
                        .getRelativeTimeSpanString(
                                timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                        );


        lastSyncText.setText(
                "Last synced "
                        + relative
        );
    }



    private void renderRecentMovements(
            List<MovementHistoryRecord> movements)
    {
        recentMovementContainer
                .removeAllViews();


        if (movements == null
                ||
                movements.isEmpty())
        {
            recentMovementEmpty.setVisibility(
                    View.VISIBLE
            );

            return;
        }


        recentMovementEmpty.setVisibility(
                View.GONE
        );


        LayoutInflater inflater =
                LayoutInflater.from(
                        this
                );


        boolean canOpenMedicine =
                currentUserProfile != null
                        &&
                        currentUserProfile.isActive()
                        &&
                        currentUserProfile.can(
                                Permission.VIEW_MEDICINES
                        );


        for (MovementHistoryRecord record :
                movements)
        {
            if (record == null)
            {
                continue;
            }


            View item =
                    inflater.inflate(
                            R.layout.item_dashboard_movement,
                            recentMovementContainer,
                            false
                    );


            TextView medicineText =
                    item.findViewById(
                            R.id.dashboardMovementMedicine
                    );


            TextView summaryText =
                    item.findViewById(
                            R.id.dashboardMovementSummary
                    );


            TextView metaText =
                    item.findViewById(
                            R.id.dashboardMovementMeta
                    );


            medicineText.setText(
                    movementMedicineName(
                            record
                    )
            );


            summaryText.setText(
                    movementSummary(
                            record
                    )
            );


            metaText.setText(
                    movementMeta(
                            record
                    )
            );


            boolean hasMedicineId =
                    record.medicineId != null
                            &&
                            !record.medicineId
                                    .trim()
                                    .isEmpty();


            boolean clickable =
                    canOpenMedicine
                            &&
                            hasMedicineId;


            item.setClickable(
                    clickable
            );


            item.setFocusable(
                    clickable
            );


            if (clickable)
            {
                item.setOnClickListener(
                        view ->
                                openMedicine(
                                        record.medicineId
                                )
                );
            }


            recentMovementContainer
                    .addView(
                            item
                    );
        }


        if (recentMovementContainer
                .getChildCount() == 0)
        {
            recentMovementEmpty.setVisibility(
                    View.VISIBLE
            );
        }
    }



    private String movementMedicineName(
            MovementHistoryRecord record)
    {
        if (record.medicineName != null
                &&
                !record.medicineName
                        .trim()
                        .isEmpty())
        {
            return record.medicineName
                    .trim();
        }


        if (record.medicineCode != null
                &&
                !record.medicineCode
                        .trim()
                        .isEmpty())
        {
            return record.medicineCode
                    .trim();
        }


        return "Medicine";
    }



    private String movementSummary(
            MovementHistoryRecord record)
    {
        String type =
                displayMovementType(
                        record.transactionType
                );


        String quantity =
                record.quantityChange > 0
                        ? "+"
                          + record.quantityChange
                        : String.valueOf(
                        record.quantityChange
                );


        return type
                + "  •  "
                + quantity;
    }



    private String movementMeta(
            MovementHistoryRecord record)
    {
        StringBuilder result =
                new StringBuilder();


        if (record.batchNumber != null
                &&
                !record.batchNumber
                        .trim()
                        .isEmpty())
        {
            result.append(
                    "Batch "
            );


            result.append(
                    record.batchNumber
                            .trim()
            );
        }


        long timestamp =
                record.serverCreatedAt > 0L
                        ? record.serverCreatedAt
                        : record.clientCreatedAt;


        if (timestamp > 0L)
        {
            appendMetaSeparator(
                    result
            );


            result.append(
                    DateUtils
                            .getRelativeTimeSpanString(
                                    timestamp,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_RELATIVE
                            )
            );
        }


        appendMetaSeparator(
                result
        );


        result.append(
                movementState(
                        record
                )
        );


        return result.toString();
    }



    private String movementState(
            MovementHistoryRecord record)
    {
        if (record.provisional)
        {
            return "Pending";
        }


        if (record.operationStatus == null)
        {
            return "Recorded";
        }


        String status =
                record.operationStatus
                        .trim()
                        .toUpperCase(Locale.ROOT);


        switch (status)
        {
            case "LOCAL_PENDING":
                return "Pending";

            case "SUBMITTED":
                return "Awaiting confirmation";

            case "CONFLICT":
                return "Conflict";

            case "REJECTED":
                return "Rejected";

            case "CONFIRMED":
                return "Confirmed";

            default:
                return displayMovementType(
                        status
                );
        }
    }



    private String displayMovementType(
            String value)
    {
        if (value == null
                ||
                value.trim().isEmpty())
        {
            return "Movement";
        }


        String normalized =
                value
                        .trim()
                        .replace(
                                '_',
                                ' '
                        )
                        .toLowerCase(Locale.ROOT);


        StringBuilder result =
                new StringBuilder();


        boolean capitalize =
                true;


        for (int index = 0;
             index < normalized.length();
             index++)
        {
            char character =
                    normalized.charAt(
                            index
                    );


            if (capitalize
                    &&
                    Character.isLetter(
                            character
                    ))
            {
                result.append(
                        Character.toUpperCase(
                                character
                        )
                );


                capitalize =
                        false;
            }
            else
            {
                result.append(
                        character
                );
            }


            if (Character.isWhitespace(
                    character
            ))
            {
                capitalize =
                        true;
            }
        }


        return result.toString();
    }



    private void appendMetaSeparator(
            StringBuilder builder)
    {
        if (builder.length() > 0)
        {
            builder.append(
                    "  •  "
            );
        }
    }



    private void openInventory()
    {
        startActivity(
                new Intent(
                        this,
                        InventoryActivity.class
                )
        );
    }



    private void openAlerts()
    {
        startActivity(
                new Intent(
                        this,
                        AlertsActivity.class
                )
        );
    }



    private void openSyncStatus()
    {
        startActivity(
                new Intent(
                        this,
                        SyncStatusActivity.class
                )
        );
    }



    private void openMedicine(
            String medicineId)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            return;
        }


        Intent intent =
                new Intent(
                        this,
                        MedicineDetailsActivity.class
                );


        intent.putExtra(
                MedicineDetailsActivity
                        .EXTRA_MEDICINE_ID,
                medicineId.trim()
        );


        startActivity(
                intent
        );
    }



    private int safeCount(
            Integer value)
    {
        return value == null
                ? 0
                : Math.max(
                0,
                value
        );
    }


    private void setVisible(
            View view,
            boolean visible)
    {
        view.setVisibility(
                visible
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    private String getGreeting()
    {
        int hour =
                java.util.Calendar
                        .getInstance()
                        .get(
                                java.util.Calendar
                                        .HOUR_OF_DAY
                        );


        if (hour < 12)
        {
            return "Good morning";
        }


        if (hour < 18)
        {
            return "Good afternoon";
        }


        return "Good evening";
    }



    private String buildInitials(
            UserProfile profile)
    {
        StringBuilder initials =
                new StringBuilder();


        if (profile.getFirstName() != null
                &&
                !profile
                        .getFirstName()
                        .trim()
                        .isEmpty())
        {
            initials.append(
                    profile
                            .getFirstName()
                            .trim()
                            .charAt(
                                    0
                            )
            );
        }


        if (profile.getLastName() != null
                &&
                !profile
                        .getLastName()
                        .trim()
                        .isEmpty())
        {
            initials.append(
                    profile
                            .getLastName()
                            .trim()
                            .charAt(
                                    0
                            )
            );
        }


        if (initials.length() == 0)
        {
            return "--";
        }


        return initials
                .toString()
                .toUpperCase(Locale.getDefault());
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


        bottomNavigation.setSelectedItemId(
                R.id.nav_dashboard
        );


        bottomNavigation
                .setOnItemSelectedListener(
                        item ->
                        {
                            int itemId =
                                    item.getItemId();


                            if (itemId
                                    == R.id.nav_dashboard)
                            {
                                return true;
                            }


                            if (itemId
                                    == R.id.nav_inventory)
                            {
                                openInventory();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_alerts)
                            {
                                openAlerts();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_reports)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                ReportsActivity.class
                                        )
                                );


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