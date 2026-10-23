package com.medicationinventory.service;

import com.medicationinventory.data.local.dao.BatchDao;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Applies Daily Inventory Maintenance Service business rules used by inventory operations and reports.
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