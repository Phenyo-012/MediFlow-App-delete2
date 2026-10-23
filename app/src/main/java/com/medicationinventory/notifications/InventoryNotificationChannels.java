package com.medicationinventory.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;


/**
 * Creates the stable Android 8+ channel used for inventory alerts. Channel identity persists across
 * app runs, so the ID must remain consistent with permission checks and the publisher; creation is
 * a no-op on older versions or when NotificationManager is unavailable.
 */
public final class InventoryNotificationChannels {

    public static final String INVENTORY_ALERTS_CHANNEL_ID = "mediflow_inventory_alerts";


    private InventoryNotificationChannels() {
    }


    public static void create(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                INVENTORY_ALERTS_CHANNEL_ID,
                "Inventory alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(
                "Low stock, near-expiry and expired stock notifications from MediFlow."
        );
        manager.createNotificationChannel(channel);
    }
}
