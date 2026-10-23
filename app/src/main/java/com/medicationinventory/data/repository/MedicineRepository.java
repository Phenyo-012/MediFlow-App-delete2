package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.medicationinventory.data.mapper.FirestoreMedicineDocumentMapper;
import com.medicationinventory.sync.MedicineCloudImportPolicy;
import com.medicationinventory.data.remote.backend.TrustedMedicineMasterDataSource;
import com.medicationinventory.data.remote.backend.TrustedMedicineBarcodeDataSource;
import com.medicationinventory.service.SharedBarcodePolicy;
import com.medicationinventory.data.local.converter.MedicineMapper;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.MedicineBarcodeDao;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import com.medicationinventory.data.local.projection.MedicineStockProjection;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.util.AppExecutors;
import com.medicationinventory.service.BarcodeCodePolicy;
import com.medicationinventory.service.Gs1ScanParser;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;



/**
 * Serves medicine and inventory projections from Room while coordinating shared master and barcode changes with trusted backend sources. Local records support offline browsing and queued inventory work; shared medicine updates are validated and refreshed from the authoritative cloud source before the result is reported.
 */
public class MedicineRepository
{
    private final MedicineDao medicineDao;
    private final AppExecutors appExecutors;
    private final MedicineBarcodeDao barcodeDao;
    private final TrustedMedicineMasterDataSource cloud;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final TrustedMedicineBarcodeDataSource cloudBarcode;

    public MedicineRepository(MedicineDao medicineDao, AppExecutors appExecutors) {
        this(medicineDao, null, appExecutors);
    }

    public MedicineRepository(MedicineDao medicineDao, MedicineBarcodeDao barcodeDao,
                              AppExecutors appExecutors) {
        this(medicineDao, barcodeDao, appExecutors, null, null, null);
    }

    public MedicineRepository(MedicineDao medicineDao, MedicineBarcodeDao barcodeDao,
                              AppExecutors appExecutors,
                              TrustedMedicineMasterDataSource cloud,
                              FirebaseFirestore firestore, FirebaseAuth auth) {
        this(medicineDao, barcodeDao, appExecutors, cloud, firestore, auth, null);
    }

    public MedicineRepository(MedicineDao medicineDao, MedicineBarcodeDao barcodeDao,
                              AppExecutors appExecutors,
                              TrustedMedicineMasterDataSource cloud,
                              FirebaseFirestore firestore, FirebaseAuth auth,
                              TrustedMedicineBarcodeDataSource cloudBarcode) {
        this.medicineDao = medicineDao;
        this.barcodeDao = barcodeDao;
        this.appExecutors = appExecutors;
        this.cloud = cloud;
        this.firestore = firestore;
        this.auth = auth;
        this.cloudBarcode = cloudBarcode;
    }

    public LiveData<Integer> observeActiveMedicineCount()
    {
        return medicineDao.observeActiveMedicineCount();
    }

    public LiveData<List<Medicine>> observeActiveMedicines()
    {

        return Transformations.map(
                medicineDao.observeActiveMedicines(),
                entities -> {

                    List<Medicine> medicines = new ArrayList<>();

                    if (entities == null)
                    {
                        return medicines;
                    }

                    for (MedicineEntity entity : entities)
                    {
                        Medicine medicine = MedicineMapper.toDomain(entity);

                        if (medicine != null)
                        {
                            medicines.add(medicine);
                        }
                    }

                    return medicines;
                }
        );
    }

    public LiveData<List<InventoryMedicineItem>> observeInventoryItems(
            long today, long day15, long day31, long day91) {
        return Transformations.map(
                medicineDao.observeInventoryStock(today, day15, day31, day91),
                projections -> {
                    List<InventoryMedicineItem> results = new ArrayList<>();
                    if (projections == null) return results;
                    for (MedicineStockProjection projection : projections) {
                        if (projection == null || projection.medicine == null) continue;
                        Medicine medicine = MedicineMapper.toDomain(projection.medicine);
                        if (medicine == null) continue;
                        results.add(new InventoryMedicineItem(medicine,
                                projection.usableUnits, projection.expiredUnits,
                                projection.expiry0To14Units, projection.expiry15To30Units,
                                projection.expiry31To90Units, projection.expiryAfter90Units,
                                projection.nearestUsableExpiry));
                    }
                    return results;
                });
    }


