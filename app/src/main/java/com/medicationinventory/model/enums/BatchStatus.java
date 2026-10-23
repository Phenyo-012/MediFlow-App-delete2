package com.medicationinventory.model.enums;


/**
 * Availability state assigned to a batch as stock is depleted or its expiry date passes.
 */
public enum BatchStatus
{
    ACTIVE,
    DEPLETED,
    EXPIRED
}