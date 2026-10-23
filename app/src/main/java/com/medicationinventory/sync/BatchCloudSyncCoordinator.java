package com.medicationinventory.sync;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.InvalidationTracker;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.mapper.FirestoreBatchDocumentMapper;
import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Imports server-confirmed batch snapshots into Room for the active Firebase UID. It waits for the matching medicine master, defers all medicine batches while a local stock operation is unresolved, rejects ID or medicine/batch-number collisions, and only replaces a local batch when the cloud timestamp is newer. The transaction is cancelled on account switch and retried after relevant local table changes.
 */
public final class BatchCloudSyncCoordinator {
    private static final String TAG = "BatchCloudSync";
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AppDatabase room;
    private final AppExecutors executors;
    private final MedicineDao medicines;
    private final BatchDao batches;
    private final FirebaseAuth.AuthStateListener authListener;
    private final InvalidationTracker.Observer medicineObserver;
    private final Object gate = new Object();
    private ListenerRegistration registration;
    private String subscriptionUid;
    private List<BatchEntity> lastServerBatches;
    private boolean started;


    public BatchCloudSyncCoordinator(FirebaseAuth auth, FirebaseFirestore firestore,
                                     AppDatabase room, AppExecutors executors) {
        if (auth == null || firestore == null || room == null || executors == null) {
            throw new IllegalArgumentException("MS2.1 dependencies are required.");
        }
        this.auth = auth;
        this.firestore = firestore;
        this.room = room;
        this.executors = executors;
        this.medicines = room.medicineDao();
        this.batches = room.batchDao();
        authListener = unused -> updateSubscription();
        medicineObserver = new InvalidationTracker.Observer("medicines", "inventory_operations") {
            @Override public void onInvalidated(@NonNull Set<String> tables) {
                retryDeferredAfterLocalChanges();
            }
        };
    }

    public void start() {
        synchronized (gate) {
            if (started) return;
            started = true;
        }
        room.getInvalidationTracker().addObserver(medicineObserver);
        auth.addAuthStateListener(authListener);
    }

    public void stop() {
        synchronized (gate) {
            if (!started) return;
            started = false;
            closeSubscriptionLocked();
        }
        auth.removeAuthStateListener(authListener);
        room.getInvalidationTracker().removeObserver(medicineObserver);
    }

    private void updateSubscription() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user == null ? null : user.getUid();
        synchronized (gate) {
            if (!started) return;
            if (uid != null && uid.equals(subscriptionUid)) return;
            closeSubscriptionLocked();
            if (uid == null) return;
            subscriptionUid = uid;
            registration = firestore.collection("batches")
                    .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                        if (!sameUid(uid)) return;
                        if (error != null) {
                            Log.w(TAG, "Batch read denied/unavailable. Check VIEW_BATCHES and Firestore rules.", error);
                            return;
                        }
                        if (snapshot == null || snapshot.getMetadata().isFromCache()
                                || snapshot.getMetadata().hasPendingWrites()) {
                            // Never treat an offline Firestore cache as an authorised server response.
                            return;
                        }
                        parseServerSnapshot(uid, snapshot);
                    });
        }
    }

    private void closeSubscriptionLocked() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        subscriptionUid = null;
        lastServerBatches = null;
    }

    private boolean sameUid(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        synchronized (gate) {
            return started && current != null && uid.equals(current.getUid())
                    && uid.equals(subscriptionUid);
        }
    }

    private void parseServerSnapshot(String uid, QuerySnapshot snapshot) {
        List<BatchEntity> incoming = new ArrayList<>();
        int invalid = 0;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            try {
                incoming.add(FirestoreBatchDocumentMapper.fromDocument(
                        doc.getId(), doc.getData()));
            } catch (IllegalArgumentException exception) {
                invalid++;
                Log.w(TAG, "Invalid batch " + doc.getId() + ": " + exception.getMessage());
            }
        }
        synchronized (gate) {
            if (!uid.equals(subscriptionUid)) return;
            lastServerBatches = incoming;
        }
        applySnapshot(uid, incoming, invalid);
    }

    private void retryDeferredAfterLocalChanges() {
        String uid;
        List<BatchEntity> cached;
        synchronized (gate) {
            uid = subscriptionUid;
            cached = lastServerBatches;
        }
        if (uid != null && cached != null) applySnapshot(uid, cached, 0);
    }

    private void applySnapshot(String uid, List<BatchEntity> incoming, int invalid) {
        executors.databaseIO().execute(() -> {
            if (!sameUid(uid)) return;
            int[] count = new int[5]; // inserted, updated, upToDate, deferred, collisions
            try {
                room.runInTransaction(() -> {
                    SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
                    for (BatchEntity remote : incoming) {
                        if (!sameUid(uid)) throw new CancelledImport();
                        MedicineEntity parent = medicines.getById(remote.getMedicineId());
                        if (parent == null || hasUnresolvedLocalOperation(db, remote.getMedicineId())) {
                            count[3]++;
                            continue;
                        }
                        BatchEntity local = batches.getById(remote.getBatchId());
                        if (local == null) {
                            if (batches.getByMedicineAndBatchNumber(remote.getMedicineId(),
                                    remote.getBatchNumber()) != null) {
                                count[4]++;
                                continue;
                            }
                            batches.upsert(remote);
                            count[0]++;
                        } else if (!remote.getMedicineId().equals(local.getMedicineId())
                                || !remote.getBatchNumber().equals(local.getBatchNumber())) {
                            count[4]++;
                        } else if (remote.getUpdatedAt() > local.getUpdatedAt()) {
                            batches.upsert(remote);
                            count[1]++;
                        } else {
                            count[2]++;
                        }
                    }
                });
                Log.i(TAG, "MS2.1 batches: " + count[0] + " new, " + count[1]
                        + " newer cloud updates, " + count[2] + " unchanged/older, "
                        + count[3] + " deferred, " + count[4] + " ID/code conflicts, "
                        + invalid + " malformed cloud records.");
            } catch (CancelledImport ignored) {
                Log.i(TAG, "MS2.1 cancelled due to account switch. No partial changes.");
            } catch (Exception e) {
                Log.e(TAG, "MS2.1 batch import rolled back. Room data preserved.", e);
            }
        });
    }

    private static boolean hasUnresolvedLocalOperation(SupportSQLiteDatabase db, String medicineId) {
        try (android.database.Cursor cursor = db.query(
                "SELECT 1 FROM inventory_operations WHERE medicineId = ? "
                        + "AND status IN ('LOCAL_PENDING', 'SUBMITTED') LIMIT 1",
                new Object[]{medicineId})) {
            return cursor.moveToFirst();
        }
    }

    private static final class CancelledImport extends RuntimeException { }
}
