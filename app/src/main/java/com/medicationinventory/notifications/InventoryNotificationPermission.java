package com.medicationinventory.notifications;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;


/**
 * Centralizes the platform checks required before MediFlow posts an alert: Android 13 runtime
 * permission, the app-wide notification setting, and (on Android 8+) the inventory-alert channel's
 * importance. The settings helper first opens notification-specific settings and falls back to the
 * app details page when that intent is unavailable.
 */
public final class InventoryNotificationPermission {


    private InventoryNotificationPermission() {
    }


    public static boolean needsRuntimePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }


    public static boolean isRuntimePermissionGranted(Context context) {
        return !needsRuntimePermission() ||
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED;
    }


    public static boolean canPost(Context context) {
        if (!isRuntimePermissionGranted(context) ||
                !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return false;
            }
            NotificationChannel channel = manager.getNotificationChannel(
                    InventoryNotificationChannels.INVENTORY_ALERTS_CHANNEL_ID
            );
            return channel != null &&
                    channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }


    public static void openSystemSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        try {
            activity.startActivity(intent);
        } catch (RuntimeException exception) {
            Intent fallback = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(fallback);
        }
    }
}
