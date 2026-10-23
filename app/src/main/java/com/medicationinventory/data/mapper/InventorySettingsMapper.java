package com.medicationinventory.data.mapper;


import com.medicationinventory.data.local.entity.InventorySettingsEntity;
import com.medicationinventory.model.domain.InventorySettings;


/**
 * Maps InventorySettings domain objects to local entities and local entities back to the domain model.
 */
public final class InventorySettingsMapper
{

    private InventorySettingsMapper()
    {
    }

    public static InventorySettings toDomain(
            InventorySettingsEntity entity)
    {
        if (entity == null)
        {
            return null;
        }


        return new InventorySettings(
                entity.getSettingId(),
                entity.getNearExpiryWarningDays(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy()
        );
    }


    public static InventorySettingsEntity toEntity(
            InventorySettings settings)
    {
        if (settings == null)
        {
            return null;
        }


        return new InventorySettingsEntity(
                settings.getSettingId(),
                settings.getNearExpiryWarningDays(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy()
        );
    }
}