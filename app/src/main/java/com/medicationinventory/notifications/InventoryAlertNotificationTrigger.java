package com.medicationinventory.notifications;

import androidx.annotation.NonNull;
import androidx.room.InvalidationTracker;

import com.medicationinventory.data.local.database.AppDatabase;

import java.util.Set;


/**
 * Watches Room's alert table and asks WorkManager to dispatch notifications after invalidation.
 * Startup also schedules an initial pass to cover alerts that existed before the observer was
 * registered; unique work policy in the scheduler coalesces repeated database changes.
 */
public final class InventoryAlertNotificationTrigger {

    private final AppDatabase database;
    private final InventoryAlertNotificationScheduler scheduler;
    private boolean started;
    private final InvalidationTracker.Observer observer =
            new InvalidationTracker.Observer("alerts") {
                @Override

                public void onInvalidated(@NonNull Set<String> tables) {
                    scheduler.scheduleAfterAlertChange();
                }
            };


    public InventoryAlertNotificationTrigger(AppDatabase database,
                                             InventoryAlertNotificationScheduler scheduler) {
        this.database = database;
        this.scheduler = scheduler;
    }


    public synchronized void start() {
        if (started) return;
        started = true;
        database.getInvalidationTracker().addObserver(observer);
        scheduler.scheduleAfterAlertChange();
    }
}
