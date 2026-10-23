package com.medicationinventory.notifications;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.data.local.entity.AlertEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Applies a user's notification preference changes to visible alerts and schedules a fresh dispatch.
 * It verifies that the requested UID is still the Firebase account before doing work, cancels all
 * visible alerts when the master switch is disabled, and otherwise reads Room alerts asynchronously
 * to cancel deliveries that are no longer active or enabled.
 */
public final class InventoryAlertNotificationPreferenceCoordinator {

    private InventoryAlertNotificationPreferenceCoordinator() { }


    public static void apply(Context context, String uid) {
        if (uid == null || uid.trim().isEmpty()) return;
        Context appContext = context.getApplicationContext();
        if (!isCurrentUser(uid)) return;
        InventoryAlertNotificationPreferences preferences =
                new InventoryAlertNotificationPreferences(appContext);

        if (!preferences.isMasterEnabled(uid)) {

            InventoryAlertNotificationHistory.cancelVisible(appContext, uid);
            return;
        }

        MedicationInventoryApplication app =
                (MedicationInventoryApplication) appContext;
        app.getAppContainer().getAppExecutors().databaseIO().execute(() -> {
            if (!isCurrentUser(uid)) return;
            List<AlertEntity> activeAlerts = app.getAppContainer()
                    .getAppDatabase().alertDao().getActiveForNotificationDispatch();
            Set<String> allowedVisible = new HashSet<>();
            if (activeAlerts != null) {
                for (AlertEntity alert : activeAlerts) {
                    if (alert != null && alert.getAlertId() != null
                            && alert.getAlertType() != null
                            && preferences.allows(uid, alert.getAlertType().name())) {
                        allowedVisible.add(alert.getAlertId());
                    }
                }
            }

            InventoryAlertNotificationHistory.cancelNoLongerActive(
                    appContext, uid, allowedVisible);
        });

        new InventoryAlertNotificationScheduler(appContext).scheduleAfterAlertChange();
    }

    private static boolean isCurrentUser(String uid) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && uid.equals(user.getUid());
    }
}
