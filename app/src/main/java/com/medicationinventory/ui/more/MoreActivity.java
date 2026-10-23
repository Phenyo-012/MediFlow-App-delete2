package com.medicationinventory.ui.more;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.medicationinventory.ui.BottomNavigationPermissionHelper;
import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.account.AccountSecurityActivity;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.alerts.AlertsActivity;
import com.medicationinventory.ui.audit.AuditLogActivity;
import com.medicationinventory.ui.dashboard.DashboardActivity;
import com.medicationinventory.ui.inventory.InventoryActivity;
import com.medicationinventory.ui.reports.ReportsActivity;
import com.medicationinventory.ui.sync.SyncStatusActivity;
import com.medicationinventory.ui.settings.SettingsActivity;
import com.medicationinventory.ui.users.UserManagementActivity;


/**
 * Provides the secondary navigation hub for account, settings, administration, audit, and sync
 * screens. Profile permissions control which destination cards are shown; logout routes through
 * the authentication repository and clears the current session before returning to login. The
 * visibility checks improve navigation but protected activities and backend services enforce access
 * independently.
 */
public class MoreActivity
        extends AppCompatActivity {
    private View auditCard;

    private View userManagementCard;

    private View settingsCard;

    private BottomNavigationView bottomNavigation;

    // Retained only while this screen is active; never store credentials here.
    private AuthenticationRepository authenticationRepository;


    @Override

    protected void onCreate(
            Bundle savedInstanceState) {
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


        if (!authenticationRepository.isSignedIn()) {
            goToLogin();
            return;
        }


        setContentView(
                R.layout.activity_more
        );


        /*
         * AUDIT LOG
         */
        auditCard =
                findViewById(
                        R.id.moreAuditCard
                );


        /*
         * Hidden until the signed-in user's local profile
         * confirms VIEW_AUDIT.
         */
        auditCard.setVisibility(
                View.GONE
        );


        auditCard.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        AuditLogActivity.class
                                )
                        )
        );


        /*
         * SYNCHRONISATION STATUS
         */
        View syncCard =
                findViewById(
                        R.id.moreSyncCard
                );


        syncCard.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        SyncStatusActivity.class
                                )
                        )
        );


        /*
         * USER MANAGEMENT
         */
        userManagementCard =
                findViewById(
                        R.id.moreUserManagementCard
                );


        /*
         * Hidden until VIEW_USERS has been confirmed.
         */
        userManagementCard.setVisibility(
                View.GONE
        );


        userManagementCard.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        UserManagementActivity.class
                                )
                        )
        );

        /*
         * SETTINGS
         */
        settingsCard =
                findViewById(
                        R.id.moreSettingsCard
                );


        settingsCard.setVisibility(
                View.GONE
        );


        settingsCard.setOnClickListener(
                view ->
                        startActivity(
                                new Intent(
                                        this,
                                        SettingsActivity.class
                                )
                        )
        );

        /*
         * CURRENT USER PERMISSIONS
         */
        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid != null) {
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
                            this::renderPermissions
                    );
        }


        // Personal security is available to every signed-in active user;
        // it is not gated by MANAGE_SETTINGS.
        findViewById(R.id.moreAccountSecurityCard).setOnClickListener(view ->
                startActivity(new Intent(this, AccountSecurityActivity.class)));

        installLogoutCard();

        setupBottomNavigation();
    }


    private void renderPermissions(
            UserProfile profile) {
        /*
         * If the profile is unavailable or inactive,
         * permission-controlled administrative options
         * remain hidden.
         */
        if (profile == null
                ||
                !profile.isActive()) {
            auditCard.setVisibility(
                    View.GONE
            );


            userManagementCard.setVisibility(
                    View.GONE
            );

            settingsCard.setVisibility(
                    View.GONE
            );


            return;
        }


        /*
         * Audit Log visibility
         */
        boolean canViewAudit =
                profile.can(
                        Permission.VIEW_AUDIT
                );


        auditCard.setVisibility(
                canViewAudit
                        ? View.VISIBLE
                        : View.GONE
        );


        /*
         * User Management visibility
         */
        boolean canViewUsers =
                profile.can(
                        Permission.VIEW_USERS
                );


        userManagementCard.setVisibility(
                canViewUsers
                        ? View.VISIBLE
                        : View.GONE
        );

        /*
         * Settings visibility
         */
        boolean canManageSettings =
                profile.can(
                        Permission.MANAGE_SETTINGS
                );


        settingsCard.setVisibility(
                canManageSettings
                        ? View.VISIBLE
                        : View.GONE
        );
    }



    private void installLogoutCard() {
        View anchor = settingsCard != null ? settingsCard
                : findViewById(R.id.moreSyncCard);

        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) {
            // Fail visibly in development rather than silently omitting logout.
            throw new IllegalStateException(
                    "More screen needs a vertical card container for Log out.");
        }

        LinearLayout container = (LinearLayout) anchor.getParent();
        MaterialCardView logoutCard = new MaterialCardView(this);
        logoutCard.setId(View.generateViewId());
        logoutCard.setCardBackgroundColor(Color.WHITE);
        logoutCard.setRadius(dp(12));
        logoutCard.setCardElevation(dp(1));
        logoutCard.setStrokeColor(ColorStateList.valueOf(
                Color.rgb(244, 204, 204)));
        logoutCard.setStrokeWidth(dp(1));
        logoutCard.setClickable(true);
        logoutCard.setFocusable(true);
        logoutCard.setContentDescription("Log out of MediFlow");

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("Log out");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(173, 43, 43));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView description = new TextView(this);
        description.setText("Sign out of this account on this device");
        description.setTextSize(13);
        description.setTextColor(Color.rgb(115, 128, 150));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        content.addView(description, detailParams);

        logoutCard.addView(content);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(16);

        int anchorIndex = container.indexOfChild(anchor);
        container.addView(logoutCard, anchorIndex + 1, cardParams);
        logoutCard.setOnClickListener(view -> confirmLogout());
        ensureMoreContentScrolls(container);
    }

    private void ensureMoreContentScrolls(LinearLayout container) {
        if (container.getParent() instanceof android.widget.ScrollView
                || container.getParent() instanceof NestedScrollView) {
            return;
        }
        if (!(container.getParent() instanceof ViewGroup)) {
            return;
        }
        ViewGroup parent = (ViewGroup) container.getParent();
        int index = parent.indexOfChild(container);
        ViewGroup.LayoutParams oldParams = container.getLayoutParams();
        parent.removeView(container);

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        parent.addView(scroll, index, oldParams);
        scroll.addView(container, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out of MediFlow?")
                .setMessage("You will return to the login screen. "
                        + "Pending stock operations remain stored for synchronisation "
                        + "when an authorised user signs in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> performLogout())
                .show();
    }

    /**
     * Carries out logout and keeps its validation, state changes and completion handling together.
     */
    private void performLogout() {

        authenticationRepository.signOut();

        try {
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancelAll();
            }
        } catch (RuntimeException ignored) {
            // A notification service failure must not prevent logout.
        }

        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override

    protected void onResume() {
        super.onResume();
        if (authenticationRepository != null
                && !authenticationRepository.isSignedIn()) {
            goToLogin();
        }
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
                        R.id.nav_more
                );


        bottomNavigation
                .setOnItemSelectedListener(
                        item ->
                        {
                            int itemId =
                                    item.getItemId();


                            /*
                             * Already on More.
                             */
                            if (itemId == R.id.nav_more)
                            {
                                return true;
                            }


                            /*
                             * Dashboard
                             */
                            if (itemId == R.id.nav_dashboard)
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


                            /*
                             * Inventory
                             */
                            if (itemId == R.id.nav_inventory) {
                                startActivity(
                                        new Intent(
                                                this,
                                                InventoryActivity.class
                                        )
                                );


                                finish();


                                return true;
                            }


                            /*
                             * Alerts
                             */
                            if (itemId == R.id.nav_alerts) {
                                startActivity(
                                        new Intent(
                                                this,
                                                AlertsActivity.class
                                        )
                                );

                                finish();

                                return true;
                            }


                            /*
                             * Reports
                             */
                            if (itemId == R.id.nav_reports) {
                                startActivity(
                                        new Intent(
                                                this,
                                                ReportsActivity.class
                                        )
                                );


                                finish();


                                return true;
                            }


                            return false;
                        }
                );
    }
}
