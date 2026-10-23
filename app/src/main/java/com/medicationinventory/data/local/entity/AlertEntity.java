package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.medicationinventory.data.local.converter.AlertConverter;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;


/**
 * Locally derived inventory alert associated with a medicine and, when relevant, a specific batch.
 * Alert type, severity, status, message, and lifecycle timestamps support the alerts screen,
 * acknowledgement and resolution flows, reports, and notification dispatch. These rows are
 * recomputed from local stock/settings and are not the trusted stock ledger.
 */
@Entity(
        tableName = "alerts",

        foreignKeys = {
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
                @Index(value = "status"),
                @Index(value = "alertType"),
                @Index(value = "medicineId"),
                @Index(value = "batchId")
        }
)
@TypeConverters(AlertConverter.class)
public class AlertEntity
{
    @PrimaryKey
    @NonNull
    private String alertId;


    @NonNull
    private AlertType alertType;


    @NonNull
    private String medicineId;


    private String batchId;


    @NonNull
    private AlertSeverity severity;


    @NonNull
    private AlertStatus status;


    @NonNull
    private String message;


    private long createdAt;

    private long updatedAt;


    public AlertEntity(
            @NonNull String alertId,
            @NonNull AlertType alertType,
            @NonNull String medicineId,
            String batchId,
            @NonNull AlertSeverity severity,
            @NonNull AlertStatus status,
            @NonNull String message,
            long createdAt,
            long updatedAt)
    {
        this.alertId =
                alertId;

        this.alertType =
                alertType;

        this.medicineId =
                medicineId;

        this.batchId =
                batchId;

        this.severity =
                severity;

        this.status =
                status;

        this.message =
                message;

        this.createdAt =
                createdAt;

        this.updatedAt =
                updatedAt;
    }


    @NonNull
    public String getAlertId()
    {
        return alertId;
    }


    public void setAlertId(
            @NonNull String alertId)
    {
        this.alertId =
                alertId;
    }


    @NonNull
    public AlertType getAlertType()
    {
        return alertType;
    }


    public void setAlertType(
            @NonNull AlertType alertType)
    {
        this.alertType =
                alertType;
    }


    @NonNull
    public String getMedicineId()
    {
        return medicineId;
    }


    public void setMedicineId(
            @NonNull String medicineId)
    {
        this.medicineId =
                medicineId;
    }


    public String getBatchId()
    {
        return batchId;
    }


    public void setBatchId(
            String batchId)
    {
        this.batchId =
                batchId;
    }


    @NonNull
    public AlertSeverity getSeverity()
    {
        return severity;
    }


    public void setSeverity(
            @NonNull AlertSeverity severity)
    {
        this.severity =
                severity;
    }


    @NonNull
    public AlertStatus getStatus()
    {
        return status;
    }


    public void setStatus(
            @NonNull AlertStatus status)
    {
        this.status =
                status;
    }


    @NonNull
    public String getMessage()
    {
        return message;
    }


    public void setMessage(
            @NonNull String message)
    {
        this.message =
                message;
    }


    public long getCreatedAt()
    {
        return createdAt;
    }


    public void setCreatedAt(
            long createdAt)
    {
        this.createdAt =
                createdAt;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public void setUpdatedAt(
            long updatedAt)
    {
        this.updatedAt =
                updatedAt;
    }
}