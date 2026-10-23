package com.medicationinventory.sync;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.medicationinventory.data.local.dao.MedicineBarcodeDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.SyncMetadataDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.service.SharedBarcodePolicy;
import com.medicationinventory.util.AppExecutors;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Listens to barcode mapping snapshots for the current Firebase user and imports validated mappings into Room. Listener changes are bound to UID, and shared mapping documents are checked against their barcode/document identity before local insertion.
 */
public final class MedicineBarcodeCloudSyncCoordinator {
    private static final String TAG = "BarcodeCloudSync";
    private static final String PREFIX = "medicine-barcode-server:";
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AppDatabase room;
    private final MedicineDao medicines;
    private final MedicineBarcodeDao barcodes;
    private final SyncMetadataDao checkpoints;
    private final AppExecutors executors;
    private final LiveData<List<MedicineEntity>> observedMedicines;
    private final Observer<List<MedicineEntity>> medicineObserver;
    private final FirebaseAuth.AuthStateListener authListener;
    private final Object gate = new Object();
    private ListenerRegistration listener;
    private String subscriptionUid;
    private Map<String, RemoteBarcode> latest;
    private boolean started;

    public MedicineBarcodeCloudSyncCoordinator(FirebaseAuth auth, FirebaseFirestore firestore,
                                               AppDatabase room, AppExecutors executors) {
        this.auth = auth;
        this.firestore = firestore;
        this.room = room;
        this.medicines = room.medicineDao();
        this.barcodes = room.medicineBarcodeDao();
        this.checkpoints = room.syncMetadataDao();
        this.executors = executors;
        this.observedMedicines = medicines.observeActiveMedicines();
        this.medicineObserver = ignored -> applyLatest();
        this.authListener = ignored -> updateSubscription();
    }


    public void start() {
        synchronized (gate) { if (started) return; started = true; }
        observedMedicines.observeForever(medicineObserver);
        auth.addAuthStateListener(authListener);
    }

    public void stop() {
        synchronized (gate) { started = false; closeLocked(); }
        auth.removeAuthStateListener(authListener);
        observedMedicines.removeObserver(medicineObserver);
    }

    private void updateSubscription() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user == null ? null : user.getUid();
        synchronized (gate) {
            if (!started || (uid != null && uid.equals(subscriptionUid))) return;
            closeLocked();
            if (uid == null) return;
            subscriptionUid = uid;
            listener = firestore.collection("medicineBarcodes")
                    .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                        if (!sameUser(uid)) return;
                        if (error != null) {
                            Log.w(TAG, "Shared barcode read denied or offline.", error);
                            return;
                        }
                        if (snapshot == null || snapshot.getMetadata().isFromCache() ||
                                snapshot.getMetadata().hasPendingWrites()) return;
                        handleSnapshot(uid, snapshot);
                    });
        }
    }


    private void closeLocked() {
        if (listener != null) listener.remove();
        listener = null;
        subscriptionUid = null;
        latest = null;
    }

    private boolean sameUser(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        synchronized (gate) {
            return started && current != null && uid.equals(current.getUid()) &&
                    uid.equals(subscriptionUid);
        }
    }

    private void handleSnapshot(String uid, QuerySnapshot snapshot) {
        Map<String, RemoteBarcode> incoming = new HashMap<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            try {
                String barcode = document.getString("barcode");
                String medicineId = document.getString("medicineId");
                if (!SharedBarcodePolicy.validCloudMapping(document.getId(), barcode, medicineId)) {
                    // Invalid cloud data must never trigger deletion of an imported link.
                    Log.w(TAG, "Invalid cloud barcode record; skipping entire sync.");
                    return;
                }
                com.google.firebase.Timestamp stamp = document.getTimestamp("createdAt");
                long createdAt = stamp == null ? System.currentTimeMillis() :
                        stamp.toDate().getTime();
                incoming.put(document.getId(), new RemoteBarcode(barcode, medicineId, createdAt));
            } catch (RuntimeException invalid) {
                Log.w(TAG, "Malformed cloud barcode record; skipping entire sync.", invalid);
                return;
            }
        }
        synchronized (gate) {
            if (!sameUser(uid)) return;
            latest = Collections.unmodifiableMap(incoming);
        }
        applyLatest();
    }


    private void applyLatest() {
        final Map<String, RemoteBarcode> snapshot;
        final String uid;
        synchronized (gate) {
            if (!started || subscriptionUid == null || latest == null) return;
            uid = subscriptionUid;
            snapshot = latest;
        }
        executors.databaseIO().execute(() -> {
            if (!sameUser(uid)) return;
            try {
                room.runInTransaction(() -> {
                    if (!sameUser(uid)) throw new CancelledSync();
                    String prefix = PREFIX + uid + ":";
                    List<SyncMetadataEntity> old = checkpoints.getByPrefix(prefix);
                    for (Map.Entry<String, RemoteBarcode> item : snapshot.entrySet()) {
                        if (!sameUser(uid)) throw new CancelledSync();
                        RemoteBarcode remote = item.getValue();
                        MedicineEntity medicine = medicines.getById(remote.medicineId);
                        if (medicine == null) continue; // Retry when medicine enters Room.
                        MedicineEntity codeOwner = medicines.getByCode(remote.barcode);
                        if (codeOwner != null && !remote.medicineId.equals(codeOwner.getMedicineId()))
                            continue;
                        MedicineBarcodeEntity local = barcodes.getByBarcode(remote.barcode);
                        if (local != null && !remote.medicineId.equals(local.getMedicineId()))
                            continue; // Preserve unresolved local legacy ownership.
                        if (local == null) barcodes.insert(new MedicineBarcodeEntity(
                                remote.barcode, remote.medicineId, remote.createdAt));
                        checkpoints.upsert(new SyncMetadataEntity(prefix + item.getKey(),
                                System.currentTimeMillis(), remote.createdAt,
                                remote.medicineId + "\n" + remote.barcode));
                    }

                    for (SyncMetadataEntity marker : old) {
                        if (!sameUser(uid)) throw new CancelledSync();
                        String id = marker.getKey().substring(prefix.length());
                        if (snapshot.containsKey(id) || marker.getValue() == null) continue;
                        String value = marker.getValue();
                        int split = value.indexOf('\n');
                        if (split <= 0) continue;
                        String owner = value.substring(0, split);
                        String barcode = value.substring(split + 1);
                        if (!SharedBarcodePolicy.validCloudMapping(id, barcode, owner)) continue;
                        MedicineBarcodeEntity local = barcodes.getByBarcode(barcode);
                        if (local != null && owner.equals(local.getMedicineId()))
                            barcodes.unlink(barcode, owner);
                        checkpoints.upsert(new SyncMetadataEntity(marker.getKey(),
                                System.currentTimeMillis(), 0L, null));
                    }
                });
            } catch (CancelledSync ignored) {
                Log.i(TAG, "Account changed; transaction cancelled.");
            } catch (Exception error) {
                Log.e(TAG, "Barcode sync rolled back; local data preserved.", error);
            }
        });
    }

    private static final class RemoteBarcode {
        final String barcode, medicineId;
        final long createdAt;
        RemoteBarcode(String barcode, String medicineId, long createdAt) {
            this.barcode = barcode;
            this.medicineId = medicineId;
            this.createdAt = createdAt;
        }
    }
    private static final class CancelledSync extends RuntimeException { }
}
