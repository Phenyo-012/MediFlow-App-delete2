package com.medicationinventory.model.enums;



/**
 * Defines supported Inventory Operation Status values shared by inventory and authorization workflows.
 */
public enum InventoryOperationStatus
{
    LOCAL_PENDING,
    SUBMITTED,
    CONFIRMED,
    CONFLICT,
    REJECTED
}