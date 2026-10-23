package com.medicationinventory.notifications;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;


/**
 * Enqueues network-constrained WorkManager passes for alert notification delivery. Alert changes
 * request unique one-time work with KEEP semantics so bursts share an existing run, while a daily
 * periodic request provides a safety check when a change-triggered enqueue was missed.
 */
public final class InventoryAlertNotificationScheduler {

    private static final String ON_CHANGE_WORK = "mediflow_notify_changed_alerts_v1";
    private static final String PERIODIC_WORK = "mediflow_notification_safety_v1";
    private final Context context;


    public InventoryAlertNotificationScheduler(Context context) {
        this.context = context.getApplicationContext();
    }


    private Constraints onlineConstraint() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }


    /** Requests one connected-network dispatch after alert data has changed; duplicate enqueues coalesce. */
    public void scheduleAfterAlertChange() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                InventoryAlertNotificationWorker.class)
                .setConstraints(onlineConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                ON_CHANGE_WORK, ExistingWorkPolicy.KEEP, request);
    }


    /** Registers a once-daily connected-network refresh without replacing an existing periodic schedule. */
    public void schedulePeriodicSafetyCheck() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                InventoryAlertNotificationWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(onlineConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
