package com.medicationinventory.security;



/**
 * Enforces Inactivity Timeout Policy rules that protect account access and locally cached data.
 */
public final class InactivityTimeoutPolicy {

    private InactivityTimeoutPolicy() { }

    /**
     * Evaluates a monotonic inactivity interval; invalid timeout or timestamp state expires immediately.
     * @param lastInteraction elapsed-realtime timestamp of the last accepted input
     * @param now current elapsed-realtime timestamp
     * @param timeoutMs maximum allowed idle interval
     * @return true when the interval has reached the timeout or its timestamps are invalid
     */
    public static boolean isExpired(long lastInteraction, long now, long timeoutMs) {
        // A missing timestamp or a backwards monotonic clock must fail closed.
        return timeoutMs <= 0L || lastInteraction < 0L || now < lastInteraction
                || now - lastInteraction >= timeoutMs;
    }
}
