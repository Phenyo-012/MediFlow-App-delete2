package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;



/**
 * Stores small synchronization checkpoints keyed by purpose, such as the last successful sync time,
 * server timestamp, or serialized fingerprint. Coordinators compare these values with later remote
 * snapshots; a checkpoint is sync bookkeeping rather than an inventory record.
 */
@Entity(
        tableName = "sync_metadata"
)
public class SyncMetadataEntity
{
    @PrimaryKey
    @NonNull
    private String key;

    private long lastSuccessfulSyncAt;

    private long lastServerTimestamp;

    private String value;


    public SyncMetadataEntity(
            @NonNull String key,
            long lastSuccessfulSyncAt,
            long lastServerTimestamp,
            String value)
    {
        this.key =
                key;

        this.lastSuccessfulSyncAt =
                lastSuccessfulSyncAt;

        this.lastServerTimestamp =
                lastServerTimestamp;

        this.value =
                value;
    }


    @NonNull
    public String getKey()
    {
        return key;
    }


    public void setKey(
            @NonNull String key)
    {
        this.key =
                key;
    }


    public long getLastSuccessfulSyncAt()
    {
        return lastSuccessfulSyncAt;
    }


    public void setLastSuccessfulSyncAt(
            long lastSuccessfulSyncAt)
    {
        this.lastSuccessfulSyncAt =
                lastSuccessfulSyncAt;
    }


    public long getLastServerTimestamp()
    {
        return lastServerTimestamp;
    }


    public void setLastServerTimestamp(
            long lastServerTimestamp)
    {
        this.lastServerTimestamp =
                lastServerTimestamp;
    }


    public String getValue()
    {
        return value;
    }


    public void setValue(
            String value)
    {
        this.value =
                value;
    }
}