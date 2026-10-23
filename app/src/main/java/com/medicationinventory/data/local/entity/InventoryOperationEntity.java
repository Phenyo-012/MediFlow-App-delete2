package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.medicationinventory.data.local.converter.InventoryOperationConverter;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;


/**
 * Room representation of a user's inventory-operation envelope. It records the requested type and
 * quantity, medicine, initiating UID and device, client timestamp, current local/cloud status, and
 * operation-specific receipt, correction, or FEFO-override metadata. The operation is the parent
 * identity for allocations, provisional movement rows, and its sync queue entry; its status and
 * result fields let synchronization distinguish pending work from terminal server outcomes.
 */
@Entity(tableName = "inventory_operations",

        foreignKeys =
                {
                        @ForeignKey(
                                entity = MedicineEntity.class,
                                parentColumns = "medicineId",
                                childColumns = "medicineId",
                                onUpdate = ForeignKey.NO_ACTION,
                                onDelete = ForeignKey.NO_ACTION
                        )},

        indices =
                {
                        @Index(value = "medicineId"),
                        @Index(value = "status"),
                        @Index(value = "clientCreatedAt"),
                        @Index(value = "performedBy")
                }
)
@TypeConverters(InventoryOperationConverter.class)
public class InventoryOperationEntity
{
    @PrimaryKey
    @NonNull
    private String operationId;
    @NonNull
    private InventoryOperationType operationType;
    @NonNull
    private String medicineId;
    private int requestedQuantity;
    private String reason;
    @NonNull
    private String performedBy;
    private long clientCreatedAt;
    @NonNull
    private InventoryOperationStatus status;

    private String relatedOperationId;

    private String overrideReason;
    @NonNull
    private String deviceId;

    private String resultCode;

    private String resultMessage;


    private String receiptMode;
    private Long receiptReceivedDate;

    public InventoryOperationEntity(
            @NonNull String operationId,
            @NonNull InventoryOperationType operationType,
            @NonNull String medicineId,
            int requestedQuantity,
            String reason,
            @NonNull String performedBy,
            long clientCreatedAt,
            @NonNull InventoryOperationStatus status,
            String relatedOperationId,
            String overrideReason,
            @NonNull String deviceId,
            String resultCode,
            String resultMessage)
    {

        this.operationId = operationId;
        this.operationType = operationType;
        this.medicineId = medicineId;
        this.requestedQuantity = requestedQuantity;
        this.reason = reason;
        this.performedBy = performedBy;
        this.clientCreatedAt = clientCreatedAt;
        this.status = status;
        this.relatedOperationId = relatedOperationId;
        this.overrideReason = overrideReason;
        this.deviceId = deviceId;
        this.resultCode = resultCode;
        this.resultMessage = resultMessage;
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
    public InventoryOperationType getOperationType()
    {
        return operationType;
    }

    public void setOperationType(@NonNull InventoryOperationType operationType)
    {
        this.operationType = operationType;
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

    public int getRequestedQuantity()
    {
        return requestedQuantity;
    }

    public void setRequestedQuantity(int requestedQuantity)
    {
        this.requestedQuantity = requestedQuantity;
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
        this.clientCreatedAt = clientCreatedAt;
    }

    @NonNull
    public InventoryOperationStatus getStatus()
    {
        return status;
    }

    public void setStatus(@NonNull InventoryOperationStatus status)
    {
        this.status = status;
    }

    public String getRelatedOperationId()
    {
        return relatedOperationId;
    }

    public void setRelatedOperationId(String relatedOperationId)
    {
        this.relatedOperationId = relatedOperationId;
    }

    public String getOverrideReason()
    {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason)
    {
        this.overrideReason = overrideReason;
    }

    @NonNull
    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(
            @NonNull String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getResultCode()
    {
        return resultCode;
    }

    public void setResultCode(String resultCode)
    {
        this.resultCode = resultCode;
    }

    public Long getReceiptReceivedDate() {
        return receiptReceivedDate;
    }

    public void setReceiptReceivedDate(Long receiptReceivedDate) {
        this.receiptReceivedDate = receiptReceivedDate;
    }

    public String getReceiptMode() {
        return receiptMode;
    }

    public void setReceiptMode(String receiptMode) {
        this.receiptMode = receiptMode;
    }

    public String getResultMessage()
    {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage)
    {
        this.resultMessage = resultMessage;
    }
}