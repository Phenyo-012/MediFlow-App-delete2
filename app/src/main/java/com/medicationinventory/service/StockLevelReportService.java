package com.medicationinventory.service;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.domain.StockLevelReportItem;

import com.medicationinventory.model.enums.StockLevelStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


/**
 * Applies Stock Level Report Service business rules used by inventory operations and reports.
 */
public class StockLevelReportService
{
    private final ReorderRecommendationService
            reorderRecommendationService;


    public StockLevelReportService(
            ReorderRecommendationService
                    reorderRecommendationService)
    {
        if (reorderRecommendationService == null)
        {
            throw new IllegalArgumentException(
                    "Reorder recommendation service is required."
            );
        }


        this.reorderRecommendationService =
                reorderRecommendationService;
    }


    public StockLevelReportItem buildItem(
            Medicine medicine,
            List<Batch> batches,
            int nearExpiryWarningDays)
    {
        if (medicine == null)
        {
            throw new IllegalArgumentException(
                    "Medicine is required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        List<Batch> safeBatches =
                batches == null
                        ? Collections.emptyList()
                        : batches;


        ReorderRecommendation recommendation =
                reorderRecommendationService
                        .buildReorderRecommendation(
                                medicine,
                                safeBatches,
                                nearExpiryWarningDays
                        );


        StockLevelStatus status =
                deriveStatus(
                        recommendation
                );


        int batchCount =
                0;


        int batchesWithStock =
                0;


        for (Batch batch :
                safeBatches)
        {
            if (batch == null)
            {
                continue;
            }


            if (!medicine
                    .getMedicineId()
                    .equals(
                            batch.getMedicineId()
                    ))
            {
                continue;
            }


            batchCount++;


            if (batch.getQuantityAvailable() > 0)
            {
                batchesWithStock++;
            }
        }


        return new StockLevelReportItem(
                medicine.getMedicineId(),
                medicine.getMedicineName(),
                medicine.getMedicineCode(),
                medicine.getUnitOfMeasure(),

                recommendation.getUsableStock(),

                recommendation.getReorderLevel(),

                recommendation.getTargetStockLevel(),

                recommendation.getRecommendedQuantity(),

                recommendation.getNearExpiryStock(),

                status,

                batchCount,

                batchesWithStock
        );
    }


    public List<StockLevelReportItem> buildReport(
            List<Medicine> medicines,
            Map<String, List<Batch>> batchesByMedicine,
            int nearExpiryWarningDays,
            boolean reorderOnly)
    {
        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        if (medicines == null
                ||
                medicines.isEmpty())
        {
            return Collections.emptyList();
        }


        Map<String, List<Batch>> safeBatchMap =
                batchesByMedicine == null
                        ? Collections.emptyMap()
                        : batchesByMedicine;


        List<StockLevelReportItem> result =
                new ArrayList<>();


        for (Medicine medicine :
                medicines)
        {
            if (medicine == null
                    ||
                    !medicine.isActive())
            {
                continue;
            }


            List<Batch> batches =
                    safeBatchMap.get(
                            medicine.getMedicineId()
                    );


            StockLevelReportItem item =
                    buildItem(
                            medicine,
                            batches,
                            nearExpiryWarningDays
                    );


            if (reorderOnly
                    &&
                    item.getStockStatus()
                            == StockLevelStatus.NORMAL)
            {
                continue;
            }


            result.add(
                    item
            );
        }


        result.sort(
                Comparator
                        .comparing(
                                StockLevelReportItem::getMedicineName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(
                                StockLevelReportItem::getMedicineCode,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );


        return result;
    }

    private StockLevelStatus deriveStatus(
            ReorderRecommendation recommendation)
    {
        if (recommendation.getUsableStock() == 0)
        {
            return StockLevelStatus.OUT_OF_STOCK;
        }


        if (recommendation.isReorderRequired())
        {
            return StockLevelStatus.LOW_STOCK;
        }


        return StockLevelStatus.NORMAL;
    }
}