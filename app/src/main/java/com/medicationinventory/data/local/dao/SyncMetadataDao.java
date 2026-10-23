package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.List;

import com.medicationinventory.data.local.entity.SyncMetadataEntity;




/**
 * Stores durable synchronization timestamps and metadata by key so screens and workers can observe the last successful run after process restart.
 */
@Dao
public interface SyncMetadataDao
{
    @Query("SELECT * FROM sync_metadata WHERE substr(`key`, 1, length(:prefix)) = :prefix")

    List<SyncMetadataEntity> getByPrefix(String prefix);

    @Upsert

    void upsert(
            SyncMetadataEntity metadata
    );


    @Query(
            "SELECT * " +
                    "FROM sync_metadata " +
                    "WHERE `key` = :key " +
                    "LIMIT 1"
    )
    LiveData<SyncMetadataEntity>
    observeByKey(
            String key
    );


    @Query(
            "SELECT * " +
                    "FROM sync_metadata " +
                    "WHERE `key` = :key " +
                    "LIMIT 1"
    )

    SyncMetadataEntity getByKey(
            String key
    );

    @Query(
            "SELECT lastSuccessfulSyncAt " +
                    "FROM sync_metadata " +
                    "WHERE `key` = :key " +
                    "LIMIT 1"
    )

    LiveData<Long> observeLastSuccessfulSyncAt(
            String key
    );
}