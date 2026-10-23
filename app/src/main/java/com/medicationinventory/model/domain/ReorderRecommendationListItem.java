package com.medicationinventory.model.domain;



/**
 * Display-ready reorder row combining medicine identity with usable stock,
 * near-expiry stock, configured thresholds, and the recommendation quantity.
 * The report can render these values without repeating medicine lookups for
 * each row.
 */
public class ReorderRecommendationListItem
{
    private final String medicineId;

    private final String medicineName;

    private final String medicineCode;

    private final int usableStock;

    private final int nearExpiryStock;

    private final int reorderLevel;

    private final int targetStockLevel;

    private final int recommendedQuantity;



    public ReorderRecommendationListItem(
            String medicineId,
            String medicineName,
            String medicineCode,
            int usableStock,
            int nearExpiryStock,
            int reorderLevel,
            int targetStockLevel,
            int recommendedQuantity)
    {
        this.medicineId =
                medicineId;

        this.medicineName =
                medicineName;

        this.medicineCode =
                medicineCode;

        this.usableStock =
                usableStock;

        this.nearExpiryStock =
                nearExpiryStock;

        this.reorderLevel =
                reorderLevel;

        this.targetStockLevel =
                targetStockLevel;

        this.recommendedQuantity =
                recommendedQuantity;
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


    public int getUsableStock()
    {
        return usableStock;
    }


    public int getNearExpiryStock()
    {
        return nearExpiryStock;
    }


    public int getReorderLevel()
    {
        return reorderLevel;
    }


    public int getTargetStockLevel()
    {
        return targetStockLevel;
    }


    public int getRecommendedQuantity()
    {
        return recommendedQuantity;
    }
}