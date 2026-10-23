package com.medicationinventory.model.enums;



/**
 * Lifecycle state of a persisted alert: active, acknowledged by a user, or resolved after its condition clears.
 */
public enum AlertStatus
{
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}