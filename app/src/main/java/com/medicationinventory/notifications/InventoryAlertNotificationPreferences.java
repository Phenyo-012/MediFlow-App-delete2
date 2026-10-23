package com.medicationinventory.notifications;

import android.content.Context;
import android.content.SharedPreferences;




/**
 * Stores the master switch and per-type choices in app-private preferences, namespaced by Firebase
 * UID so one device can retain separate settings for multiple accounts. New keys default to enabled;
 * the policy layer still denies alert types that the application does not recognize.
 */
public final class InventoryAlertNotificationPreferences {

    private static final String PREFS = "mediflow_notification_preferences_v1";
    private static final String MASTER = "master";
    private static final String LOW = "low_stock";
    private static final String NEAR = "near_expiry";
    private static final String EXPIRED = "expired";

    private final SharedPreferences preferences;


    public InventoryAlertNotificationPreferences(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
    }


    private String key(String uid, String name) {
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("Signed-in user ID is required.");
        }
        return uid.trim() + ":" + name;
    }

    public boolean isMasterEnabled(String uid) {
        return preferences.getBoolean(key(uid, MASTER), true);
    }

    public boolean isLowStockEnabled(String uid) {
        return preferences.getBoolean(key(uid, LOW), true);
    }

    public boolean isNearExpiryEnabled(String uid) {
        return preferences.getBoolean(key(uid, NEAR), true);
    }

    public boolean isExpiredEnabled(String uid) {
        return preferences.getBoolean(key(uid, EXPIRED), true);
    }

    public void setMasterEnabled(String uid, boolean enabled) {
        preferences.edit().putBoolean(key(uid, MASTER), enabled).apply();
    }

    public void setLowStockEnabled(String uid, boolean enabled) {
        preferences.edit().putBoolean(key(uid, LOW), enabled).apply();
    }

    public void setNearExpiryEnabled(String uid, boolean enabled) {
        preferences.edit().putBoolean(key(uid, NEAR), enabled).apply();
    }

    public void setExpiredEnabled(String uid, boolean enabled) {
        preferences.edit().putBoolean(key(uid, EXPIRED), enabled).apply();
    }


    public boolean allows(String uid, String type) {
        return InventoryNotificationPreferencePolicy.allows(
                isMasterEnabled(uid),
                isLowStockEnabled(uid),
                isNearExpiryEnabled(uid),
                isExpiredEnabled(uid),
                type);
    }


    public int enabledTypeCount(String uid) {
        return InventoryNotificationPreferencePolicy.enabledTypeCount(
                isLowStockEnabled(uid),
                isNearExpiryEnabled(uid),
                isExpiredEnabled(uid));
    }
}
