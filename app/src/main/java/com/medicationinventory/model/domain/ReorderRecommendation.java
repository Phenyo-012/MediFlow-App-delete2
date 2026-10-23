package com.medicationinventory.model.domain;



/**
 * Result of the local reorder policy for one medicine. It carries usable and
 * near-expiry quantities alongside the configured reorder and target levels,
 * plus the policy's recommended quantity; it is guidance for the user and does
 * not itself create a purchase order or stock operation.
 */
public class ReorderRecommendation
{
    private final String medicineId;

    private final int usableStock;

    private final int nearExpiryStock;

    private final int reorderLevel;

    private final int targetStockLevel;

    private final boolean reorderRequired;

    private final int recommendedQuantity;


    public ReorderRecommendation(
            String medicineId,
            int usableStock,
            int nearExpiryStock,
            int reorderLevel,
            int targetStockLevel,
            boolean reorderRequired,
            int recommendedQuantity)
    {
        this.medicineId =
                medicineId;

        this.usableStock =
                usableStock;

        this.nearExpiryStock =
                nearExpiryStock;

        this.reorderLevel =
                reorderLevel;

        this.targetStockLevel =
                targetStockLevel;

        this.reorderRequired =
                reorderRequired;

        this.recommendedQuantity =
                recommendedQuantity;
    }


    public String getMedicineId()
    {
        return medicineId;
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


    public boolean isReorderRequired()
    {
        return reorderRequired;
    }


    public int getRecommendedQuantity()
    {
        return recommendedQuantity;
    }
}