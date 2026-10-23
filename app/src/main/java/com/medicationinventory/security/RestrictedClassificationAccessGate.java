package com.medicationinventory.security;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.function.Supplier;


/**
 * Enforces Restricted Classification Access Gate rules that protect account access and locally cached data.
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


    public synchronized Ticket capture() {
        return isGranted() ? new Ticket(grantedUid, generation) : null;
    }


    public synchronized boolean accepts(Ticket ticket) {
        return ticket != null && ticket.generation == generation
                && ticket.uid.equals(grantedUid)
                && ticket.uid.equals(currentUid.get());
    }


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
