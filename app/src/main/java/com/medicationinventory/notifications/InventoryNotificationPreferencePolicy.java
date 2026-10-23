package com.medicationinventory.notifications;


/**
 * Applies the master switch and per-alert-type selections used by the notification worker. Unknown
 * alert types are disabled by default, so newly introduced types cannot notify until explicitly
 * supported by the preference flow.
 */
public final class InventoryNotificationPreferencePolicy {

    private InventoryNotificationPreferencePolicy() { }


    /** Returns whether the master switch and the selected type's preference both allow delivery. */
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
