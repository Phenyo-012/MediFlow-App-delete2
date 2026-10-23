package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


/**
 * Room ledger row for a quantity movement tied to one operation, medicine, and batch. The signed
 * quantity change and transaction type describe the stock effect; client and server timestamps
 * preserve both local creation and authoritative processing time. The provisional flag distinguishes
 * an optimistic local movement from a confirmed transaction imported from the trusted backend.
 */
@Entity(tableName = "inventory_transactions",

        foreignKeys = {
                @ForeignKey(
                        entity = InventoryOperationEntity.class,
                        parentColumns = "operationId",
                        childColumns = "operationId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                ),

                @ForeignKey(
                        entity = MedicineEntity.class,
                        parentColumns = "medicineId",
                        childColumns = "medicineId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                ),

                @ForeignKey(
                        entity = BatchEntity.class,
                        parentColumns = "batchId",
                        childColumns = "batchId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                )
        },

        indices = {
                @Index(value = "operationId"),
                @Index(value = "medicineId"),
                @Index(value = "batchId"),
                @Index(value = "serverCreatedAt")
        }
)
public class InventoryTransactionEntity
{
    @PrimaryKey
    @NonNull
    private String transactionId;

    @NonNull
    private String operationId;

    @NonNull
    private String medicineId;

    @NonNull
    private String batchId;

    @NonNull
    private String transactionType;

    private int quantityChange;

    private String reason;

    @NonNull
    private String performedBy;

    private long clientCreatedAt;

    private long serverCreatedAt;

    private boolean provisional;

    public InventoryTransactionEntity(
            @NonNull String transactionId,
            @NonNull String operationId,
            @NonNull String medicineId,
            @NonNull String batchId,
            @NonNull String transactionType,
            int quantityChange,
            String reason,
            @NonNull String performedBy,
            long clientCreatedAt,
            long serverCreatedAt,
            boolean provisional)
    {

        this.transactionId = transactionId;

        this.operationId = operationId;

        this.medicineId = medicineId;

        this.batchId = batchId;

        this.transactionType = transactionType;

        this.quantityChange = quantityChange;

        this.reason = reason;

        this.performedBy = performedBy;

        this.clientCreatedAt = clientCreatedAt;

        this.serverCreatedAt = serverCreatedAt;

        this.provisional = provisional;
    }

    @NonNull
    public String getTransactionId()
    {
        return transactionId;
    }

    public void setTransactionId(@NonNull String transactionId)
    {
        this.transactionId = transactionId;
    }

    @NonNull
    public String getOperationId()
    {
        return operationId;
    }

    public void setOperationId(@NonNull String operationId)
    {
        this.operationId = operationId;
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
    public String getBatchId()
    {
        return batchId;
    }

    public void setBatchId(@NonNull String batchId)
    {
        this.batchId = batchId;
    }

    @NonNull
    public String getTransactionType()
    {
        return transactionType;
    }

    public void setTransactionType(@NonNull String transactionType)
    {
        this.transactionType = transactionType;
    }

    public int getQuantityChange()
    {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange)
    {
        this.quantityChange = quantityChange;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    @NonNull
    public String getPerformedBy()
    {
        return performedBy;
    }

    public void setPerformedBy(@NonNull String performedBy)
    {
        this.performedBy = performedBy;
    }

    public long getClientCreatedAt()
    {
        return clientCreatedAt;
    }

    public void setClientCreatedAt(long clientCreatedAt)
    {
        this.clientCreatedAt =
                clientCreatedAt;
    }

    public long getServerCreatedAt()
    {
        return serverCreatedAt;
    }

    public void setServerCreatedAt(long serverCreatedAt)
    {
        this.serverCreatedAt =
                serverCreatedAt;
    }

    public boolean isProvisional()
    {
        return provisional;
    }

    public void setProvisional(boolean provisional)
    {
        this.provisional = provisional;
    }
}