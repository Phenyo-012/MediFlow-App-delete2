package com.medicationinventory.data.local.model;



/**
 * A movement history row with transaction details, local and server times, and sync outcome fields.
 */
public class MovementHistoryRecord
{
    public String transactionId;

    public String operationId;

    public String medicineId;

    public String medicineName;

    public String medicineCode;

    public String batchId;

    public String batchNumber;

    public String transactionType;

    public int quantityChange;

    public String reason;

    public String performedBy;

    public long clientCreatedAt;

    public long serverCreatedAt;

    public boolean provisional;

    public String operationStatus;

    public String overrideReason;

    public String resultCode;

    public String resultMessage;
}