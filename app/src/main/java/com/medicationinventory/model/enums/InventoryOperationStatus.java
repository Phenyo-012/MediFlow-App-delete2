package com.medicationinventory.model.enums;



/**
 * Progress state for a stock operation, from local queueing and server submission to confirmation,
 * conflict, or rejection. A submitted or locally pending status does not mean the shared stock change is confirmed.
 */
public enum InventoryOperationStatus
{
    LOCAL_PENDING,
    SUBMITTED,
    CONFIRMED,
    CONFLICT,
    REJECTED
}