    public LiveData<List<String>> observeActiveCategories()
    {
        return medicineDao
                .observeActiveCategories();
    }


    public LiveData<Medicine> observeMedicine(String medicineId)
    {
        return Transformations.map(medicineDao.observeById(medicineId), MedicineMapper::toDomain);
    }
    public void getMedicineById(String medicineId, MedicineCallback callback)
    {
        appExecutors.databaseIO().execute(() -> {
            MedicineEntity entity = medicineDao.getById(medicineId);
            Medicine medicine = MedicineMapper.toDomain(entity);
            appExecutors.mainThread().execute(() -> callback.onResult(medicine));
        });
    }
    public void getMedicineByCode(String medicineCode, MedicineCallback callback)
    {
        appExecutors.databaseIO().execute(() -> {

            MedicineEntity entity = medicineDao.getByCode(medicineCode);
            Medicine medicine = MedicineMapper.toDomain(entity);
            appExecutors.mainThread().execute(() -> callback.onResult(medicine));
        });
    }

    public void saveMedicine(Medicine medicine, OperationCallback callback)
    {
        if (medicine == null)
        {
            callback.onError(new IllegalArgumentException("Medicine cannot be null."));
            return;
        }

        appExecutors.databaseIO().execute(() -> {

            try
            {
                MedicineEntity entity = MedicineMapper.toEntity(medicine);
                medicineDao.upsert(entity);
                appExecutors.mainThread().execute(callback::onSuccess);
            }
            catch (Exception exception)
            {
                appExecutors.mainThread().execute(() -> callback.onError(exception));
            }
        });
    }

    /**
     * Saves a shared master through the trusted source and refreshes or verifies the Firestore version before accepting an edit.
     * An edit is rejected when the account changes, the server record is missing/invalid, or its sync version is stale;
     * these checks prevent this client from overwriting a newer cloud master.
     * @param medicine proposed medicine values
     * @param editing true when this is an update to an existing shared master
     * @param callback reports rejection, commit, and any subsequent refresh outcome
     */
    public void saveSharedMedicine(Medicine medicine, boolean editing,
                                   SharedMedicineSaveCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required.");
        if (medicine == null || cloud == null || firestore == null || auth == null) {
            callback.onRejected("Cloud medicine saving is unavailable.");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onRejected("Sign in to save to the shared inventory.");
            return;
        }
        final String uid = user.getUid();
        if (editing) {

            firestore.collection("medicines").document(medicine.getMedicineId())
                    .get(Source.SERVER).addOnCompleteListener(task -> {
                        if (!sameUser(uid)) {
                            callback.onRejected("Your session changed. Sign in again.");
                            return;
                        }
                        if (!task.isSuccessful() || task.getResult() == null) {
                            callback.onRejected("Cannot verify this medicine in Firestore. "
                                    + "Connect and retry; local data remains unchanged.");
                            return;
                        }
                        DocumentSnapshot snapshot = task.getResult();
                        if (!snapshot.exists()) {
                            callback.onRejected("This medicine exists only on this device. "
                                    + "It requires legacy-data reconciliation before cloud editing.");
                            return;
                        }
                        try {
                            MedicineEntity remote = FirestoreMedicineDocumentMapper.fromDocument(
                                    snapshot.getId(), snapshot.getData());
                            if (remote.getSyncVersion() != medicine.getSyncVersion()) {
                                callback.onRejected("The cloud medicine version differs from "
                                        + "this device. Refresh and reconcile before editing.");
                                return;
                            }
                        } catch (RuntimeException invalid) {
                            callback.onRejected("The cloud medicine record is invalid. "
                                    + "No changes were made.");
                            return;
                        }
                        callCloud(medicine, true, uid, callback);
                    });
        } else {
            callCloud(medicine, false, uid, callback);
        }
    }


