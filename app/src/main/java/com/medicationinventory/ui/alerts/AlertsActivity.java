package com.medicationinventory.ui.alerts;


import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.snackbar.Snackbar;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;
import com.medicationinventory.notifications.InventoryNotificationPermission;
import com.medicationinventory.notifications.InventoryNotificationPublisher;
import com.medicationinventory.notifications.InventoryAlertNotificationPreferences;
import com.medicationinventory.notifications.InventoryAlertNotificationPreferenceCoordinator;

import com.medicationinventory.data.local.model.AlertListRecord;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.BottomNavigationPermissionHelper;
import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.dashboard.DashboardActivity;
import com.medicationinventory.ui.inventory.InventoryActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;
import com.medicationinventory.ui.more.MoreActivity;
import com.medicationinventory.ui.reports.ReportsActivity;

import java.util.List;



/**
 * Renders permission-gated alert rows, type filters, acknowledgement actions, and navigation. The
 * ViewModel supplies locally reconciled alerts, while this activity separately manages the user's
 * device-notification preferences and Android runtime permission. It requests notification
 * permission only after an explicit enable action and shows a generic test preview after the
 * platform reports that posting is available.
 */
public class AlertsActivity
        extends AppCompatActivity
{
    private RecyclerView alertsRecyclerView;

    private View alertsEmptyState;

    private MaterialButtonToggleGroup
            alertFilterGroup;

    private AlertAdapter alertAdapter;

    private AlertsViewModel viewModel;

    private AuthenticationRepository
            authenticationRepository;

    private BottomNavigationView
            bottomNavigation;

    private boolean canViewAlerts;
    private MaterialButton notificationPreferencesButton;
    private TextView notificationPreferencesStatus;
    private BottomSheetDialog notificationPreferencesDialog;

    // Permission requests only follow an explicit Enable tap.
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!canViewAlerts) {
                            return;
                        }
                        dismissNotificationNudge();
                        if (granted && InventoryNotificationPermission.canPost(this)) {
                            showPreviewAction();
                        } else {
                            showSettingsAction();
                        }
                    }
            );

    private boolean returnedFromNotificationSettings;
    private boolean nudgeDismissedThisVisit;
    private Snackbar notificationNudge;


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


        if (!authenticationRepository.isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_alerts
        );


        bindViews();

        setupList();


        setupViewModel(
                application
        );


        setupFilters();
        setupNotificationPreferences();

        setupBottomNavigation();

        observeData();
    }



    @Override

    protected void onResume() {
        super.onResume();

        if (canViewAlerts) updateNotificationPreferencesStatus();
        if (returnedFromNotificationSettings) {
            returnedFromNotificationSettings = false;
            if (canViewAlerts) {
                dismissNotificationNudge();
                if (InventoryNotificationPermission.canPost(this)) {
                    showPreviewAction();
                } else {
                    showSettingsAction();
                }
            }
        }
    }

    @Override

    protected void onDestroy() {
        dismissNotificationNudge();
        if (notificationPreferencesDialog != null) {
            notificationPreferencesDialog.dismiss();
            notificationPreferencesDialog = null;
        }
        super.onDestroy();
    }


    private void bindViews()
    {
        alertsRecyclerView =
                findViewById(
                        R.id.alertsRecyclerView
                );


        alertsEmptyState =
                findViewById(
                        R.id.alertsEmptyState
                );


        alertFilterGroup =
                findViewById(
                        R.id.alertFilterGroup
                );
        notificationPreferencesButton = findViewById(R.id.notificationPreferencesButton);
        notificationPreferencesStatus = findViewById(R.id.notificationPreferencesStatus);
    }



    private void setupList()
    {
        alertAdapter =
                new AlertAdapter(
                        this::openAlert,
                        this::acknowledgeAlert
                );


        alertsRecyclerView
                .setLayoutManager(
                        new LinearLayoutManager(
                                this
                        )
                );


        alertsRecyclerView
                .setAdapter(
                        alertAdapter
                );
    }


    private void acknowledgeAlert(
            AlertListRecord alert)
    {
        if (alert == null
                ||
                alert.alertId == null)
        {
            return;
        }


        viewModel
                .acknowledgeAlert(
                        alert.alertId
                );
    }


    private void handleActionState(
            AlertsViewModel.ActionState state)
    {
        if (state == null)
        {
            return;
        }


        switch (state.getStatus())
        {
            case LOADING:

                alertAdapter
                        .setAcknowledgementInProgress(
                                true
                        );

                break;


            case SUCCESS:

                alertAdapter
                        .setAcknowledgementInProgress(
                                false
                        );


                Toast.makeText(
                        this,
                        "Alert acknowledged.",
                        Toast.LENGTH_SHORT
                ).show();


                viewModel
                        .markActionHandled();

                break;


            case ERROR:

                alertAdapter
                        .setAcknowledgementInProgress(
                                false
                        );


                Toast.makeText(
                        this,
                        state.getMessage(),
                        Toast.LENGTH_SHORT
                ).show();


                viewModel
                        .markActionHandled();

                break;


            case IDLE:
            default:

                alertAdapter
                        .setAcknowledgementInProgress(
                                false
                        );

                break;
        }
    }


    private void setupViewModel(
            MedicationInventoryApplication application)
    {
        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        AlertRepository alertRepository =
                application
                        .getAppContainer()
                        .getAlertRepository();


        AlertsViewModelFactory factory =
                new AlertsViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        alertRepository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                AlertsViewModel.class
                        );
    }


    private void setupFilters()
    {
        alertFilterGroup
                .addOnButtonCheckedListener(
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
                                    == R.id.filterLowStockButton)
                            {
                                viewModel.setFilter(
                                        AlertsViewModel.Filter.LOW_STOCK
                                );
                            }
                            else if (checkedId
                                    == R.id.filterNearExpiryButton)
                            {
                                viewModel.setFilter(
                                        AlertsViewModel.Filter.NEAR_EXPIRY
                                );
                            }
                            else if (checkedId
                                    == R.id.filterExpiredButton)
                            {
                                viewModel.setFilter(
                                        AlertsViewModel.Filter.EXPIRED
                                );
                            }
                            else
                            {
                                viewModel.setFilter(
                                        AlertsViewModel.Filter.ALL
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
                .getVisibleAlerts()
                .observe(
                        this,
                        this::showAlerts
                );


        viewModel
                .getActionState()
                .observe(
                        this,
                        this::handleActionState
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


        if (profile == null) {
            canViewAlerts = false;
            closeNotificationPreferences();
            dismissNotificationNudge();
            return;
        }

        if (!profile.isActive()) {
            canViewAlerts = false;
            closeNotificationPreferences();
            dismissNotificationNudge();
            goToLogin();
            return;
        }

        if (!profile.can(
                Permission.VIEW_ALERTS
        ))
        {
            canViewAlerts = false;
            closeNotificationPreferences();
            dismissNotificationNudge();
            Toast.makeText(
                    this,
                    "You do not have permission to view alerts.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }



        canViewAlerts = true;
        notificationPreferencesButton.setVisibility(View.VISIBLE);
        notificationPreferencesStatus.setVisibility(View.VISIBLE);
        updateNotificationPreferencesStatus();
        maybeShowNotificationNudge();

        updateListVisibility(
                alertAdapter == null
                        ? 0
                        : alertAdapter
                        .getItemCount()
        );


        alertAdapter
                .setCanAcknowledge(
                        profile.can(
                                Permission.ACKNOWLEDGE_ALERTS
                        )
                );
    }


    private void maybeShowNotificationNudge() {
        String uid = currentNotificationUser();
        if (!canViewAlerts || uid == null || nudgeDismissedThisVisit ||
                InventoryNotificationPermission.canPost(this) ||
                !new InventoryAlertNotificationPreferences(this).isMasterEnabled(uid) ||
                new InventoryAlertNotificationPreferences(this).enabledTypeCount(uid) == 0) {
            dismissNotificationNudge();
            return;
        }
        if (notificationNudge != null && notificationNudge.isShown()) {
            return;
        }

        notificationNudge = Snackbar.make(
                        bottomNavigation,
                        "Device alerts are off. Enable notifications for upcoming stock reminders.",
                        Snackbar.LENGTH_INDEFINITE
                ).setAnchorView(bottomNavigation)
                .setAction("Enable", view -> enableDeviceNotifications());
        notificationNudge.show();
    }

    /** Requests Android's runtime permission only from the user's explicit enable action, then offers a preview or settings route. */
    private void enableDeviceNotifications() {
        nudgeDismissedThisVisit = true;
        dismissNotificationNudge();
        if (!canViewAlerts) {
            return;
        }

        if (InventoryNotificationPermission.needsRuntimePermission() &&
                !InventoryNotificationPermission.isRuntimePermissionGranted(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!InventoryNotificationPermission.canPost(this)) {
            openDeviceNotificationSettings();
        } else {
            showPreviewAction();
        }
    }

    private void openDeviceNotificationSettings() {
        returnedFromNotificationSettings = true;
        InventoryNotificationPermission.openSystemSettings(this);
    }

    private void showSettingsAction() {
        if (!canViewAlerts) {
            return;
        }
        notificationNudge = Snackbar.make(
                        bottomNavigation,
                        "Notifications are still off. You can change this in Android Settings.",
                        Snackbar.LENGTH_LONG
                ).setAnchorView(bottomNavigation)
                .setAction("Settings", view -> openDeviceNotificationSettings());
        notificationNudge.show();
    }

    private void showPreviewAction() {
        if (!canViewAlerts || !InventoryNotificationPermission.canPost(this)) {
            return;
        }
        notificationNudge = Snackbar.make(
                        bottomNavigation,
                        "Device notifications are ready. Send a preview?",
                        Snackbar.LENGTH_LONG
                ).setAnchorView(bottomNavigation)
                .setAction("Test", view -> {
                    if (canViewAlerts && InventoryNotificationPublisher.sendPreview(this)) {
                        Toast.makeText(
                                this,
                                "Test notification requested.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
        notificationNudge.show();
    }

    private void dismissNotificationNudge() {
        if (notificationNudge != null) {
            notificationNudge.dismiss();
            notificationNudge = null;
        }
    }

    private void showAlerts(
            List<AlertListRecord> alerts)
    {
        alertAdapter
                .submitList(
                        alerts
                );


        updateListVisibility(
                alerts == null
                        ? 0
                        : alerts.size()
        );
    }


    private void updateListVisibility(
            int count)
    {
        if (count <= 0)
        {
            alertsRecyclerView
                    .setVisibility(
                            View.GONE
                    );


            alertsEmptyState
                    .setVisibility(
                            View.VISIBLE
                    );
        }
        else
        {
            alertsRecyclerView
                    .setVisibility(
                            View.VISIBLE
                    );


            alertsEmptyState
                    .setVisibility(
                            View.GONE
                    );
        }
    }


    private void openAlert(
            AlertListRecord alert)
    {
        if (alert == null
                ||
                alert.medicineId == null)
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
                alert.medicineId
        );


        startActivity(
                intent
        );
    }

    private String currentNotificationUser() {
        // Use the active authentication context instead of assuming a previously loaded user is still signed in.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }


    private void setupNotificationPreferences() {
        notificationPreferencesButton.setOnClickListener(v -> showNotificationPreferences());
    }

    private void closeNotificationPreferences() {
        if (notificationPreferencesButton != null) {
            notificationPreferencesButton.setVisibility(View.GONE);
        }
        if (notificationPreferencesStatus != null) {
            notificationPreferencesStatus.setVisibility(View.GONE);
        }
        if (notificationPreferencesDialog != null) {
            notificationPreferencesDialog.dismiss();
            notificationPreferencesDialog = null;
        }
    }

    private void updateNotificationPreferencesStatus() {
        if (!canViewAlerts || notificationPreferencesStatus == null) return;
        String uid = currentNotificationUser();
        if (uid == null) {
            notificationPreferencesStatus.setText("Sign in to manage notifications");
            return;
        }
        InventoryAlertNotificationPreferences prefs =
                new InventoryAlertNotificationPreferences(this);
        if (!prefs.isMasterEnabled(uid)) {
            notificationPreferencesStatus.setText("Automatic notifications paused");
        } else if (prefs.enabledTypeCount(uid) == 0) {
            notificationPreferencesStatus.setText("All notification types switched off");
        } else if (!InventoryNotificationPermission.canPost(this)) {
            notificationPreferencesStatus.setText("Device notifications disabled");
        } else {
            int enabled = prefs.enabledTypeCount(uid);
            notificationPreferencesStatus.setText("Notifications on · "
                    + enabled + (enabled == 1 ? " alert type" : " alert types"));
        }
    }

    private void showNotificationPreferences() {
        if (!canViewAlerts) return;
        String uid = currentNotificationUser();
        if (uid == null) {
            goToLogin();
            return;
        }
        if (notificationPreferencesDialog != null) {
            notificationPreferencesDialog.dismiss();
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(
                R.layout.bottom_sheet_notification_preferences, null);
        sheet.setContentView(sheetView);
        notificationPreferencesDialog = sheet;
        sheet.setOnDismissListener(dialog -> {
            if (notificationPreferencesDialog == sheet) {
                notificationPreferencesDialog = null;
            }
            updateNotificationPreferencesStatus();
        });

        InventoryAlertNotificationPreferences prefs =
                new InventoryAlertNotificationPreferences(this);
        SwitchMaterial master = sheetView.findViewById(R.id.notificationMasterSwitch);
        SwitchMaterial low = sheetView.findViewById(R.id.notificationLowStockSwitch);
        SwitchMaterial near = sheetView.findViewById(R.id.notificationNearExpirySwitch);
        SwitchMaterial expired = sheetView.findViewById(R.id.notificationExpiredSwitch);
        TextView deviceStatus = sheetView.findViewById(R.id.notificationDeviceStatus);
        MaterialButton deviceAction = sheetView.findViewById(R.id.notificationDeviceAction);
        MaterialButton testButton = sheetView.findViewById(R.id.notificationTestButton);
        MaterialButton closeButton = sheetView.findViewById(R.id.notificationCloseButton);

        master.setChecked(prefs.isMasterEnabled(uid));
        low.setChecked(prefs.isLowStockEnabled(uid));
        near.setChecked(prefs.isNearExpiryEnabled(uid));
        expired.setChecked(prefs.isExpiredEnabled(uid));
        updateTypeSwitchEnabled(master.isChecked(), low, near, expired);
        updateDeviceStatus(deviceStatus, deviceAction, testButton);

        master.setOnCheckedChangeListener((button, enabled) -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            prefs.setMasterEnabled(uid, enabled);
            updateTypeSwitchEnabled(enabled, low, near, expired);
            onNotificationPreferenceChanged(uid);
        });
        low.setOnCheckedChangeListener((button, enabled) -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            prefs.setLowStockEnabled(uid, enabled);
            onNotificationPreferenceChanged(uid);
        });
        near.setOnCheckedChangeListener((button, enabled) -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            prefs.setNearExpiryEnabled(uid, enabled);
            onNotificationPreferenceChanged(uid);
        });
        expired.setOnCheckedChangeListener((button, enabled) -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            prefs.setExpiredEnabled(uid, enabled);
            onNotificationPreferenceChanged(uid);
        });
        deviceAction.setOnClickListener(v -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            sheet.dismiss();
            if (!InventoryNotificationPermission.canPost(this)) {
                enableDeviceNotifications();
            } else {
                openDeviceNotificationSettings();
            }
        });
        testButton.setOnClickListener(v -> {
            if (!canViewAlerts || !uid.equals(currentNotificationUser())) return;
            boolean sent = InventoryNotificationPublisher.sendPreview(this);
            Toast.makeText(this,
                    sent ? "Test notification requested." : "Enable device notifications first.",
                    Toast.LENGTH_SHORT).show();
        });
        closeButton.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
    }

    private void updateTypeSwitchEnabled(boolean enabled,
                                         SwitchMaterial low,
                                         SwitchMaterial near,
                                         SwitchMaterial expired) {
        low.setEnabled(enabled);
        near.setEnabled(enabled);
        expired.setEnabled(enabled);
    }

    private void updateDeviceStatus(TextView status, MaterialButton action,
                                    MaterialButton testButton) {
        boolean available = InventoryNotificationPermission.canPost(this);
        status.setText(available ? "Android notifications enabled" :
                "Android notifications off or blocked");
        action.setText(available ? "Android settings" : "Enable notifications");
        testButton.setEnabled(available);
    }


    private void onNotificationPreferenceChanged(String uid) {
        updateNotificationPreferencesStatus();
        dismissNotificationNudge();
        InventoryAlertNotificationPreferenceCoordinator.apply(this, uid);
        maybeShowNotificationNudge();
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
                        R.id.nav_alerts
                );


        bottomNavigation
                .setOnItemSelectedListener(
                        item ->
                        {
                            int itemId =
                                    item.getItemId();


                            if (itemId
                                    == R.id.nav_alerts)
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
                                    == R.id.nav_reports)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                ReportsActivity.class
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
