package com.medicationinventory.data.local.converter;

import androidx.room.TypeConverter;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;


/**
 * Maps alert type, severity, and status enums to their Room string values and back.
 */
public class AlertConverter
{
    @TypeConverter

    public static String fromAlertType(
            AlertType value)
    {
        return value == null
                ? null
                : value.name();
    }


    @TypeConverter

    public static AlertType toAlertType(
            String value)
    {
        return value == null
                ? null
                : AlertType.valueOf(value);
    }


    @TypeConverter

    public static String fromAlertSeverity(
            AlertSeverity value)
    {
        return value == null
                ? null
                : value.name();
    }


    @TypeConverter

    public static AlertSeverity toAlertSeverity(
            String value)
    {
        return value == null
                ? null
                : AlertSeverity.valueOf(value);
    }


    @TypeConverter

    public static String fromAlertStatus(
            AlertStatus value)
    {
        return value == null
                ? null
                : value.name();
    }


    @TypeConverter

    public static AlertStatus toAlertStatus(
            String value)
    {
        return value == null
                ? null
                : AlertStatus.valueOf(value);
    }
}