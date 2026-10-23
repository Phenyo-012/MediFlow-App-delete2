package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;
import java.util.List;

import com.medicationinventory.data.local.entity.SyncMetadataEntity;




/**
 * Declares Room queries and updates for Sync Metadata records used by local repositories.
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