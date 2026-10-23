package com.medicationinventory.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;



/**
 * Provides Device Id Provider behavior used by the MediFlow application.
 */
public class DeviceIdProvider
{
    private static final String PREFS_NAME = "medication_inventory_identity";

    private static final String KEY_DEVICE_ID = "device_id";

    private final SharedPreferences preferences;


    public DeviceIdProvider(Context context)
    {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME,
                Context.MODE_PRIVATE);
    }

    public synchronized String getDeviceId()
    {
        String existing = preferences.getString(KEY_DEVICE_ID, null);

        if (existing != null && !existing.trim().isEmpty())
        {
            return existing;
        }

        String generated = UUID.randomUUID().toString();

        preferences.edit().putString(KEY_DEVICE_ID, generated).apply();

        return generated;
    }
}