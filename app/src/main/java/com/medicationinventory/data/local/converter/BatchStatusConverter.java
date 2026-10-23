package com.medicationinventory.data.local.converter;

import androidx.room.TypeConverter;

import com.medicationinventory.model.enums.BatchStatus;


/**
 * Maps BatchStatus enum values to the strings stored in Room and parses stored strings back.
 */
public final class BatchStatusConverter
{

    private BatchStatusConverter()
    {
        // Prevent instantiation
    }

    @TypeConverter

    public static String fromBatchStatus(BatchStatus status)
    {
        if (status == null)
        {
            return null;
        }

        return status.name();
    }

    @TypeConverter

    public static BatchStatus toBatchStatus(String value)
    {
        if (value == null)
        {
            return null;
        }

        return BatchStatus.valueOf(value);
    }
}