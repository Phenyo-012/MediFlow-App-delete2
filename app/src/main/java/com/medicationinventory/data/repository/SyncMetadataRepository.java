package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.SyncMetadataDao;
import com.medicationinventory.data.local.entity.SyncMetadataEntity;



/**
 * Observes inventory synchronization timestamps and metadata through the local Room DAO.
 */
public class SyncMetadataRepository
{
    public static final String KEY_INVENTORY_SYNC =
            "INVENTORY_SYNC";


    private final SyncMetadataDao
            syncMetadataDao;


    public SyncMetadataRepository(
            SyncMetadataDao syncMetadataDao)
    {
        if (syncMetadataDao == null)
        {
            throw new IllegalArgumentException(
                    "Sync metadata DAO is required."
            );
        }


        this.syncMetadataDao =
                syncMetadataDao;
    }


    public LiveData<SyncMetadataEntity>
    observeInventorySync()
    {
        return syncMetadataDao
                .observeByKey(
                        KEY_INVENTORY_SYNC
                );
    }

    public LiveData<Long> observeLastSuccessfulInventorySyncAt()
    {
        return syncMetadataDao
                .observeLastSuccessfulSyncAt(
                        KEY_INVENTORY_SYNC
                );
    }

    public SyncMetadataEntity getInventorySync()
    {
        return syncMetadataDao
                .getByKey(
                        KEY_INVENTORY_SYNC
                );
    }


}