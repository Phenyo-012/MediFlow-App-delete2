package com.medicationinventory.model.domain;

import com.medicationinventory.model.enums.BatchStatus;



/**
 * Domain view of a medicine lot, including its receipt and expiry dates,
 * remaining and originally received quantities, status, and last update time.
 * Inventory calculations use the batch-level values so expired or otherwise
 * unavailable stock can be excluded independently of the medicine master.
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