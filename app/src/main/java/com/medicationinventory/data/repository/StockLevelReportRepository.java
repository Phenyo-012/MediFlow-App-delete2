package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.MedicineDao;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.domain.StockLevelReportItem;

import com.medicationinventory.service.StockLevelReportService;

import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;



/**
 * Combines local medicine and batch snapshots with inventory settings, then publishes report rows through LiveData. DAO snapshots feed the calculation service; source or setting changes recompute rows without mutating inventory.
 */
public class StockLevelReportRepository
{
    private final StockLevelReportService
            stockLevelReportService;

    private final AppExecutors
            appExecutors;

    private int nearExpiryWarningDays =
            InventorySettings.DEFAULT_NEAR_EXPIRY_WARNING_DAYS;


    private boolean settingsLoaded =
            false;


    private final MediatorLiveData<
            List<StockLevelReportItem>>
            reportItems =
            new MediatorLiveData<>();


    private final MutableLiveData<String>
            error =
            new MutableLiveData<>(
                    null
            );


    private List<MedicineEntity> latestMedicines =
            Collections.emptyList();

    private List<BatchEntity> latestBatches =
            Collections.emptyList();


    private boolean medicinesLoaded =
            false;

    private boolean batchesLoaded =
            false;


    private final AtomicInteger generation =
            new AtomicInteger(0);


    public StockLevelReportRepository(
            MedicineDao medicineDao,
            BatchDao batchDao,
            StockLevelReportService stockLevelReportService,
            AppExecutors appExecutors,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (medicineDao == null
                ||
                batchDao == null
                ||
                stockLevelReportService == null
                ||
                appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "Stock-level report dependencies are required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        this.stockLevelReportService =
                stockLevelReportService;

        this.appExecutors =
                appExecutors;

        this.nearExpiryWarningDays =
                nearExpiryWarningDays;


        LiveData<List<MedicineEntity>>
                medicineSource =
                medicineDao
                        .observeActiveForReorderRecommendations();


        LiveData<List<BatchEntity>>
                batchSource =
                batchDao
                        .observeAllForReorderRecommendations();


        reportItems.addSource(
                medicineSource,
                medicines ->
                {
                    latestMedicines =
                            medicines == null
                                    ? Collections.emptyList()
                                    : medicines;


                    medicinesLoaded =
                            true;


                    requestRebuild();
                }
        );


        reportItems.addSource(
                batchSource,
                batches ->
                {
                    latestBatches =
                            batches == null
                                    ? Collections.emptyList()
                                    : batches;


                    batchesLoaded =
                            true;


                    requestRebuild();
                }
        );

        reportItems.addSource(
                inventorySettingsRepository
                        .observeInventorySettings(),
                settings ->
                {
                    nearExpiryWarningDays =
                            settings == null
                                    ? InventorySettings
                                      .DEFAULT_NEAR_EXPIRY_WARNING_DAYS
                                    : settings
                                    .getNearExpiryWarningDays();


                    settingsLoaded =
                            true;


                    requestRebuild();
                }
        );
    }


    public LiveData<List<StockLevelReportItem>>
    observeReportItems()
    {
        return reportItems;
    }


    public LiveData<String>
    observeError()
    {
        return error;
    }


    public void refresh()
    {
        requestRebuild();
    }


    private void requestRebuild()
    {

        if (!medicinesLoaded
                ||
                !batchesLoaded
                ||
                !settingsLoaded)
        {
            return;
        }


        int currentGeneration =
                generation.incrementAndGet();


        List<MedicineEntity> medicineSnapshot =
                new ArrayList<>(
                        latestMedicines
                );


        List<BatchEntity> batchSnapshot =
                new ArrayList<>(
                        latestBatches
                );


        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        List<StockLevelReportItem> result =
                                buildReport(
                                        medicineSnapshot,
                                        batchSnapshot
                                );



                        if (currentGeneration
                                != generation.get())
                        {
                            return;
                        }


                        error.postValue(
                                null
                        );


                        reportItems.postValue(
                                result
                        );
                    }
                    catch (Exception exception)
                    {
                        if (currentGeneration
                                != generation.get())
                        {
                            return;
                        }


                        error.postValue(
                                "Unable to generate the stock-level report."
                        );
                    }
                });
    }


    private List<StockLevelReportItem> buildReport(
            List<MedicineEntity> medicineEntities,
            List<BatchEntity> batchEntities)
    {
        List<Medicine> medicines =
                new ArrayList<>();


        for (MedicineEntity entity :
                medicineEntities)
        {
            if (entity == null
                    ||
                    !entity.isActive())
            {
                continue;
            }


            medicines.add(
                    toDomainMedicine(
                            entity
                    )
            );
        }


        Map<String, List<Batch>>
                batchesByMedicine =
                new HashMap<>();


        for (BatchEntity entity :
                batchEntities)
        {
            if (entity == null)
            {
                continue;
            }


            Batch batch =
                    toDomainBatch(
                            entity
                    );


            batchesByMedicine
                    .computeIfAbsent(
                            batch.getMedicineId(),
                            key ->
                                    new ArrayList<>()
                    )
                    .add(
                            batch
                    );
        }


        return stockLevelReportService
                .buildReport(
                        medicines,
                        batchesByMedicine,
                        nearExpiryWarningDays,
                        false
                );
    }


    private Medicine toDomainMedicine(
            MedicineEntity entity)
    {
        return new Medicine(
                entity.getMedicineId(),
                entity.getMedicineCode(),
                entity.getMedicineName(),
                entity.getGenericName(),
                entity.getCategory(),
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


    private Batch toDomainBatch(
            BatchEntity entity)
    {
        return new Batch(
                entity.getBatchId(),
                entity.getMedicineId(),
                entity.getBatchNumber(),
                entity.getReceivedDate(),
                entity.getExpiryDate(),
                entity.getQuantityAvailable(),
                entity.getInitialQuantity(),
                entity.getStatus(),
                entity.getUpdatedAt()
        );
    }
}