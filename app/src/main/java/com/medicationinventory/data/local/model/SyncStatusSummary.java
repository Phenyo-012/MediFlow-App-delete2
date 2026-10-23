package com.medicationinventory.data.local.model;


/**
 * Counts of inventory operations grouped by pending, synced, attention-required, and retrying status.
 */
public class SyncStatusSummary
{
    public int pendingCount;

    public int syncedCount;

    public int requiresAttentionCount;

    public int retryingCount;
}