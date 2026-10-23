package com.medicationinventory.notifications;


/**
 * Handles Inventory Alert Notification Policy behavior for inventory-related Android notifications.
 */
public final class InventoryAlertNotificationPolicy {


    private InventoryAlertNotificationPolicy() { }

    public static boolean shouldNotify(String status, String type,
                                       boolean previouslyNotified) {
        return !previouslyNotified
                && "ACTIVE".equals(status)
                && ("LOW_STOCK".equals(type)
                || "NEAR_EXPIRY".equals(type)
                || "EXPIRED".equals(type));
    }


    public static boolean isAuthorized(Boolean active, String documentUid,
                                       String expectedUid, Object permissions) {
        if (!Boolean.TRUE.equals(active) || expectedUid == null || expectedUid.isEmpty()) {
            return false;
        }
        if (documentUid != null && !documentUid.equals(expectedUid)) return false;
        if (!(permissions instanceof java.util.List<?>)) return false;
        for (Object permission : (java.util.List<?>) permissions) {
            if (permission instanceof String
                    && "VIEW_ALERTS".equals(((String) permission).trim())) return true;
        }
        return false;
    }


    public static String title(String type) {
        if ("LOW_STOCK".equals(type)) return "Low stock alert";
        if ("NEAR_EXPIRY".equals(type)) return "Near-expiry alert";
        if ("EXPIRED".equals(type)) return "Expired stock alert";
        return "Inventory alert";
    }
}
