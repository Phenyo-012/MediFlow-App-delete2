package com.medicationinventory.model.domain;

import com.medicationinventory.model.enums.StockLevelStatus;



/**
 * Row in the stock-level report, combining a medicine's usable quantity and
 * stock status with its reorder/target thresholds and near-expiry quantity. Batch
 * counts show how that stock is distributed across all batches versus batches
 * that currently contain quantity.
 */
public class StockLevelReportItem
{
    private final String medicineId;

    private final String medicineName;

    private final String medicineCode;

    private final String unitOfMeasure;


    private final int totalAvailableQuantity;

    private final int reorderLevel;

    private final int targetStockLevel;

    private final int recommendedReorderQuantity;

    private final int nearExpiryQuantity;

    private final StockLevelStatus stockStatus;

    private final int batchCount;

    private final int batchesWithStock;



    public StockLevelReportItem(
            String medicineId,
            String medicineName,
            String medicineCode,
            String unitOfMeasure,
            int totalAvailableQuantity,
            int reorderLevel,
            int targetStockLevel,
            int recommendedReorderQuantity,
            int nearExpiryQuantity,
            StockLevelStatus stockStatus,
            int batchCount,
            int batchesWithStock)
    {
        this.medicineId =
                medicineId;

        this.medicineName =
                medicineName;

        this.medicineCode =
                medicineCode;

        this.unitOfMeasure =
                unitOfMeasure;

        this.totalAvailableQuantity =
                totalAvailableQuantity;

        this.reorderLevel =
                reorderLevel;

        this.targetStockLevel =
                targetStockLevel;

        this.recommendedReorderQuantity =
                recommendedReorderQuantity;

        this.nearExpiryQuantity =
                nearExpiryQuantity;

        this.stockStatus =
                stockStatus;

        this.batchCount =
                batchCount;

        this.batchesWithStock =
                batchesWithStock;
    }


    public String getMedicineId()
    {
        return medicineId;
    }


    public String getMedicineName()
    {
        return medicineName;
    }


    public String getMedicineCode()
    {
        return medicineCode;
    }


    public String getUnitOfMeasure()
    {
        return unitOfMeasure;
    }


    public int getTotalAvailableQuantity()
    {
        return totalAvailableQuantity;
    }


    public int getReorderLevel()
    {
        return reorderLevel;
    }


    public int getTargetStockLevel()
    {
        return targetStockLevel;
    }


    public int getRecommendedReorderQuantity()
    {
        return recommendedReorderQuantity;
    }


    public int getNearExpiryQuantity()
    {
        return nearExpiryQuantity;
    }


    public StockLevelStatus getStockStatus()
    {
        return stockStatus;
    }


    public int getBatchCount()
    {
        return batchCount;
    }


    public int getBatchesWithStock()
    {
        return batchesWithStock;
    }
}