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
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.domain.ReorderRecommendationListItem;

import com.medicationinventory.service.ReorderRecommendationService;

import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Combines active medicines, their local batches, and the current near-expiry setting to publish reorder recommendation rows. Room data changes and settings refreshes trigger recomputation; the repository does not place orders or mutate stock.
 */
public class ReorderRecommendationsRepository
{
    private final ReorderRecommendationService
            reorderRecommendationService;

    private final AppExecutors
            appExecutors;

    private int nearExpiryWarningDays =
            InventorySettings.DEFAULT_NEAR_EXPIRY_WARNING_DAYS;


    private boolean settingsLoaded =
            false;


    private final MediatorLiveData<
            List<ReorderRecommendationListItem>>
            recommendations =
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


    private final AtomicInteger rebuildGeneration =
            new AtomicInteger(0);


    public ReorderRecommendationsRepository(
            MedicineDao medicineDao,
            BatchDao batchDao,
            ReorderRecommendationService
                    reorderRecommendationService,
            AppExecutors appExecutors,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (medicineDao == null
                ||
                batchDao == null
                ||
                reorderRecommendationService == null
                ||
                appExecutors == null
                ||
                inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Reorder recommendation dependencies are required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        this.reorderRecommendationService =
                reorderRecommendationService;

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


        recommendations.addSource(
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


        recommendations.addSource(
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

        recommendations.addSource(
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

    public void refresh()
    {
        requestRebuild();
    }

    public LiveData<List<ReorderRecommendationListItem>>
    observeRecommendations()
    {
        return recommendations;
    }


    public LiveData<String>
    observeError()
    {
        return error;
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


        int generation =
                rebuildGeneration
                        .incrementAndGet();


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
                        List<ReorderRecommendationListItem>
                                result =
                                buildRecommendations(
                                        medicineSnapshot,
                                        batchSnapshot
                                );


                        if (generation
                                != rebuildGeneration.get())
                        {
                            return;
                        }


                        error.postValue(
                                null
                        );


                        recommendations.postValue(
                                result
                        );
                    }
                    catch (Exception exception)
                    {
                        if (generation
                                != rebuildGeneration.get())
                        {
                            return;
                        }


                        error.postValue(
                                "Unable to calculate reorder recommendations."
                        );
                    }
                });
    }


    private List<ReorderRecommendationListItem>
    buildRecommendations(List<MedicineEntity> medicineEntities,
                         List<BatchEntity> batchEntities)
    {
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


        List<ReorderRecommendationListItem>
                result =
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


            Medicine medicine =
                    toDomainMedicine(
                            entity
                    );


            List<Batch> batches =
                    batchesByMedicine.get(
                            medicine.getMedicineId()
                    );


            if (batches == null)
            {
                batches =
                        Collections.emptyList();
            }


            ReorderRecommendation recommendation =
                    reorderRecommendationService
                            .buildReorderRecommendation(
                                    medicine,
                                    batches,
                                    nearExpiryWarningDays
                            );


            if (!recommendation.isReorderRequired())
            {
                continue;
            }


            result.add(
                    new ReorderRecommendationListItem(
                            medicine.getMedicineId(),
                            medicine.getMedicineName(),
                            medicine.getMedicineCode(),
                            recommendation.getUsableStock(),
                            recommendation.getNearExpiryStock(),
                            recommendation.getReorderLevel(),
                            recommendation.getTargetStockLevel(),
                            recommendation.getRecommendedQuantity()
                    )
            );
        }

        result.sort(
                (left, right) ->
                {
                    /*
                     * 1. Out-of-stock medicines first.
                     */
                    boolean leftOutOfStock =
                            left.getUsableStock() == 0;

                    boolean rightOutOfStock =
                            right.getUsableStock() == 0;


                    if (leftOutOfStock
                            != rightOutOfStock)
                    {
                        return leftOutOfStock
                                ? -1
                                : 1;
                    }


                    if (!leftOutOfStock)
                    {
                        long leftRatio =
                                (long) left.getUsableStock()
                                        *
                                        right.getReorderLevel();


                        long rightRatio =
                                (long) right.getUsableStock()
                                        *
                                        left.getReorderLevel();


                        int ratioComparison =
                                Long.compare(
                                        leftRatio,
                                        rightRatio
                                );


                        if (ratioComparison != 0)
                        {
                            return ratioComparison;
                        }
                    }



                    int quantityComparison =
                            Integer.compare(
                                    right.getRecommendedQuantity(),
                                    left.getRecommendedQuantity()
                            );


                    if (quantityComparison != 0)
                    {
                        return quantityComparison;
                    }


                    String leftName =
                            left.getMedicineName() == null
                                    ? ""
                                    : left.getMedicineName();


                    String rightName =
                            right.getMedicineName() == null
                                    ? ""
                                    : right.getMedicineName();


                    return leftName.compareToIgnoreCase(
                            rightName
                    );
                }
        );

        return result;
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


    private Batch toDomainBatch(BatchEntity entity)
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