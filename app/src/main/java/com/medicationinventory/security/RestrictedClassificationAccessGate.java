package com.medicationinventory.security;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.function.Supplier;


/**
 * Provides a UID-scoped, generation-checked gate for ABC/VED data already stored on the device. Cache reads and writes remain denied until a current profile grants access; generation tickets let asynchronous work detect revocation or account changes before it mutates restricted data.
 */
public final class RestrictedClassificationAccessGate {
    private final Supplier<String> currentUid;
    private final MutableLiveData<Boolean> observableAccess =
            new MutableLiveData<>(false);
    private String grantedUid;
    private long generation;


    public RestrictedClassificationAccessGate(Supplier<String> currentUid) {
        if (currentUid == null) throw new IllegalArgumentException("Current UID provider required");
        this.currentUid = currentUid;
    }


    public synchronized void revoke() {
        grantedUid = null;
        generation++;
        observableAccess.postValue(false);
    }


    /**
     * Grants access only when the supplied UID is still the active Firebase account.
     * A change of owner advances the generation so outstanding tickets cannot cross accounts.
     */
    public synchronized void grant(String uid) {
        if (uid == null || uid.trim().isEmpty() || !uid.equals(currentUid.get())) {
            revoke();
            return;
        }
        if (!uid.equals(grantedUid)) generation++;
        grantedUid = uid;
        observableAccess.postValue(true);
    }


    public LiveData<Boolean> observeAccess() {
        return observableAccess;
    }

    public synchronized boolean isGranted() {
        return grantedUid != null && grantedUid.equals(currentUid.get());
    }


    /** Captures the current UID and generation for a later guarded asynchronous cache operation. */
    public synchronized Ticket capture() {
        return isGranted() ? new Ticket(grantedUid, generation) : null;
    }


    /** Rejects null, stale-generation, prior-owner, and no-longer-current-account tickets. */
    public synchronized boolean accepts(Ticket ticket) {
        return ticket != null && ticket.generation == generation
                && ticket.uid.equals(grantedUid)
                && ticket.uid.equals(currentUid.get());
    }


    /** Runs a cache mutation only while its captured authorization ticket remains valid. */
    public synchronized void runIfAuthorized(Ticket ticket, Runnable mutation) {
        if (!accepts(ticket)) {
            throw new SecurityException("ABC/VED access changed before cache mutation.");
        }
        mutation.run();
    }

    public static final class Ticket {
        private final String uid;
        private final long generation;

        private Ticket(String uid, long generation) {
            this.uid = uid;
            this.generation = generation;
        }
    }
}