    private void callCloud(Medicine medicine, boolean editing, String uid,
                           SharedMedicineSaveCallback callback) {
        TrustedMedicineMasterDataSource.MutationCallback remoteCallback =
                new TrustedMedicineMasterDataSource.MutationCallback() {
                    @Override
                    public void onSuccess(TrustedMedicineMasterDataSource.MutationResult result) {
                        if (!sameUser(uid)) {
                            callback.onOutcomeUncertain("The cloud request completed after your "
                                    + "session changed. Do not resubmit until reconciled.");
                            return;
                        }

                        firestore.collection("medicines").document(medicine.getMedicineId())
                                .get(Source.SERVER).addOnCompleteListener(task -> {
                                    if (!sameUser(uid)) {
                                        callback.onOutcomeUncertain("Session changed after cloud "
                                                + "save. Reconcile before resubmitting.");
                                        return;
                                    }
                                    if (!task.isSuccessful() || task.getResult() == null
                                            || !task.getResult().exists()) {
                                        callback.onOutcomeUncertain("Saved to Firestore, but "
                                                + "could not refresh this device. Do not resubmit.");
                                        return;
                                    }
                                    MedicineEntity authoritative;
                                    try {
                                        DocumentSnapshot snapshot = task.getResult();
                                        authoritative = FirestoreMedicineDocumentMapper.fromDocument(
                                                snapshot.getId(), snapshot.getData());
                                        if (authoritative.getSyncVersion() != result.getSyncVersion()) {
                                            throw new IllegalStateException("Cloud version advanced.");
                                        }
                                    } catch (RuntimeException invalid) {
                                        callback.onOutcomeUncertain("Cloud save completed, but "
                                                + "the authoritative record needs reconciliation. "
                                                + "Do not resubmit.");
                                        return;
                                    }
                                    appExecutors.databaseIO().execute(() -> {
                                        try {
                                            if (!sameUser(uid)) throw new IllegalStateException(
                                                    "Session changed before updating Room.");
                                            MedicineEntity existing = medicineDao.getById(
                                                    medicine.getMedicineId());

                                            boolean alreadySynced = MedicineCloudImportPolicy.sameRecord(
                                                    existing, authoritative);
                                            if (editing) {
                                                if (!alreadySynced && (existing == null ||
                                                        existing.getSyncVersion() != medicine.getSyncVersion())) {
                                                    throw new IllegalStateException(
                                                            "Local medicine changed during cloud save.");
                                                }
                                            } else if (existing != null && !alreadySynced) {
                                                throw new IllegalStateException(
                                                        "Local medicine ID was already in use.");
                                            }
                                            if (!alreadySynced) medicineDao.upsert(authoritative);
                                            appExecutors.mainThread().execute(() -> {
                                                if (!sameUser(uid)) {
                                                    callback.onOutcomeUncertain("Session changed after "
                                                            + "cloud save. Reconcile before resubmitting.");
                                                } else {
                                                    callback.onSaved(MedicineMapper.toDomain(authoritative));
                                                }
                                            });
                                        } catch (Exception localError) {
                                            appExecutors.mainThread().execute(() ->
                                                    callback.onOutcomeUncertain("Saved to Firestore "
                                                            + "but could not update this device. "
                                                            + "Do not resubmit; reconcile the local data."));
                                        }
                                    });
                                });
                    }

                    @Override
                    public void onError(TrustedMedicineMasterDataSource.MedicineRemoteException error) {
                        if (error.getStatusCode() == 0 || error.getStatusCode() >= 500 ||
                                error.getStatusCode() == 408 ||
                                "SESSION_CHANGED".equals(error.getCode()) ||
                                "INVALID_SERVER_RESPONSE".equals(error.getCode())) {
                            callback.onOutcomeUncertain(error.getMessage());
                        } else if (error.getStatusCode() == 409) {
                            callback.onRejected("Cloud conflict: " + error.getMessage());
                        } else {
                            callback.onRejected(error.getMessage());
                        }
                    }
                };
        if (editing) cloud.updateMedicine(medicine, remoteCallback);
        else cloud.createMedicine(medicine, remoteCallback);
    }


