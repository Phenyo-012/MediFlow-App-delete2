package com.medicationinventory.sync;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.InvalidationTracker;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.QuerySnapshot;
import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.mapper.FirestoreHistoryDocumentMapper;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Reads confirmed operation and transaction collections from Firestore for the active Firebase UID, maps only valid server records, and imports them together with Room transaction protection. It waits until both snapshots are available and required medicine/batch parents exist; existing local operations are accepted only when their confirmed identity matches, preserving pending or conflicting local state.
 */
public final class SharedHistoryCloudSyncCoordinator {
    private static final String TAG = "SharedHistorySync";
    private final FirebaseAuth auth;
    private final FirebaseFirestore cloud;
    private final AppDatabase room;
    private final AppExecutors executors;
    private final MedicineDao medicineDao;
    private final BatchDao batchDao;
    private final InventoryOperationDao operationDao;
    private final AllocationDao allocationDao;
    private final TransactionDao transactionDao;
    private final FirebaseAuth.AuthStateListener authListener;
    private final InvalidationTracker.Observer parentObserver;
    private final Object gate = new Object();
    private ListenerRegistration operationListener;
    private ListenerRegistration transactionListener;
    private String currentUid;
    private List<FirestoreHistoryDocumentMapper.ConfirmedOperation> operations;
    private List<InventoryTransactionEntity> transactions;
    private boolean started;


    public SharedHistoryCloudSyncCoordinator(FirebaseAuth auth, FirebaseFirestore cloud,
                                             AppDatabase room, AppExecutors executors) {
        if (auth == null || cloud == null || room == null || executors == null)
            throw new IllegalArgumentException("History sync requires all dependencies.");
        this.auth = auth;
        this.cloud = cloud;
        this.room = room;
        this.executors = executors;
        medicineDao = room.medicineDao();
        batchDao = room.batchDao();
        operationDao = room.inventoryOperationDao();
        allocationDao = room.allocationDao();
        transactionDao = room.transactionDao();
        authListener = unused -> onAuthChanged();
        parentObserver = new InvalidationTracker.Observer("medicines", "batches") {
            @Override public void onInvalidated(@NonNull Set<String> tables) {
                scheduleImport();
            }
        };
    }

    public void start() {
        synchronized (gate) {
            if (started) return;
            started = true;
        }
        room.getInvalidationTracker().addObserver(parentObserver);
        auth.addAuthStateListener(authListener);
    }

    public void stop() {
        synchronized (gate) {
            started = false;
            unsubscribe();
        }
        auth.removeAuthStateListener(authListener);
        room.getInvalidationTracker().removeObserver(parentObserver);
    }

