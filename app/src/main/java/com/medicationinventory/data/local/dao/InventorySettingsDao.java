package com.medicationinventory.data.local.dao;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.InventorySettingsEntity;


/**
 * Stores the single inventory settings record and exposes it as observable or synchronous data. Repository code supplies defaults when no row exists and handles remote refresh and trusted mutations.
 */
@Dao
public interface InventorySettingsDao
{
    @Query(
            "SELECT * FROM inventory_settings "
                    + "WHERE settingId = :settingId "
                    + "LIMIT 1"
    )
    LiveData<InventorySettingsEntity>
    observeById(
            String settingId
    );


    @Query(
            "SELECT * FROM inventory_settings "
                    + "WHERE settingId = :settingId "
                    + "LIMIT 1"
    )
    InventorySettingsEntity
    getById(
            String settingId
    );


    @Insert(
            onConflict = OnConflictStrategy.REPLACE
    )

    void upsert(
            InventorySettingsEntity settings
    );


    @Query(
            "DELETE FROM inventory_settings "
                    + "WHERE settingId = :settingId"
    )

    void deleteById(
            String settingId
    );
}