package com.medicationinventory.service;

import com.medicationinventory.data.local.dao.BatchDao;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Runs the daily local maintenance pass for batch expiry and inventory alerts. Expiry is evaluated
 * against the South African business date; afterward, the alert observer rebuilds its derived
 * records from the updated local inventory. Scheduling is handled by the worker layer, and this
 * service does not itself upload those records to Firestore.
 */
public class DailyInventoryMaintenanceService
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final BatchDao batchDao;

    private final AlertInventoryObserver
            alertInventoryObserver;


    public DailyInventoryMaintenanceService(
            BatchDao batchDao,
            AlertInventoryObserver alertInventoryObserver)
    {
        if (batchDao == null)
        {
            throw new IllegalArgumentException(
                    "BatchDao is required."
            );
        }


        if (alertInventoryObserver == null)
        {
            throw new IllegalArgumentException(
                    "AlertInventoryObserver is required."
            );
        }


        this.batchDao =
                batchDao;

        this.alertInventoryObserver =
                alertInventoryObserver;
    }


    /**
     * Marks batches expired before today's business-day boundary, then refreshes all observed
     * alerts. The DAO update precedes alert evaluation so the derived alerts see the new batch
     * status; failures propagate to the scheduled caller for retry or failure reporting.
     */
    public void runNowBlocking()
    {
        long todayStartMillis =
                LocalDate
                        .now(
                                BUSINESS_ZONE
                        )
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        batchDao.markExpiredBefore(
                todayStartMillis
        );


        alertInventoryObserver
                .refreshAllNowBlocking();
    }
}