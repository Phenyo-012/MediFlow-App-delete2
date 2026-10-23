package com.medicationinventory.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the exact inactivity deadline, window restart after interaction, and fail-closed handling of missing or regressed timestamps.
 */
public class InactivityTimeoutPolicyTest {
    private static final long FIFTEEN_MINUTES = 15L * 60L * 1000L;

    @Test public void activeJustBeforeDeadlineRemainsUnlocked() {
        assertFalse(InactivityTimeoutPolicy.isExpired(100L, 100L + FIFTEEN_MINUTES - 1L,
                FIFTEEN_MINUTES));
    }

    @Test public void lockExactlyAtDeadline() {
        assertTrue(InactivityTimeoutPolicy.isExpired(100L, 100L + FIFTEEN_MINUTES,
                FIFTEEN_MINUTES));
    }

    @Test public void interactionCanRestartTheWindow() {
        long input = 800_000L;
        assertFalse(InactivityTimeoutPolicy.isExpired(input, input + 1000L, FIFTEEN_MINUTES));
    }

    @Test public void missingAndRegressedTimestampsFailClosed() {
        assertTrue(InactivityTimeoutPolicy.isExpired(-1L, 500L, FIFTEEN_MINUTES));
        assertTrue(InactivityTimeoutPolicy.isExpired(1000L, 999L, FIFTEEN_MINUTES));
    }
}
