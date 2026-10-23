package com.medicationinventory.notifications;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Checks default notification selections, global pause, per-type opt-out, fail-closed handling of unknown types, and selection counts.
 */
public class InventoryNotificationPreferencePolicyTest {
    @Test public void defaultsPermitAllThreeTypes() {
        assertTrue(InventoryNotificationPreferencePolicy.allows(true, true, true, true, "LOW_STOCK"));
        assertTrue(InventoryNotificationPreferencePolicy.allows(true, true, true, true, "NEAR_EXPIRY"));
        assertTrue(InventoryNotificationPreferencePolicy.allows(true, true, true, true, "EXPIRED"));
    }

    @Test public void pauseBlocksEveryType() {
        for (String type : new String[]{"LOW_STOCK", "NEAR_EXPIRY", "EXPIRED"}) {
            assertFalse(InventoryNotificationPreferencePolicy.allows(false, true, true, true, type));
        }
    }

    @Test public void switchingOffSingleTypeDoesNotSuppressOthers() {
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, false, true, true, "LOW_STOCK"));
        assertTrue(InventoryNotificationPreferencePolicy.allows(true, false, true, true, "NEAR_EXPIRY"));
        assertTrue(InventoryNotificationPreferencePolicy.allows(true, false, true, true, "EXPIRED"));
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, true, false, true, "NEAR_EXPIRY"));
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, true, true, false, "EXPIRED"));
    }

    @Test public void unexpectedTypeFailsClosed() {
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, true, true, true, null));
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, true, true, true, "OTHER"));
        assertFalse(InventoryNotificationPreferencePolicy.allows(true, true, true, true, ""));
    }

    @Test public void typeCountTracksSelections() {
        assertEquals(3, InventoryNotificationPreferencePolicy.enabledTypeCount(true, true, true));
        assertEquals(2, InventoryNotificationPreferencePolicy.enabledTypeCount(true, false, true));
        assertEquals(1, InventoryNotificationPreferencePolicy.enabledTypeCount(false, true, false));
        assertEquals(0, InventoryNotificationPreferencePolicy.enabledTypeCount(false, false, false));
    }
}
