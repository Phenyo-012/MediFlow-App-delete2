package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(tableName = "operation_allocations",

        foreignKeys = {
                @ForeignKey(
                        entity = InventoryOperationEntity.class,
                        parentColumns = "operationId",
                        childColumns = "operationId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.CASCADE
                ),

                @ForeignKey(
                        entity = BatchEntity.class,
                        parentColumns = "batchId",
                        childColumns = "batchId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                )
        },

/**
 * Persists the local Operation Allocation record used by offline inventory workflows.
 */
        indices =
                {
                        @Index(value = "operationId"), @Index(value = "batchId")
                }
)
public class OperationAllocationEntity
{

    @PrimaryKey
    @NonNull
    private String id;
    @NonNull
    private String operationId;
    @NonNull
    private String batchId;
    private int quantity;
    private int allocationOrder;

    public OperationAllocationEntity(
            @NonNull String id,
            @NonNull String operationId,
            @NonNull String batchId,
            int quantity,
            int allocationOrder)
    {

        this.id = id;
        this.operationId = operationId;
        this.batchId = batchId;
        this.quantity = quantity;
        this.allocationOrder = allocationOrder;
    }

    @NonNull
    public String getId()
    {
        return id;
    }

    public void setId(@NonNull String id)
    {
        this.id = id;
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
    public String getBatchId()
    {
        return batchId;
    }

    public void setBatchId(@NonNull String batchId)
    {
        this.batchId = batchId;
    }

    public int getQuantity()
    {
        return quantity;
    }

    public void setQuantity(int quantity)
    {
        this.quantity = quantity;
    }

    public int getAllocationOrder()
    {
        return allocationOrder;
    }

    public void setAllocationOrder(int allocationOrder)
    {
        this.allocationOrder =
                allocationOrder;
    }
}