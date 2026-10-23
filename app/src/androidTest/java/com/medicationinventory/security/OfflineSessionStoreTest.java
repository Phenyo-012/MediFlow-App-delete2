package com.medicationinventory.security;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;



/**
 * Verifies that an offline session is tied to its enrolled UID and is rejected after its expiry.
 */
@RunWith(AndroidJUnit4.class)
public class OfflineSessionStoreTest {
    @Test public void enrollmentIsBoundToUidAndExpires() {
        OfflineSessionStore store = new OfflineSessionStore(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                "mediflow_offline_session_instrumentation_test");
        store.clear();
        long now = System.currentTimeMillis();
        assertTrue(store.enroll("offline-test-user", now));
        assertTrue(store.isEligible("offline-test-user"));
        assertFalse(store.isEligible("different-user"));
        store.clear();
        assertFalse(store.isEligible("offline-test-user"));
        assertFalse(store.enroll("offline-test-user", now - OfflineSessionStore.MAX_OFFLINE_AGE_MS));
        assertFalse(store.isEligible("offline-test-user"));
    }
}
