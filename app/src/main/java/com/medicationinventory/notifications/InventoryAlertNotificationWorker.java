package com.medicationinventory.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.data.local.entity.AlertEntity;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;


/**
 * Dispatches locally reconciled active alerts as private Android notifications. Before reading Room,
 * the worker checks the signed-in Firebase UID, the user's master notification preference, Android
 * notification permission, and a server-fetched active profile with {@code VIEW_ALERTS}. It then
 * cancels deliveries for resolved alerts, applies per-type preferences and one-time delivery
 * history, and caps a run to avoid flooding the notification shade. Profile/network failures are
 * retried up to the configured attempt limit; account changes stop the current user's dispatch.
 */
public final class InventoryAlertNotificationWorker extends Worker {

    private static final int MAX_NOTIFICATIONS_PER_RUN = 12;


    public InventoryAlertNotificationWorker(@NonNull Context context,
                                            @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * Performs one notification pass. A signed-out user cancels the prior owner's visible history;
     * a changed UID is checked again after the server profile fetch and during dispatch so a run
     * cannot continue sending under the account that was previously active.
     *
     * @return WorkManager success, retry, or failure according to the operation stage
     */
    @NonNull
    @Override

    public Result doWork() {
        Context context = getApplicationContext();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            InventoryAlertNotificationHistory.syncOwner(context, null);
            return Result.success();
        }
        String uid = user.getUid();
        InventoryAlertNotificationHistory.syncOwner(context, uid);

        InventoryAlertNotificationPreferences preferences =
                new InventoryAlertNotificationPreferences(context);
        if (!preferences.isMasterEnabled(uid)) {
            InventoryAlertNotificationHistory.cancelVisible(context, uid);
            return Result.success();
        }

        if (!InventoryNotificationPermission.canPost(context)) {
            return Result.success();
        }

        final DocumentSnapshot currentProfile;
        try {

            currentProfile = Tasks.await(
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .get(Source.SERVER),
                    20, TimeUnit.SECONDS);
        } catch (Exception exception) {

            return getRunAttemptCount() < 5 ? Result.retry() : Result.success();
        }

        if (!isCurrentUid(auth, uid)) {
            InventoryAlertNotificationHistory.syncOwner(context, currentUid(auth));
            return Result.success();
        }

        if (!hasCurrentAlertAccess(currentProfile, uid)) {
            InventoryAlertNotificationHistory.cancelVisible(context, uid);
            return Result.success();
        }

        try {
            MedicationInventoryApplication app =
                    (MedicationInventoryApplication) context;
            List<AlertEntity> activeAlerts = app.getAppContainer()
                    .getAppDatabase().alertDao().getActiveForNotificationDispatch();

            Set<String> stillActive = new HashSet<>();
            if (activeAlerts != null) {
                for (AlertEntity alert : activeAlerts) {
                    if (alert != null && alert.getAlertId() != null
                            && alert.getAlertType() != null
                            && preferences.allows(uid, alert.getAlertType().name())) {
                        stillActive.add(alert.getAlertId());
                    }
                }
            }
            InventoryAlertNotificationHistory.cancelNoLongerActive(
                    context, uid, stillActive);
            if (activeAlerts == null || activeAlerts.isEmpty()) {
                return Result.success();
            }
            int posted = 0;
            for (AlertEntity alert : activeAlerts) {
                if (isStopped()) break;
                if (!isCurrentUid(auth, uid)) {
                    InventoryAlertNotificationHistory.syncOwner(context, currentUid(auth));
                    break;
                }
                if (posted >= MAX_NOTIFICATIONS_PER_RUN) break;
                if (alert == null) continue;

                String id = alert.getAlertId();
                if (id == null || id.trim().isEmpty()) continue;

                if (!preferences.allows(uid, alert.getAlertType().name())) continue;
                boolean sent = InventoryAlertNotificationHistory.wasDelivered(context, uid, id);
                if (!InventoryAlertNotificationPolicy.shouldNotify(
                        alert.getStatus().name(), alert.getAlertType().name(), sent)) continue;

                if (InventoryNotificationPublisher.sendAlert(context, uid, id,
                        alert.getAlertType().name(), alert.getMessage())) {
                    InventoryAlertNotificationHistory.markDelivered(context, uid, id);
                    posted++;
                }
            }
            return Result.success();
        } catch (Exception exception) {
            return getRunAttemptCount() < 5 ? Result.retry() : Result.failure();
        }
    }


    /**
     * Applies the same active-profile, UID-consistency, and VIEW_ALERTS policy to the server
     * document that gates access to the alert list. Cached Android permissions are not used here.
     */
    private static boolean hasCurrentAlertAccess(DocumentSnapshot snapshot, String uid) {
        if (snapshot == null || !snapshot.exists()) return false;
        return InventoryAlertNotificationPolicy.isAuthorized(
                snapshot.getBoolean("isActive"), snapshot.getString("uid"),
                uid, snapshot.get("permissions"));
    }


    private static String currentUid(FirebaseAuth auth) {
        FirebaseUser current = auth.getCurrentUser();
        return current == null ? null : current.getUid();
    }

    private static boolean isCurrentUid(FirebaseAuth auth, String uid) {
        return uid != null && uid.equals(currentUid(auth));
    }
}
