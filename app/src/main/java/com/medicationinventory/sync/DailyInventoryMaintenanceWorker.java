package com.medicationinventory.sync;

import android.content.Context;

import androidx.annotation.NonNull;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.medicationinventory.MedicationInventoryApplication;

import com.medicationinventory.service.DailyInventoryMaintenanceService;



/**
 * Coordinates Daily Inventory Maintenance Worker work between the local database and shared cloud state.
 */
public class DailyInventoryMaintenanceWorker
        extends Worker
{

    public DailyInventoryMaintenanceWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams)
    {
        super(
                appContext,
                workerParams
        );
    }


    @NonNull
    @Override
    public Result doWork()
    {
        try
        {
            MedicationInventoryApplication application =
                    (MedicationInventoryApplication)
                            getApplicationContext();


            DailyInventoryMaintenanceService service =
                    application
                            .getAppContainer()
                            .getDailyInventoryMaintenanceService();


            service.runNowBlocking();


            return Result.success();
        }
        catch (Exception exception)
        {

            if (getRunAttemptCount() < 3)
            {
                return Result.retry();
            }


            return Result.failure();
        }
    }
}