package com.medicationinventory.service;

import androidx.annotation.NonNull;
import androidx.room.InvalidationTracker;

import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.MedicineDao;

import com.medicationinventory.data.local.database.AppDatabase;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;

import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import java.util.concurrent.atomic.AtomicBoolean;



/**
 * Bridges Room table invalidations to alert recomputation. Medicine or batch changes request a
 * refresh on a background executor; an atomic request flag coalesces bursts while the refresh loop
 * is active, then reloads each medicine and its batches before asking {@link AlertRefreshService}
 * to reconcile derived alert rows. This observer is local-only and does not start cloud sync.
 */
public class AlertInventoryObserver
{
    private final AppDatabase database;

    private final MedicineDao medicineDao;

    private final BatchDao batchDao;

    private final AlertRefreshService
            alertRefreshService;

    private final AppExecutors
            appExecutors;


    private final AtomicBoolean refreshRunning =
            new AtomicBoolean(false);

    private final AtomicBoolean refreshRequested =
            new AtomicBoolean(false);


    private boolean started;


    private final InvalidationTracker.Observer
            roomObserver =
            new InvalidationTracker.Observer(
                    "medicines",
                    "batches"
            )
            {
                @Override
                public void onInvalidated(
                        @NonNull Set<String> tables)
                {
                    requestRefresh();
                }
            };


    public AlertInventoryObserver(
            AppDatabase database,
            AlertRefreshService alertRefreshService,
            AppExecutors appExecutors)
    {
        if (database == null)
        {
            throw new IllegalArgumentException(
                    "AppDatabase is required."
            );
        }


        if (alertRefreshService == null)
        {
            throw new IllegalArgumentException(
                    "AlertRefreshService is required."
            );
        }


        if (appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "AppExecutors is required."
            );
        }


        this.database =
                database;

        this.medicineDao =
                database.medicineDao();

        this.batchDao =
                database.batchDao();

        this.alertRefreshService =
                alertRefreshService;

        this.appExecutors =
                appExecutors;
    }



    public synchronized void start()
    {
        if (started)
        {
            return;
        }


        started =
                true;


        database
                .getInvalidationTracker()
                .addObserver(
                        roomObserver
                );

        requestRefresh();
    }


    private void requestRefresh()
    {
        refreshRequested.set(
                true
        );


        if (!refreshRunning.compareAndSet(
                false,
                true
        ))
        {
            return;
        }


        appExecutors
                .databaseIO()
                .execute(
                        this::runRefreshLoop
                );
    }


    private void runRefreshLoop()
    {
        try
        {
            while (refreshRequested.getAndSet(
                    false
            ))
            {
                refreshAllNowBlocking();
            }
        }
        finally
        {
            refreshRunning.set(
                    false
            );


            if (refreshRequested.get())
            {
                requestRefresh();
            }
        }
    }


    /**
     * Runs one complete local alert refresh synchronously for scheduled maintenance. The method is
     * synchronized with observer lifecycle changes so callers cannot race observer startup state.
     */
    public synchronized void refreshAllNowBlocking()
    {
        refreshAllMedicinesInternal();
    }


    private void refreshAllMedicinesInternal()
    {
        List<MedicineEntity> medicineEntities =
                medicineDao
                        .getAllForAlertEvaluation();


        if (medicineEntities == null
                ||
                medicineEntities.isEmpty())
        {
            return;
        }


        for (MedicineEntity medicineEntity :
                medicineEntities)
        {
            if (medicineEntity == null)
            {
                continue;
            }


            Medicine medicine =
                    toDomainMedicine(
                            medicineEntity
                    );


            List<BatchEntity> batchEntities =
                    batchDao
                            .getForMedicineForAlertEvaluation(
                                    medicine.getMedicineId()
                            );


            List<Batch> batches =
                    toDomainBatches(
                            batchEntities
                    );


            alertRefreshService
                    .refreshMedicineAlertsBlocking(
                            medicine,
                            batches
                    );
        }
    }



    private Medicine toDomainMedicine(
            MedicineEntity entity)
    {
        return new Medicine(
                entity.getMedicineId(),
                entity.getMedicineCode(),
                entity.getMedicineName(),
                entity.getGenericName(),
                entity.getStrength(),
                entity.getDosageForm(),
                entity.getUnitOfMeasure(),
                entity.getReorderLevel(),
                entity.getTargetStockLevel(),
                entity.isActive(),
                entity.getUpdatedAt(),
                entity.getSyncVersion()
        );
    }


    private List<Batch> toDomainBatches(
            List<BatchEntity> entities)
    {
        if (entities == null
                ||
                entities.isEmpty())
        {
            return Collections.emptyList();
        }


        List<Batch> result =
                new ArrayList<>(
                        entities.size()
                );


        for (BatchEntity entity :
                entities)
        {
            if (entity == null)
            {
                continue;
            }


            result.add(
                    new Batch(
                            entity.getBatchId(),
                            entity.getMedicineId(),
                            entity.getBatchNumber(),
                            entity.getReceivedDate(),
                            entity.getExpiryDate(),
                            entity.getQuantityAvailable(),
                            entity.getInitialQuantity(),
                            entity.getStatus(),
                            entity.getUpdatedAt()
                    )
            );
        }


        return result;
    }
}