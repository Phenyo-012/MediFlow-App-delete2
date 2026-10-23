package com.medicationinventory.model.domain;

import com.medicationinventory.model.enums.BatchStatus;



/**
 * Represents Batch data used by inventory rules and application workflows.
 */
public class Batch
{
    private final String batchId;
    private final String medicineId;
    private final String batchNumber;
    private final long receivedDate;
    private final long expiryDate;
    private final int quantityAvailable;
    private final int initialQuantity;
    private final BatchStatus status;
    private final long updatedAt;


    public Batch(
            String batchId,
            String medicineId,
            String batchNumber,
            long receivedDate,
            long expiryDate,
            int quantityAvailable,
            int initialQuantity,
            BatchStatus status,
            long updatedAt)
    {
        this.batchId = batchId;
        this.medicineId = medicineId;
        this.batchNumber = batchNumber;
        this.receivedDate = receivedDate;
        this.expiryDate = expiryDate;
        this.quantityAvailable = quantityAvailable;
        this.initialQuantity = initialQuantity;
        this.status = status;
        this.updatedAt = updatedAt;
    }
    public String getBatchId()
    {
        return batchId;
    }
    public String getMedicineId()
    {
        return medicineId;
    }
    public String getBatchNumber()
    {
        return batchNumber;
    }
    public long getReceivedDate()
    {
        return receivedDate;
    }
    public long getExpiryDate()
    {
        return expiryDate;
    }
    public int getQuantityAvailable()
    {
        return quantityAvailable;
    }
    public int getInitialQuantity()
    {
        return initialQuantity;
    }
    public BatchStatus getStatus()
    {
        return status;
    }
    public long getUpdatedAt()
    {
        return updatedAt;
    }
}