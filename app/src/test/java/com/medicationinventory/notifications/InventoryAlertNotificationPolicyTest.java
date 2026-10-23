package com.medicationinventory.notifications;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;


/**
 * Verifies notification eligibility for active alerts and suppression after acknowledgment, resolution, delivery, or missing explicit permission.
 */
public class InventoryAlertNotificationPolicyTest {

    @Test public void newActiveAlertCanNotify() {
        for (String type : new String[]{"LOW_STOCK", "NEAR_EXPIRY", "EXPIRED"}) {
            assertTrue(InventoryAlertNotificationPolicy.shouldNotify("ACTIVE", type, false));
        }
    }

    @Test public void acknowledgedResolvedAndDeliveredAreSuppressed() {
        assertFalse(InventoryAlertNotificationPolicy.shouldNotify("ACKNOWLEDGED", "LOW_STOCK", false));
        assertFalse(InventoryAlertNotificationPolicy.shouldNotify("RESOLVED", "EXPIRED", false));
        assertFalse(InventoryAlertNotificationPolicy.shouldNotify("ACTIVE", "LOW_STOCK", true));
        assertFalse(InventoryAlertNotificationPolicy.shouldNotify("ACTIVE", "INVALID", false));
    }

    @Test public void explicitActivePermissionIsRequired() {
        assertTrue(InventoryAlertNotificationPolicy.isAuthorized(true, "user1", "user1",
                Arrays.asList("VIEW_MEDICINES", "VIEW_ALERTS")));
        assertFalse(InventoryAlertNotificationPolicy.isAuthorized(false, "user1", "user1",
                Collections.singletonList("VIEW_ALERTS")));
        assertFalse(InventoryAlertNotificationPolicy.isAuthorized(true, "user2", "user1",
                Collections.singletonList("VIEW_ALERTS")));
        assertFalse(InventoryAlertNotificationPolicy.isAuthorized(true, "user1", "user1",
                Collections.singletonList("VIEW_MEDICINES")));
        assertFalse(InventoryAlertNotificationPolicy.isAuthorized(true, "user1", "user1", null));
    }

    @Test public void labelsAreStable() {
        assertEquals("Low stock alert", InventoryAlertNotificationPolicy.title("LOW_STOCK"));
        assertEquals("Near-expiry alert", InventoryAlertNotificationPolicy.title("NEAR_EXPIRY"));
        assertEquals("Expired stock alert", InventoryAlertNotificationPolicy.title("EXPIRED"));
    }
}
