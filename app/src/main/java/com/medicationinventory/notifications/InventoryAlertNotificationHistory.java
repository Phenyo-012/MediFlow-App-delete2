package com.medicationinventory.notifications;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.app.NotificationManagerCompat;

import java.util.HashSet;
import java.util.Set;


/**
 * Tracks delivered alert IDs in app-private preferences and coordinates notification replacement
 * and cancellation. The stored owner scopes history to one Firebase UID; switching accounts
 * cancels the previous owner's notifications and synchronously clears its delivery set before the
 * new owner can use the store, preventing stale alerts from leaking across account sessions.
 */
public final class InventoryAlertNotificationHistory {

    private static final String PREFS = "mediflow_notification_delivery_v1";
    private static final String KEY_OWNER = "owner";
    private static final String KEY_DELIVERED = "delivered";
    private static final int ALERT_NOTIFICATION_ID = 1;


    private InventoryAlertNotificationHistory() { }


    public static String tag(String uid, String alertId) {
        return "mediflow.alert." + uid + "." + alertId;
    }




    @SuppressLint("ApplySharedPref")
    public static synchronized void syncOwner(Context context, String uid) {
        SharedPreferences prefs = prefs(context);
        String newOwner = safe(uid);
        String oldOwner = prefs.getString(KEY_OWNER, "");
        if (oldOwner.equals(newOwner)) return;

        for (String alertId : prefs.getStringSet(KEY_DELIVERED, new HashSet<>())) {
            NotificationManagerCompat.from(context)
                    .cancel(tag(oldOwner, alertId), ALERT_NOTIFICATION_ID);
        }
        // Persist the account switch synchronously before returning.
        prefs.edit().clear().putString(KEY_OWNER, newOwner).commit();
    }


    public static synchronized boolean wasDelivered(Context context,
                                                    String uid, String alertId) {
        SharedPreferences prefs = prefs(context);
        return safe(uid).equals(prefs.getString(KEY_OWNER, ""))
                && prefs.getStringSet(KEY_DELIVERED, new HashSet<>()).contains(alertId);
    }


    public static synchronized boolean markDelivered(Context context,
                                                     String uid, String alertId) {
        SharedPreferences prefs = prefs(context);
        if (!safe(uid).equals(prefs.getString(KEY_OWNER, ""))) return false;
        Set<String> updated = new HashSet<>(
                prefs.getStringSet(KEY_DELIVERED, new HashSet<>()));
        updated.add(alertId);
        return prefs.edit().putStringSet(KEY_DELIVERED, updated).commit();
    }


    public static synchronized void cancelNoLongerActive(
            Context context, String uid, Set<String> activeAlertIds) {
        SharedPreferences prefs = prefs(context);
        if (!safe(uid).equals(prefs.getString(KEY_OWNER, ""))) return;
        for (String id : prefs.getStringSet(KEY_DELIVERED, new HashSet<>())) {
            if (activeAlertIds == null || !activeAlertIds.contains(id)) {
                NotificationManagerCompat.from(context)
                        .cancel(tag(uid, id), ALERT_NOTIFICATION_ID);
            }
        }
    }


    public static synchronized void cancelVisible(Context context, String uid) {
        SharedPreferences prefs = prefs(context);
        if (!safe(uid).equals(prefs.getString(KEY_OWNER, ""))) return;
        for (String alertId : prefs.getStringSet(KEY_DELIVERED, new HashSet<>())) {
            NotificationManagerCompat.from(context)
                    .cancel(tag(uid, alertId), ALERT_NOTIFICATION_ID);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }


    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
