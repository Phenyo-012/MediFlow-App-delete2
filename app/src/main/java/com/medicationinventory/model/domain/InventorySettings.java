package com.medicationinventory.model.domain;




/**
 * Shared inventory policy values currently used for the near-expiry warning
 * window. The setting carries update provenance for synchronization and rejects
 * blank IDs or negative warning periods so invalid values cannot enter the
 * domain model.
 */
public class InventorySettings
{
    public static final String SETTING_ID =
            "inventory";


    public static final int DEFAULT_NEAR_EXPIRY_WARNING_DAYS =
            90;


    private final String settingId;

    private final int nearExpiryWarningDays;

    private final long updatedAt;

    private final String updatedBy;


    public InventorySettings(
            String settingId,
            int nearExpiryWarningDays,
            long updatedAt,
            String updatedBy)
    {
        if (settingId == null
                ||
                settingId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Setting ID is required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        this.settingId =
                settingId.trim();


        this.nearExpiryWarningDays =
                nearExpiryWarningDays;


        this.updatedAt =
                updatedAt;


        this.updatedBy =
                updatedBy;
    }


    public String getSettingId()
    {
        return settingId;
    }


    public int getNearExpiryWarningDays()
    {
        return nearExpiryWarningDays;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public String getUpdatedBy()
    {
        return updatedBy;
    }


    public static InventorySettings defaultSettings()
    {
        return new InventorySettings(
                SETTING_ID,
                DEFAULT_NEAR_EXPIRY_WARNING_DAYS,
                0L,
                null
        );
    }
}