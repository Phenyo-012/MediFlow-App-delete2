package com.medicationinventory.security;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;



/**
 * Checks default denial, UID-bound grants, revocation invalidation, account switching, and rejection of stale write tickets.
 */
public class RestrictedClassificationAccessGateTest {
    private final AtomicReference<String> currentUid = new AtomicReference<>("account-A");
    private RestrictedClassificationAccessGate gate;

    @Before public void setUp() {
        // Runs LiveData notifications synchronously in a local JVM test.
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        gate = new RestrictedClassificationAccessGate(currentUid::get);
    }

    @After public void tearDown() { ArchTaskExecutor.getInstance().setDelegate(null); }

    @Test public void startsDenied() {
        assertFalse(gate.isGranted());
        assertNull(gate.capture());
    }

    @Test public void grantsOnlyMatchingUid() {
        gate.grant("account-B");
        assertFalse(gate.isGranted());
        gate.grant("account-A");
        assertTrue(gate.isGranted());
        assertNotNull(gate.capture());
    }

    @Test public void revocationInvalidatesOldRefresh() {
        gate.grant("account-A");
        RestrictedClassificationAccessGate.Ticket old = gate.capture();
        gate.revoke();
        assertFalse(gate.accepts(old));
        assertFalse(gate.isGranted());
    }

    @Test public void switchingFirebaseUidDoesNotInheritAccess() {
        gate.grant("account-A");
        RestrictedClassificationAccessGate.Ticket old = gate.capture();
        currentUid.set("account-B");
        assertFalse(gate.isGranted());
        assertFalse(gate.accepts(old));
        gate.grant("account-B");
        assertTrue(gate.isGranted());
        assertFalse(gate.accepts(old));
    }

    @Test public void newlyGrantedSessionDoesNotReusePreviousTicket() {
        gate.grant("account-A");
        RestrictedClassificationAccessGate.Ticket old = gate.capture();
        gate.revoke();
        gate.grant("account-A");
        assertFalse(gate.accepts(old));
        assertTrue(gate.accepts(gate.capture()));
    }

    @Test public void invalidatedTicketCannotWrite() {
        gate.grant("account-A");
        RestrictedClassificationAccessGate.Ticket old = gate.capture();
        AtomicInteger mutations = new AtomicInteger();
        gate.runIfAuthorized(old, mutations::incrementAndGet);
        gate.revoke();
        try {
            gate.runIfAuthorized(old, mutations::incrementAndGet);
            fail("Expected a revoked ticket to be rejected");
        } catch (SecurityException expected) {
            // As intended.
        }
        assertEquals(1, mutations.get());
    }
}
