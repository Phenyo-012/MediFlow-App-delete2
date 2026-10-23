package com.medicationinventory.sync;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.concurrent.TimeUnit;



/**
 * Schedules the unique daily maintenance worker at the next business-day boundary so expired batch states and derived alerts are refreshed even when the application is not open.
 */
public class DailyInventoryMaintenanceScheduler
{
    private static final String UNIQUE_WORK_NAME =
            "daily_inventory_date_maintenance";


    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );



    private static final LocalTime TARGET_TIME =
            LocalTime.of(
                    0,
                    10
            );


    private final Context applicationContext;



    public DailyInventoryMaintenanceScheduler(
            Context context)
    {
        if (context == null)
        {
            throw new IllegalArgumentException(
                    "Context is required."
            );
        }


        applicationContext =
                context.getApplicationContext();
    }


    public void scheduleDaily()
    {
        long initialDelayMillis =
                calculateInitialDelayMillis();


        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        DailyInventoryMaintenanceWorker.class,
                        24,
                        TimeUnit.HOURS
                )
                        .setInitialDelay(
                                initialDelayMillis,
                                TimeUnit.MILLISECONDS
                        )
                        .build();


        WorkManager
                .getInstance(
                        applicationContext
                )
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }


    private long calculateInitialDelayMillis()
    {
        ZonedDateTime now =
                ZonedDateTime.now(
                        BUSINESS_ZONE
                );


        ZonedDateTime nextRun =
                now
                        .toLocalDate()
                        .atTime(
                                TARGET_TIME
                        )
                        .atZone(
                                BUSINESS_ZONE
                        );


        if (!nextRun.isAfter(
                now
        ))
        {
            nextRun =
                    nextRun.plusDays(
                            1
                    );
        }


        return Duration
                .between(
                        now,
                        nextRun
                )
                .toMillis();
    }
}