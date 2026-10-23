package com.medicationinventory.notifications;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.medicationinventory.ui.alerts.AlertsActivity;


/**
 * Builds and posts notification previews or alert notifications on the inventory-alert channel.
 * Alert notifications use an opaque history tag and ID for replacement/cancellation, open the
 * Alerts screen, and provide a generic public lock-screen version so medicine-specific details are
 * shown only in the private notification content. Permission denial or a posting-time security
 * exception is reported as {@code false} to the caller.
 */
public final class InventoryNotificationPublisher {

    private static final int PREVIEW_NOTIFICATION_ID = 9001;
    private static final int ALERT_NOTIFICATION_ID = 1;


    private InventoryNotificationPublisher() { }


    private static PendingIntent openAlerts(Context context, int requestCode) {
        Intent intent = new Intent(context, AlertsActivity.class);
        intent.setAction("com.medicationinventory.OPEN_ALERTS_FROM_NOTIFICATION");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("MissingPermission")

    public static boolean sendPreview(Context context) {
        if (!InventoryNotificationPermission.canPost(context)) return false;
        InventoryNotificationChannels.create(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                InventoryNotificationChannels.INVENTORY_ALERTS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MediFlow notification test")
                .setContentText("Notifications are ready. Tap to open Inventory Alerts.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openAlerts(context, PREVIEW_NOTIFICATION_ID));
        try {
            NotificationManagerCompat.from(context).notify(
                    PREVIEW_NOTIFICATION_ID, builder.build());
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }


    @SuppressLint("MissingPermission")

    /**
     * Posts one alert notification when Android permission and owner identifiers are available.
     * The body is bounded to 200 characters, and the lock-screen public version omits alert details.
     *
     * @return {@code true} after posting, or {@code false} when preconditions or platform permission fail
     */
    public static boolean sendAlert(Context context, String uid, String alertId,
                                    String type, String message) {
        if (!InventoryNotificationPermission.canPost(context)
                || uid == null || uid.isEmpty()
                || alertId == null || alertId.isEmpty()) return false;
        InventoryNotificationChannels.create(context);

        String body = message == null || message.trim().isEmpty()
                ? "An inventory condition needs your attention."
                : message.trim();
        if (body.length() > 200) body = body.substring(0, 200);


        Notification publicVersion = new NotificationCompat.Builder(context,
                InventoryNotificationChannels.INVENTORY_ALERTS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("MediFlow inventory alert")
                .setContentText("Unlock your device to review inventory alerts.")
                .build();

        Notification notification = new NotificationCompat.Builder(context,
                InventoryNotificationChannels.INVENTORY_ALERTS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(InventoryAlertNotificationPolicy.title(type))
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .setContentIntent(openAlerts(context, ALERT_NOTIFICATION_ID))
                .build();
        try {
            NotificationManagerCompat.from(context).notify(
                    InventoryAlertNotificationHistory.tag(uid, alertId),
                    ALERT_NOTIFICATION_ID, notification);
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }
}
