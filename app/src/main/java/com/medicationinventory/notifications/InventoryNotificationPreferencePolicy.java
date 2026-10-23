package com.medicationinventory.notifications;


/**
 * Handles Inventory Notification Preference Policy behavior for inventory-related Android notifications.
 */
public final class InventoryNotificationPreferencePolicy {

    private InventoryNotificationPreferencePolicy() { }


    public static boolean allows(boolean masterEnabled,
                                 boolean lowStockEnabled,
                                 boolean nearExpiryEnabled,
                                 boolean expiredEnabled,
                                 String alertType) {
        if (!masterEnabled || alertType == null) return false;
        switch (alertType) {
            case "LOW_STOCK": return lowStockEnabled;
            case "NEAR_EXPIRY": return nearExpiryEnabled;
            case "EXPIRED": return expiredEnabled;
            default: return false;
        }
    }


    public static int enabledTypeCount(boolean lowStockEnabled,
                                       boolean nearExpiryEnabled,
                                       boolean expiredEnabled) {
        return (lowStockEnabled ? 1 : 0)
                + (nearExpiryEnabled ? 1 : 0)
                + (expiredEnabled ? 1 : 0);
    }
}
