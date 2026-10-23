package com.medicationinventory.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;



/**
 * Supplies a stable installation identifier for inventory operation metadata.
 * The value is persisted in app-private preferences so retries from this install
 * keep the same device identity; it is not an account credential or an
 * authorization signal.
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

    /**
     * Returns the saved installation ID or creates and persists one on first use.
     * Synchronization prevents concurrent callers in this process from generating
     * different IDs during the initial read/write window.
     *
     * @return stable UUID associated with this app installation
     */
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