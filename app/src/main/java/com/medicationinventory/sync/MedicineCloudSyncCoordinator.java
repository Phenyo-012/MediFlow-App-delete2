package com.medicationinventory.sync;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.SyncMetadataDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.data.mapper.FirestoreMedicineDocumentMapper;
import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Listens to server-confirmed Firestore medicine snapshots for the current Firebase user and merges them into Room. It rejects malformed documents, protects local edits using a per-user/per-medicine fingerprint checkpoint, defers updates while stock operations are pending, and avoids medicine-code collisions. Snapshot import is transactional and cancelled if the account changes; shared category options are cleared on sign-out or account switch.
 */
public final class MedicineCloudSyncCoordinator {
    private static final String TAG = "MedicineCloudSync";
    private static final String CHECKPOINT_PREFIX = "medicine-master-server:";
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final AppDatabase room;
    private final MedicineDao medicines;
    private final SyncMetadataDao checkpoints;
    private final AppExecutors executors;
    private final MutableLiveData<List<String>> sharedCategories =
            new MutableLiveData<>(Collections.emptyList());
    private final FirebaseAuth.AuthStateListener authListener;
    private final Object gate = new Object();
    private ListenerRegistration registration;
    private String subscriptionUid;
    private boolean started;

    private long subscriptionGeneration;

    public MedicineCloudSyncCoordinator(FirebaseAuth auth, FirebaseFirestore firestore,
                                        AppDatabase room, AppExecutors executors) {
        if (auth == null || firestore == null || room == null || executors == null)
            throw new IllegalArgumentException("Medicine sync dependencies are required.");
        this.auth = auth;
        this.firestore = firestore;
        this.room = room;
        this.medicines = room.medicineDao();
        this.checkpoints = room.syncMetadataDao();
        this.executors = executors;
        authListener = unused -> updateSubscription();
    }


    public LiveData<List<String>> observeSharedCategories() {
        return sharedCategories;
    }


    public void start() {
        synchronized (gate) {
            if (started) return;
            started = true;
        }
        auth.addAuthStateListener(authListener);
    }


    public void stop() {
        synchronized (gate) {
            started = false;
            closeSubscriptionLocked();
        }
        auth.removeAuthStateListener(authListener);
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
            registration = firestore.collection("medicines")
                    .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                        if (!sameUser(uid)) return;
                        if (error != null) {
                            Log.w(TAG, "Medicine read denied/unavailable; check VIEW_MEDICINES.", error);
                            return;
                        }
                        if (snapshot == null || snapshot.getMetadata().isFromCache() ||
                                snapshot.getMetadata().hasPendingWrites()) return;
                        importSnapshot(uid, snapshot);
                    });
        }
    }


    private void closeSubscriptionLocked() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        subscriptionUid = null;
        final long generation = ++subscriptionGeneration;
        // Clear on logout AND account switch, before any new server data is shown.
        executors.mainThread().execute(() -> {
            synchronized (gate) {
                if (generation != subscriptionGeneration) return;
            }
            sharedCategories.setValue(Collections.emptyList());
        });
    }

    private boolean sameUser(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        synchronized (gate) {
            return started && current != null && uid.equals(current.getUid())
                    && uid.equals(subscriptionUid);
        }
    }

    private void importSnapshot(String uid, QuerySnapshot snapshot) {
        List<MedicineEntity> incoming = new ArrayList<>();
        List<String> activeRemoteCategories = new ArrayList<>();
        int invalid = 0;
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            try {
                MedicineEntity remote = FirestoreMedicineDocumentMapper.fromDocument(
                        document.getId(), document.getData());
                incoming.add(remote);
                if (remote.isActive()) activeRemoteCategories.add(remote.getCategory());
            } catch (IllegalArgumentException malformed) {
                invalid++;
                Log.w(TAG, "Skipped malformed medicine " + document.getId(), malformed);
            }
        }
        final int invalidCount = invalid;

        List<String> confirmed = Collections.unmodifiableList(
                new ArrayList<>(activeRemoteCategories));
        executors.mainThread().execute(() -> {
            if (sameUser(uid)) sharedCategories.setValue(confirmed);
        });
        executors.databaseIO().execute(() -> {
            if (!sameUser(uid)) return;
            final int[] count = new int[6];
            try {
                room.runInTransaction(() -> {
                    SupportSQLiteDatabase db = room.getOpenHelper().getWritableDatabase();
                    for (MedicineEntity remote : incoming) {
                        if (!sameUser(uid)) throw new CancelledImport();
                        MedicineEntity local = medicines.getById(remote.getMedicineId());
                        String key = checkpointKey(uid, remote.getMedicineId());
                        SyncMetadataEntity recorded = checkpoints.getByKey(key);
                        String lastFingerprint = recorded == null ? null : recorded.getValue();
                        MedicineCloudImportPolicy.Decision decision =
                                MedicineCloudImportPolicy.evaluate(local, remote, lastFingerprint);
                        if (decision == MedicineCloudImportPolicy.Decision.INSERT ||
                                decision == MedicineCloudImportPolicy.Decision.UPDATE) {

                            if (decision == MedicineCloudImportPolicy.Decision.UPDATE &&
                                    hasPendingStockOperation(db, remote.getMedicineId())) {
                                count[4]++;
                                continue;
                            }

                            MedicineEntity owner = medicines.getByCode(remote.getMedicineCode());
                            if (owner != null && !owner.getMedicineId().equals(remote.getMedicineId())) {
                                count[5]++;
                                continue;
                            }
                            medicines.upsert(remote);
                            saveCheckpoint(key, remote);
                            count[decision == MedicineCloudImportPolicy.Decision.INSERT ? 0 : 1]++;
                        } else if (decision == MedicineCloudImportPolicy.Decision.BASELINE) {

                            saveCheckpoint(key, remote);
                            count[2]++;
                        } else if (decision == MedicineCloudImportPolicy.Decision.UNCHANGED ||
                                decision == MedicineCloudImportPolicy.Decision.IGNORE_OLDER) {
                            count[3]++;
                        } else {

                            count[4]++;
                        }
                    }
                });
                Log.i(TAG, "Medicine sync: " + count[0] + " new, " + count[1]
                        + " updated, " + count[2] + " baselined, " + count[3]
                        + " unchanged/older, " + count[4] + " deferred, "
                        + count[5] + " code conflicts, " + invalidCount + " malformed.");
            } catch (CancelledImport ignored) {
                Log.i(TAG, "Medicine sync cancelled on account change; transaction rolled back.");
            } catch (Exception error) {
                Log.e(TAG, "Medicine sync failed; Room transaction rolled back.", error);
            }
        });
    }


    private static boolean hasPendingStockOperation(SupportSQLiteDatabase db,
                                                    String medicineId) {
        try (Cursor cursor = db.query(
                "SELECT 1 FROM inventory_operations WHERE medicineId = ? "
                        + "AND status IN ('LOCAL_PENDING', 'SUBMITTED') LIMIT 1",
                new Object[]{medicineId})) {
            return cursor.moveToFirst();
        }
    }


    private void saveCheckpoint(String key, MedicineEntity remote) {
        checkpoints.upsert(new SyncMetadataEntity(key, System.currentTimeMillis(),
                remote.getUpdatedAt(), MedicineCloudImportPolicy.fingerprint(remote)));
    }


    private static String checkpointKey(String uid, String medicineId) {
        return CHECKPOINT_PREFIX + uid + ":" + medicineId;
    }

    private static final class CancelledImport extends RuntimeException { }
}