    private void onAuthChanged() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user == null ? null : user.getUid();
        synchronized (gate) {
            if (!started || (uid != null && uid.equals(currentUid))) return;
            unsubscribe();
            if (uid == null) return;
            currentUid = uid;
            operationListener = cloud.collection("inventoryOperations")
                    .addSnapshotListener(MetadataChanges.INCLUDE,
                            (snapshot, error) -> onOperations(uid, snapshot, error));
            transactionListener = cloud.collection("inventoryTransactions")
                    .addSnapshotListener(MetadataChanges.INCLUDE,
                            (snapshot, error) -> onTransactions(uid, snapshot, error));
        }
    }

    private void unsubscribe() {
        if (operationListener != null) operationListener.remove();
        if (transactionListener != null) transactionListener.remove();
        operationListener = null;
        transactionListener = null;
        operations = null;
        transactions = null;
        currentUid = null;
    }

    private boolean active(String uid) {
        FirebaseUser user = auth.getCurrentUser();
        synchronized (gate) {
            return started && uid != null && uid.equals(currentUid)
                    && user != null && uid.equals(user.getUid());
        }
    }

    /**
     * Accepts only a server-confirmed operation snapshot for the still-active UID, maps each
     * document through the schema validator, and stores the mapped set for a later ledger import.
     * Cache snapshots are ignored so unconfirmed local Firestore writes cannot be mirrored into
     * Room as shared history.
     */
    private void onOperations(String uid, QuerySnapshot snapshot, Exception error) {
        if (!active(uid)) return;
        if (error != null) {
            Log.w(TAG, "Cannot list shared operations; verify VIEW_INVENTORY_HISTORY and rules.", error);
            synchronized (gate) { if (uid.equals(currentUid)) operations = null; }
            return;
        }
        if (snapshot == null || snapshot.getMetadata().isFromCache()
                || snapshot.getMetadata().hasPendingWrites()) return;
        List<FirestoreHistoryDocumentMapper.ConfirmedOperation> mapped = new ArrayList<>();
        int rejected = 0;
        for (DocumentSnapshot item : snapshot.getDocuments()) {
            try {
                FirestoreHistoryDocumentMapper.ConfirmedOperation op =
                        FirestoreHistoryDocumentMapper.confirmedOperation(item.getId(), item.getData());
                if (op != null) mapped.add(op);
            } catch (IllegalArgumentException badRecord) {
                rejected++;
                Log.w(TAG, "Malformed operation: " + item.getId() + ": " + badRecord.getMessage());
            }
        }
        synchronized (gate) { if (!uid.equals(currentUid)) return; operations = mapped; }
        Log.i(TAG, "Server confirmed operations=" + mapped.size() + ", malformed=" + rejected);
        scheduleImport();
    }

    /**
     * Maps confirmed transaction documents and holds the resulting set until the operation
     * snapshot is also available. Keeping both collections together lets import verify each
     * transaction against its confirmed parent operation and batch before writing the ledger.
     */
    private void onTransactions(String uid, QuerySnapshot snapshot, Exception error) {
        if (!active(uid)) return;
        if (error != null) {
            Log.w(TAG, "Cannot list shared transactions; verify VIEW_INVENTORY_HISTORY and rules.", error);
            synchronized (gate) { if (uid.equals(currentUid)) transactions = null; }
            return;
        }
        if (snapshot == null || snapshot.getMetadata().isFromCache()
                || snapshot.getMetadata().hasPendingWrites()) return;
        List<InventoryTransactionEntity> mapped = new ArrayList<>();
        int rejected = 0;
        for (DocumentSnapshot item : snapshot.getDocuments()) {
            try {
                mapped.add(FirestoreHistoryDocumentMapper.confirmedTransaction(
                        item.getId(), item.getData()));
            } catch (IllegalArgumentException badRecord) {
                rejected++;
                Log.w(TAG, "Malformed transaction: " + item.getId() + ": " + badRecord.getMessage());
            }
        }
        synchronized (gate) { if (!uid.equals(currentUid)) return; transactions = mapped; }
        Log.i(TAG, "Server transactions=" + mapped.size() + ", malformed=" + rejected);
        scheduleImport();
    }

    /**
     * Takes defensive copies only after both server snapshots are ready, then moves import work to
     * the database executor. This avoids mutating listener-owned lists while a subsequent snapshot
     * arrives and keeps Firestore callbacks free of Room transaction work.
     */
    private void scheduleImport() {
        final String uid;
        final List<FirestoreHistoryDocumentMapper.ConfirmedOperation> ops;
        final List<InventoryTransactionEntity> txs;
        synchronized (gate) {
            if (!started || currentUid == null || operations == null || transactions == null) return;
            uid = currentUid;
            ops = new ArrayList<>(operations);
            txs = new ArrayList<>(transactions);
        }
        executors.databaseIO().execute(() -> importLedger(uid, ops, txs));
    }

    /**
     * Imports confirmed history in one Room transaction without applying stock quantities again.
     * It verifies the UID throughout the transaction, requires medicine and batch parents, and
     * skips pending local operations or provisional movements that would conflict with a cloud
     * record. An account switch throws a sentinel so all writes from that import roll back.
     */
    private void importLedger(String uid,
                              List<FirestoreHistoryDocumentMapper.ConfirmedOperation> ops,
                              List<InventoryTransactionEntity> txs) {
        if (!active(uid)) return;
        int[] count = new int[4];
        try {
            room.runInTransaction(() -> {
                Map<String, InventoryOperationEntity> knownConfirmed = new HashMap<>();
                for (FirestoreHistoryDocumentMapper.ConfirmedOperation remote : ops) {
                    if (!active(uid)) throw new AccountSwitched();
                    InventoryOperationEntity source = remote.operation;
                    String opId = source.getOperationId();
                    InventoryOperationEntity existing = operationDao.getById(opId);
                    if (existing != null) {
                        if (existing.getStatus() == InventoryOperationStatus.CONFIRMED
                                && existing.getMedicineId().equals(source.getMedicineId())
                                && existing.getOperationType() == source.getOperationType()
                                && existing.getPerformedBy().equals(source.getPerformedBy())) {
                            knownConfirmed.put(opId, existing);
                        } else {

                            count[3]++;
                        }
                        continue;
                    }
                    if (medicineDao.getById(source.getMedicineId()) == null) {
                        count[2]++;
                        continue;
                    }
                    boolean missingBatch = false;
                    for (OperationAllocationEntity allocation : remote.allocations) {
                        BatchEntity parent = batchDao.getById(allocation.getBatchId());
                        if (parent == null || !source.getMedicineId().equals(parent.getMedicineId())) {
                            missingBatch = true;
                            break;
                        }
                    }
                    if (missingBatch) {
                        count[2]++;
                        continue;
                    }
                    operationDao.insertLocalOperation(source);
                    allocationDao.insertAllocations(remote.allocations);
                    knownConfirmed.put(opId, source);
                    count[0]++;
                }
                for (InventoryTransactionEntity remote : txs) {
                    if (!active(uid)) throw new AccountSwitched();
                    InventoryOperationEntity parent = knownConfirmed.get(remote.getOperationId());
                    if (parent == null || parent.getStatus() != InventoryOperationStatus.CONFIRMED) {
                        count[2]++;
                        continue;
                    }
                    BatchEntity batch = batchDao.getById(remote.getBatchId());
                    if (batch == null || !remote.getMedicineId().equals(parent.getMedicineId())
                            || !remote.getMedicineId().equals(batch.getMedicineId())
                            || !remote.getPerformedBy().equals(parent.getPerformedBy())
                            || !remote.getTransactionType().equals(parent.getOperationType().name())) {
                        count[3]++;
                        continue;
                    }

                    boolean alreadyExists = false;
                    boolean hasLocalProvisional = false;
                    for (InventoryTransactionEntity local :
                            transactionDao.getForOperation(remote.getOperationId())) {
                        if (local.isProvisional()) hasLocalProvisional = true;
                        if (remote.getTransactionId().equals(local.getTransactionId())) {
                            alreadyExists = true;
                            if (!remote.getMedicineId().equals(local.getMedicineId())
                                    || !remote.getBatchId().equals(local.getBatchId())
                                    || remote.getQuantityChange() != local.getQuantityChange()
                                    || !remote.getTransactionType().equals(local.getTransactionType())) {
                                count[3]++;
                            }
                            break;
                        }
                    }
                    if (hasLocalProvisional) {

                        count[2]++;
                        continue;
                    }
                    if (!alreadyExists) {

                        transactionDao.insertProvisional(remote);
                        count[1]++;
                    }
                }
                if (!active(uid)) throw new AccountSwitched();
            });
            Log.i(TAG, "MS2.2 history: new operations=" + count[0]
                    + ", new confirmed transactions=" + count[1]
                    + ", deferred=" + count[2] + ", collisions/guarded=" + count[3]
                    + ". No stock movement replayed.");
        } catch (AccountSwitched ignored) {
            Log.i(TAG, "History import cancelled: account changed; transaction rolled back.");
        } catch (Exception failure) {
            Log.e(TAG, "History import failed and rolled back. Cloud and stock untouched.", failure);
        }
    }

    private static final class AccountSwitched extends RuntimeException { }
}
