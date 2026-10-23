package com.medicationinventory.data.local.model;

/**
 * The operation, retry schedule, and latest processing result displayed in sync history.
 */
public class SyncStatusRecord
{
    public String operationId;

    public String operationType;

    public String medicineId;

    public String medicineName;

    public String medicineCode;

    public int requestedQuantity;

    public long clientCreatedAt;

    public String status;

    public String resultCode;

    public String resultMessage;

    public int attemptCount;

    public long lastAttemptAt;

    public long nextAttemptAt;

    public String lastErrorCode;
}