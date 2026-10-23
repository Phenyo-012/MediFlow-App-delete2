package com.medicationinventory.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;


/**
 * Schedules the unique WorkManager job that uploads pending inventory operations and processes submitted work. Sign-in and app startup can request the same unique work, allowing WorkManager to apply the configured conflict policy instead of creating parallel sync workers.
 */
public class InventorySyncScheduler {

    private static final String UNIQUE_WORK_NAME =
            "inventory_pending_sync";

    private static final String WORK_TAG =
            "inventory_sync";

    private final Context appContext;


    public InventorySyncScheduler(
            Context context
    ) {

        appContext =
                context.getApplicationContext();
    }


    public void schedulePendingSync() {
        enqueue(ExistingWorkPolicy.KEEP);
    }


    public void scheduleAfterSignIn() {
        enqueue(ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    private void enqueue(ExistingWorkPolicy policy) {

        Constraints constraints =
                new Constraints.Builder()
                        .setRequiredNetworkType(
                                NetworkType.CONNECTED
                        )
                        .build();

        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(
                        InventorySyncWorker.class
                )
                        .setConstraints(
                                constraints
                        )
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                30,
                                TimeUnit.SECONDS
                        )
                        .addTag(
                                WORK_TAG
                        )
                        .build();

        WorkManager
                .getInstance(
                        appContext
                )
                .enqueueUniqueWork(
                        UNIQUE_WORK_NAME,
                        policy,
                        request
                );
    }
}