    private boolean sameUser(String uid) {

        FirebaseUser current = auth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    public interface SharedMedicineSaveCallback {
        void onSaved(Medicine medicine);

        void onRejected(String message);

        void onOutcomeUncertain(String message);
    }

    public void deactivateMedicine(String medicineId, OperationCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required.");
        if (medicineId == null || medicineId.trim().isEmpty() ||
                cloud == null || firestore == null || auth == null) {
            callback.onError(new IllegalStateException(
                    "Shared medicine deactivation is unavailable."));
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Sign in before deactivating medicines."));
            return;
        }
        final String uid = user.getUid();
        appExecutors.databaseIO().execute(() -> {
            final MedicineEntity local;
            try {
                local = medicineDao.getById(medicineId);
            } catch (RuntimeException databaseError) {
                appExecutors.mainThread().execute(() -> callback.onError(databaseError));
                return;
            }
            if (local == null || !local.isActive()) {
                appExecutors.mainThread().execute(() -> callback.onError(
                        new IllegalStateException("An active medicine is required.")));
                return;
            }
            final long expectedVersion = local.getSyncVersion();
            firestore.collection("medicines").document(medicineId)
                    .get(Source.SERVER).addOnCompleteListener(task -> {
                        if (!sameUser(uid)) {
                            callback.onError(new IllegalStateException("Session changed."));
                            return;
                        }
                        if (!task.isSuccessful() || task.getResult() == null) {
                            callback.onError(new IllegalStateException(
                                    "Cannot verify this medicine in Firestore. No changes were made."));
                            return;
                        }
                        MedicineEntity remote;
                        try {
                            DocumentSnapshot snapshot = task.getResult();
                            if (!snapshot.exists()) throw new IllegalStateException(
                                    "This medicine is local-only; reconcile before cloud deactivation.");
                            remote = FirestoreMedicineDocumentMapper.fromDocument(
                                    snapshot.getId(), snapshot.getData());
                            if (!remote.isActive()) throw new IllegalStateException(
                                    "Medicine was already deactivated in Firestore. Refresh first.");
                            if (remote.getSyncVersion() != expectedVersion) {
                                throw new IllegalStateException(
                                        "Medicine changed on another device. Refresh before deactivation.");
                            }
                        } catch (RuntimeException problem) {
                            callback.onError(problem);
                            return;
                        }
                        cloud.deactivateMedicine(medicineId, expectedVersion,
                                new TrustedMedicineMasterDataSource.MutationCallback() {
                                    @Override
                                    public void onSuccess(TrustedMedicineMasterDataSource.MutationResult result) {
                                        confirmCloudDeactivation(uid, medicineId, expectedVersion,
                                                result, callback);
                                    }
                                    @Override
                                    public void onError(TrustedMedicineMasterDataSource.MedicineRemoteException error) {
                                        callback.onError(error);
                                    }
                                });
                    });
        });
    }


    private void confirmCloudDeactivation(String uid, String medicineId,
                                          long expectedVersion,
                                          TrustedMedicineMasterDataSource.MutationResult result,
                                          OperationCallback callback) {
        if (!sameUser(uid)) {
            callback.onError(new IllegalStateException(
                    "Cloud request completed after session changed; reconcile first."));
            return;
        }
        firestore.collection("medicines").document(medicineId)
                .get(Source.SERVER).addOnCompleteListener(task -> {
                    if (!sameUser(uid) || !task.isSuccessful() || task.getResult() == null ||
                            !task.getResult().exists()) {
                        callback.onError(new IllegalStateException(
                                "Cloud request completed but confirmation failed; reconcile first."));
                        return;
                    }
                    final MedicineEntity authoritative;
                    try {
                        DocumentSnapshot snapshot = task.getResult();
                        authoritative = FirestoreMedicineDocumentMapper.fromDocument(
                                snapshot.getId(), snapshot.getData());
                        if (authoritative.isActive() ||
                                authoritative.getSyncVersion() != result.getSyncVersion()) {
                            throw new IllegalStateException("Deactivation was not confirmed.");
                        }
                    } catch (RuntimeException invalid) {
                        callback.onError(new IllegalStateException(
                                "Cloud lifecycle state could not be verified; reconcile first.", invalid));
                        return;
                    }
                    appExecutors.databaseIO().execute(() -> {
                        try {
                            if (!sameUser(uid)) throw new IllegalStateException("Session changed.");
                            MedicineEntity local = medicineDao.getById(medicineId);
                            if (local == null) throw new IllegalStateException("Medicine missing in Room.");
                            if (!MedicineCloudImportPolicy.sameRecord(local, authoritative)) {
                                if (local.getSyncVersion() != expectedVersion) {
                                    throw new IllegalStateException(
                                            "Local state changed during deactivation; reconcile first.");
                                }
                                medicineDao.upsert(authoritative);
                            }
                            appExecutors.mainThread().execute(() -> {
                                if (sameUser(uid)) callback.onSuccess();
                                else callback.onError(new IllegalStateException(
                                        "Session changed after deactivation; reconcile first."));
                            });
                        } catch (Exception failure) {
                            appExecutors.mainThread().execute(() -> callback.onError(
                                    new IllegalStateException("Saved to Firestore but could not " +
                                            "update Room; reconcile before retrying.", failure)));
                        }
                    });
                });
    }

    public LiveData<List<MedicineBarcodeEntity>> observeBarcodeMappings() {
        return requireBarcodeDao().observeAll();
    }


    public LiveData<List<MedicineBarcodeEntity>> observeBarcodesForMedicine(String medicineId) {
        return requireBarcodeDao().observeForMedicine(medicineId);
    }


    /**
     * Saves a barcode mapping for a medicine after applying the identifier policy and trusted backend authorization.
     * Existing mapping identity and account state are checked before local cache updates.
     */
    public void linkBarcode(String medicineId, String rawCode, OperationCallback callback) {
        mutateBarcode(medicineId, rawCode, true, callback);
    }


    public void unlinkBarcode(String medicineId, String rawCode, OperationCallback callback) {
        mutateBarcode(medicineId, rawCode, false, callback);
    }


    private void mutateBarcode(String medicineId, String rawCode, boolean linking,
                               OperationCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required.");
        if (cloudBarcode == null || firestore == null || auth == null) {
            callback.onError(new IllegalStateException(
                    "Shared barcode service is not configured; no local changes made."));
            return;
        }
        final String barcode;
        try {
            barcode = BarcodeCodePolicy.requireCode(rawCode);
            if (medicineId == null || medicineId.trim().isEmpty())
                throw new IllegalArgumentException("Medicine ID is required.");
        } catch (Exception invalid) {
            callback.onError(invalid);
            return;
        }
        FirebaseUser initiatingUser = auth.getCurrentUser();
        if (initiatingUser == null) {
            callback.onError(new IllegalStateException("Sign in to manage shared barcodes."));
            return;
        }
        final String uid = initiatingUser.getUid();
        appExecutors.databaseIO().execute(() -> {
            try {
                if (!sameUser(uid)) throw new IllegalStateException("Session changed.");
                MedicineEntity medicine = medicineDao.getById(medicineId);
                if (medicine == null || (linking && !medicine.isActive()))
                    throw new IllegalStateException("An active local medicine is required to link.");
                MedicineBarcodeEntity existing = requireBarcodeDao().getByBarcode(barcode);
                if (linking && existing != null &&
                        !medicineId.equals(existing.getMedicineId()))
                    throw new IllegalStateException("Barcode conflicts with another local medicine. Reconcile first.");
                MedicineEntity owner = medicineDao.getByCode(barcode);
                if (linking && owner != null && !medicineId.equals(owner.getMedicineId()))
                    throw new IllegalStateException("Barcode matches another local medicine code.");
                appExecutors.mainThread().execute(() -> {
                    if (!sameUser(uid)) {
                        callback.onError(new IllegalStateException("Session changed."));
                        return;
                    }
                    TrustedMedicineBarcodeDataSource.Callback remote =
                            new TrustedMedicineBarcodeDataSource.Callback() {
                                @Override public void onSuccess() {
                                    verifyBarcodeCloudResult(uid, medicineId, barcode, linking, callback);
                                }
                                @Override public void onError(TrustedMedicineBarcodeDataSource.RemoteException error) {
                                    callback.onError(error);
                                }
                            };
                    if (linking) cloudBarcode.link(medicineId, barcode, remote);
                    else cloudBarcode.unlink(medicineId, barcode, remote);
                });
            } catch (Exception exception) {
                appExecutors.mainThread().execute(() -> callback.onError(exception));
            }
        });
    }


    private void verifyBarcodeCloudResult(String uid, String medicineId, String barcode,
                                          boolean linking, OperationCallback callback) {
        if (!sameUser(uid)) {
            callback.onError(new IllegalStateException("Session changed after cloud request; reconcile."));
            return;
        }
        String docId = SharedBarcodePolicy.documentId(barcode);
        firestore.collection("medicineBarcodes").document(docId).get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (!sameUser(uid) || !task.isSuccessful() || task.getResult() == null) {
                        callback.onError(new IllegalStateException("Cloud request completed but local "
                                + "confirmation failed. Do not retry until reconciled."));
                        return;
                    }
                    DocumentSnapshot remote = task.getResult();
                    if (linking && (!remote.exists() ||
                            !barcode.equals(remote.getString("barcode")) ||
                            !medicineId.equals(remote.getString("medicineId")))) {
                        callback.onError(new IllegalStateException("Cloud barcode assignment changed; reconcile."));
                        return;
                    }
                    if (!linking && remote.exists() &&
                            medicineId.equals(remote.getString("medicineId"))) {
                        callback.onError(new IllegalStateException("Cloud unlink could not be verified; reconcile."));
                        return;
                    }
                    appExecutors.databaseIO().execute(() -> {
                        try {
                            if (!sameUser(uid)) throw new IllegalStateException("Session changed.");
                            MedicineBarcodeEntity existing = requireBarcodeDao().getByBarcode(barcode);
                            if (linking) {
                                if (existing == null) {
                                    if (medicineDao.getById(medicineId) == null)
                                        throw new IllegalStateException("Local medicine is missing.");
                                    requireBarcodeDao().insert(new MedicineBarcodeEntity(
                                            barcode, medicineId, System.currentTimeMillis()));
                                } else if (!medicineId.equals(existing.getMedicineId())) {
                                    throw new IllegalStateException("A conflicting local barcode exists.");
                                }
                            } else {
                                requireBarcodeDao().unlink(barcode, medicineId);
                            }
                            appExecutors.mainThread().execute(() -> {
                                if (sameUser(uid)) callback.onSuccess();
                                else callback.onError(new IllegalStateException("Session changed; reconcile."));
                            });
                        } catch (Exception error) {
                            appExecutors.mainThread().execute(() -> callback.onError(
                                    new IllegalStateException("Cloud changed but the local barcode "
                                            + "needs reconciliation. Do not resubmit.", error)));
                        }
                    });
                });
    }


    public void verifyScanForMedicine(String expectedMedicineId, String scannedValue,
                                      ScanVerificationCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is required.");
        appExecutors.databaseIO().execute(() -> {
            try {
                if (expectedMedicineId == null || expectedMedicineId.trim().isEmpty())
                    throw new IllegalArgumentException("No medicine was selected.");
                MedicineEntity selected = medicineDao.getById(expectedMedicineId);
                if (selected == null || !selected.isActive())
                    throw new IllegalStateException("Selected medicine is unavailable or inactive.");


                String rawCode = null;
                try { rawCode = BarcodeCodePolicy.requireCode(scannedValue); }
                catch (IllegalArgumentException invalidRaw) {

                }
                MedicineEntity resolved = rawCode == null
                        ? null : findMedicineByCodeOrBarcode(rawCode);

                Gs1ScanParser.Data gs1 = null;
                try {
                    gs1 = Gs1ScanParser.parse(scannedValue);
                } catch (IllegalArgumentException gs1Error) {
                    if (resolved == null) throw gs1Error;
                }
                String productCode = BarcodeCodePolicy.requireCode(
                        gs1 == null ? scannedValue : gs1.getGtin());
                if (resolved == null && (rawCode == null || !rawCode.equals(productCode)))
                    resolved = findMedicineByCodeOrBarcode(productCode);

                ScanVerificationResult result = new ScanVerificationResult(
                        resolved == null ? ScanVerificationResult.Status.UNKNOWN
                                : !resolved.isActive() ? ScanVerificationResult.Status.INACTIVE
                                  : expectedMedicineId.equals(resolved.getMedicineId())
                                    ? ScanVerificationResult.Status.MATCH
                                    : ScanVerificationResult.Status.DIFFERENT_MEDICINE,
                        productCode,
                        resolved == null ? null : resolved.getMedicineName(),
                        gs1 == null ? null : gs1.getLot(),
                        gs1 == null ? null : gs1.getExpiryDate());
                appExecutors.mainThread().execute(() -> callback.onResult(result));
            } catch (Exception error) {
                appExecutors.mainThread().execute(() -> callback.onError(error));
            }
        });
    }


    private MedicineEntity findMedicineByCodeOrBarcode(String code) {
        MedicineBarcodeEntity linked = requireBarcodeDao().getByBarcode(code);
        if (linked != null) return medicineDao.getById(linked.getMedicineId());
        return medicineDao.getByCode(code);
    }

    public interface ScanVerificationCallback {
        void onResult(ScanVerificationResult result);
        void onError(Exception error);
    }

    public static final class ScanVerificationResult {
        public enum Status { MATCH, UNKNOWN, INACTIVE, DIFFERENT_MEDICINE }
        private final Status status;
        private final String identifier;
        private final String matchedMedicineName;
        private final String batchNumber;
        private final LocalDate expiryDate;


        public ScanVerificationResult(Status status, String identifier,
                                      String matchedMedicineName, String batchNumber,
                                      LocalDate expiryDate) {
            this.status = status;
            this.identifier = identifier;
            this.matchedMedicineName = matchedMedicineName;
            this.batchNumber = batchNumber;
            this.expiryDate = expiryDate;
        }
        public Status getStatus() { return status; }
        public String getIdentifier() { return identifier; }
        public String getMatchedMedicineName() { return matchedMedicineName; }
        public String getBatchNumber() { return batchNumber; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public boolean isMatch() { return status == Status.MATCH; }
    }


    private MedicineBarcodeDao requireBarcodeDao() {
        if (barcodeDao == null)
            throw new IllegalStateException("Medicine barcode storage is not configured.");
        return barcodeDao;
    }

    public interface MedicineCallback
    {
        void onResult(Medicine medicine);
    }
    public interface OperationCallback
    {
        void onSuccess();
        void onError(Exception exception);
    }
}
