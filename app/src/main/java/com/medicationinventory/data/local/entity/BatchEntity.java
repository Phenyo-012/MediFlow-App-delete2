package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.medicationinventory.data.local.converter.BatchStatusConverter;
import com.medicationinventory.model.enums.BatchStatus;


/**
 * Local lot record linked to a medicine. Batch number, receipt and expiry dates, available quantity,
 * initial quantity, status, and update time support FEFO selection, expiry maintenance, and stock
 * reports. A unique medicine-plus-batch-number index prevents two local lot rows for the same
 * medicine while the stable batch ID is used by operations and cloud synchronization.
 */
@Entity(tableName = "batches",

        foreignKeys = {
                @ForeignKey(
                        entity = MedicineEntity.class,
                        parentColumns = "medicineId",
                        childColumns = "medicineId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                )
        },

        indices = {
                @Index(value = "medicineId"),
                @Index(value = "expiryDate"),
                @Index(value = "status"),
                @Index(value = {"medicineId", "expiryDate"}),
                @Index(value = {"medicineId", "batchNumber"}, unique = true)
        })

@TypeConverters(BatchStatusConverter.class)

public class BatchEntity
{
    @PrimaryKey
    @NonNull
    private String batchId;
    @NonNull
    private String medicineId;
    @NonNull
    private String batchNumber;
    private long receivedDate;
    private long expiryDate;
    private int quantityAvailable;
    private int initialQuantity;
    @NonNull
    private BatchStatus status;
    private long updatedAt;

    public BatchEntity(
            @NonNull String batchId,
            @NonNull String medicineId,
            @NonNull String batchNumber,
            long receivedDate,
            long expiryDate,
            int quantityAvailable,
            int initialQuantity,
            @NonNull BatchStatus status,
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
    @NonNull
    public String getBatchId()
    {
        return batchId;
    }
    public void setBatchId(@NonNull String batchId)
    {
        this.batchId = batchId;
    }
    @NonNull
    public String getMedicineId()
    {
        return medicineId;
    }
    public void setMedicineId(@NonNull String medicineId)
    {
        this.medicineId = medicineId;
    }
    @NonNull
    public String getBatchNumber()
    {
        return batchNumber;
    }
    public void setBatchNumber(@NonNull String batchNumber)
    {
        this.batchNumber = batchNumber;
    }
    public long getReceivedDate()
    {
        return receivedDate;
    }
    public void setReceivedDate(long receivedDate)
    {
        this.receivedDate = receivedDate;
    }
    public long getExpiryDate()
    {
        return expiryDate;
    }
    public void setExpiryDate(long expiryDate)
    {
        this.expiryDate = expiryDate;
    }
    public int getQuantityAvailable()
    {
        return quantityAvailable;
    }
    public void setQuantityAvailable(int quantityAvailable)
    {
        this.quantityAvailable =
                quantityAvailable;
    }
    public int getInitialQuantity()
    {
        return initialQuantity;
    }
    public void setInitialQuantity(int initialQuantity)
    {
        this.initialQuantity =
                initialQuantity;
    }
    @NonNull
    public BatchStatus getStatus()
    {
        return status;
    }
    public void setStatus(@NonNull BatchStatus status)
    {
        this.status = status;
    }
    public long getUpdatedAt()
    {
        return updatedAt;
    }
    public void setUpdatedAt(long updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}