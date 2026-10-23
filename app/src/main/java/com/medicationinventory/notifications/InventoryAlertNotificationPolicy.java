package com.medicationinventory.notifications;


/**
 * Keeps notification eligibility and profile authorization rules independent of Android services.
 * Only active low-stock, near-expiry, or expired alerts can be delivered, and only once per alert
 * ID according to notification history. Profile access requires an active matching UID and the
 * server permission list to contain {@code VIEW_ALERTS}.
 */
public final class InventoryAlertNotificationPolicy {


    private InventoryAlertNotificationPolicy() { }

    /** Returns whether an active supported alert has not already been delivered. */
    public static boolean shouldNotify(String status, String type,
                                       boolean previouslyNotified) {
        return !previouslyNotified
                && "ACTIVE".equals(status)
                && ("LOW_STOCK".equals(type)
                || "NEAR_EXPIRY".equals(type)
                || "EXPIRED".equals(type));
    }


    /**
     * Validates the server profile fields used by the worker before it reads alert records.
     * Missing or wrongly typed permissions fail closed.
     */
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
