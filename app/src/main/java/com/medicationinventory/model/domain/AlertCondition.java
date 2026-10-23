package com.medicationinventory.model.domain;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertType;



/**
 * Represents Alert Condition data used by inventory rules and application workflows.
 */
public class AlertCondition
{
    private final AlertType alertType;

    private final String medicineId;

    private final String batchId;

    private final AlertSeverity severity;

    private final String message;

    private final int usableStock;

    private final int reorderLevel;

    private final int targetStockLevel;

    private final int recommendedQuantity;

    private final int batchQuantity;

    private final long expiryDate;



    public AlertCondition(
            AlertType alertType,
            String medicineId,
            String batchId,
            AlertSeverity severity,
            String message,
            int usableStock,
            int reorderLevel,
            int targetStockLevel,
            int recommendedQuantity,
            int batchQuantity,
            long expiryDate)
    {
        this.alertType =
                alertType;

        this.medicineId =
                medicineId;

        this.batchId =
                batchId;

        this.severity =
                severity;

        this.message =
                message;

        this.usableStock =
                usableStock;

        this.reorderLevel =
                reorderLevel;

        this.targetStockLevel =
                targetStockLevel;

        this.recommendedQuantity =
                recommendedQuantity;

        this.batchQuantity =
                batchQuantity;

        this.expiryDate =
                expiryDate;
    }


    public AlertType getAlertType()
    {
        return alertType;
    }


    public String getMedicineId()
    {
        return medicineId;
    }


    public String getBatchId()
    {
        return batchId;
    }


    public AlertSeverity getSeverity()
    {
        return severity;
    }


    public String getMessage()
    {
        return message;
    }


    public int getUsableStock()
    {
        return usableStock;
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


    public int getBatchQuantity()
    {
        return batchQuantity;
    }


    public long getExpiryDate()
    {
        return expiryDate;
    }
}