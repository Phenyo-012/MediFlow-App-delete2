package com.medicationinventory.security;



/**
 * Enforces Inactivity Timeout Policy rules that protect account access and locally cached data.
 */
public final class InactivityTimeoutPolicy {

    private InactivityTimeoutPolicy() { }

    public static boolean isExpired(long lastInteraction, long now, long timeoutMs) {
        // A missing timestamp or a backwards monotonic clock must fail closed.
        return timeoutMs <= 0L || lastInteraction < 0L || now < lastInteraction
                || now - lastInteraction >= timeoutMs;
    }
}
