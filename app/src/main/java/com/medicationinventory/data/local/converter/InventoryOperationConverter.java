package com.medicationinventory.data.local.converter;

import androidx.room.TypeConverter;

import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;


/**
 * Maps inventory operation type and status enums to their Room string values and back.
 */
public final class InventoryOperationConverter
{

    private InventoryOperationConverter()
    {
        // Prevent instantiation
    }

    @TypeConverter

    public static String fromOperationType(InventoryOperationType type)
    {
        if (type == null)
        {
            return null;
        }

        return type.name();
    }
    @TypeConverter

    public static InventoryOperationType toOperationType(String value)
    {
        if (value == null)
        {
            return null;
        }

        return InventoryOperationType.valueOf(value);
    }
    @TypeConverter

    public static String fromOperationStatus(InventoryOperationStatus status)
    {
        if (status == null)
        {
            return null;
        }

        return status.name();
    }
    @TypeConverter

    public static InventoryOperationStatus toOperationStatus(String value)
    {
        if (value == null)
        {
            return null;
        }

        return InventoryOperationStatus.valueOf(value);
    }
}