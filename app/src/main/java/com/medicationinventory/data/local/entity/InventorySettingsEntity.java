package com.medicationinventory.data.local.entity;


import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;



/**
 * Persists the local Inventory Settings record used by offline inventory workflows.
 */
@Entity(
        tableName = "inventory_settings"
)
public class InventorySettingsEntity
{
    @PrimaryKey
    @NonNull
    private String settingId;


    private int nearExpiryWarningDays;


    private long updatedAt;


    private String updatedBy;

    public InventorySettingsEntity(
            @NonNull String settingId,
            int nearExpiryWarningDays,
            long updatedAt,
            String updatedBy)
    {
        this.settingId =
                settingId;


        this.nearExpiryWarningDays =
                nearExpiryWarningDays;


        this.updatedAt =
                updatedAt;


        this.updatedBy =
                updatedBy;
    }


    @NonNull
    public String getSettingId()
    {
        return settingId;
    }


    public void setSettingId(
            @NonNull String settingId)
    {
        this.settingId =
                settingId;
    }


    public int getNearExpiryWarningDays()
    {
        return nearExpiryWarningDays;
    }


    public void setNearExpiryWarningDays(
            int nearExpiryWarningDays)
    {
        this.nearExpiryWarningDays =
                nearExpiryWarningDays;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public void setUpdatedAt(
            long updatedAt)
    {
        this.updatedAt =
                updatedAt;
    }


    public String getUpdatedBy()
    {
        return updatedBy;
    }


    public void setUpdatedBy(
            String updatedBy)
    {
        this.updatedBy =
                updatedBy;
    }
}