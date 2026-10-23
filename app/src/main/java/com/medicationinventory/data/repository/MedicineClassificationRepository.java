package com.medicationinventory.data.repository;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.medicationinventory.data.local.dao.MedicineClassificationDao;
import com.medicationinventory.data.local.entity.MedicineClassificationEntity;
import com.medicationinventory.data.mapper.MedicineClassificationMapper;
import com.medicationinventory.data.remote.firestore.FirestoreMedicineClassificationDataSource;
import com.medicationinventory.data.remote.backend.TrustedMedicineClassificationDataSource;

import com.medicationinventory.model.domain.MedicineClassification;

import com.medicationinventory.util.AppExecutors;
import com.medicationinventory.security.RestrictedClassificationAccessGate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Maps classification records between Room and Firestore and routes changes through the trusted mutation source. Restricted classification reads and cache writes are additionally guarded by the current user gate; the cache guard is responsible for requiring both local and server-confirmed permission before opening access.
 */
public class MedicineClassificationRepository
{
    private final MedicineClassificationDao medicineClassificationDao;

    private final AppExecutors appExecutors;


    private volatile RestrictedClassificationAccessGate restrictedAccessGate;

    /** Installs the session gate used to reject restricted classification reads and cache writes after access changes. */
    public void installRestrictedAccessGate(RestrictedClassificationAccessGate gate) {
        if (gate == null) throw new IllegalArgumentException("Restricted access gate required");
        this.restrictedAccessGate = gate;
    }

    public boolean isRestrictedAccessGranted() {
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        return gate == null || gate.isGranted();
    }

    public LiveData<Boolean> observeRestrictedAccess() {
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        return gate == null ? new MutableLiveData<>(true) : gate.observeAccess();
    }

    private RestrictedClassificationAccessGate.Ticket captureTicket() {
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        if (gate == null) return null; // legacy tests, without installed guard
        RestrictedClassificationAccessGate.Ticket ticket = gate.capture();
        if (ticket == null) throw new SecurityException("ABC/VED access is not server-confirmed.");
        return ticket;
    }

    private void requireCurrentTicket(RestrictedClassificationAccessGate.Ticket ticket) {
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        if (gate != null && !gate.accepts(ticket))
            throw new SecurityException("ABC/VED access changed while loading classifications.");
    }

    private void mutateWithTicket(RestrictedClassificationAccessGate.Ticket ticket, Runnable task) {
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        if (gate == null) task.run();
        else gate.runIfAuthorized(ticket, task);
    }

    private final FirestoreMedicineClassificationDataSource remoteDataSource;

    private final TrustedMedicineClassificationDataSource trustedMutationDataSource;

    public MedicineClassificationRepository(
            MedicineClassificationDao medicineClassificationDao,
            FirestoreMedicineClassificationDataSource remoteDataSource,
            TrustedMedicineClassificationDataSource trustedMutationDataSource,
            AppExecutors appExecutors)
    {
        if (medicineClassificationDao == null)
        {
            throw new IllegalArgumentException(
                    "Medicine classification DAO is required."
            );
        }


        if (remoteDataSource == null)
        {
            throw new IllegalArgumentException(
                    "Medicine classification remote data source is required."
            );
        }


        if (trustedMutationDataSource == null)
        {
            throw new IllegalArgumentException(
                    "Trusted classification mutation data source is required."
            );
        }


        if (appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "App executors are required."
            );
        }


        this.medicineClassificationDao =
                medicineClassificationDao;


        this.remoteDataSource =
                remoteDataSource;


        this.trustedMutationDataSource =
                trustedMutationDataSource;


        this.appExecutors =
                appExecutors;
    }


    /**
     * Observes one medicine classification while the access gate is open; otherwise emits null.
     * @param medicineId medicine identifier
     * @return gated live classification value
     */
    public LiveData<MedicineClassification> observeForMedicine(
            String medicineId)
    {
        String cleanMedicineId =
                requireMedicineId(
                        medicineId
                );


        LiveData<MedicineClassification> source = Transformations.map(
                medicineClassificationDao
                        .observeForMedicine(
                                cleanMedicineId
                        ),
                entity -> isRestrictedAccessGranted()
                        ? MedicineClassificationMapper.toDomain(entity) : null
        );
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        if (gate == null) return source;
        return Transformations.switchMap(gate.observeAccess(), allowed ->
                Boolean.TRUE.equals(allowed) && gate.isGranted()
                        ? source : new MutableLiveData<MedicineClassification>(null));
    }

    public LiveData<List<MedicineClassification>>
    observeActiveForAnalytics()
    {
        LiveData<List<MedicineClassification>> source = Transformations.map(
                medicineClassificationDao
                        .observeActiveForAnalytics(),
                entities ->
                {
                    List<MedicineClassification>
                            result =
                            new ArrayList<>();


                    if (entities == null)
                    {
                        return result;
                    }


                    for (MedicineClassificationEntity entity :
                            entities)
                    {
                        MedicineClassification classification =
                                MedicineClassificationMapper
                                        .toDomain(
                                                entity
                                        );


                        if (classification != null)
                        {
                            result.add(
                                    classification
                            );
                        }
                    }


                    return isRestrictedAccessGranted() ? result : new ArrayList<>();
                }
        );
        RestrictedClassificationAccessGate gate = restrictedAccessGate;
        if (gate == null) return source;
        return Transformations.switchMap(gate.observeAccess(), allowed ->
                Boolean.TRUE.equals(allowed) && gate.isGranted()
                        ? source : new MutableLiveData<List<MedicineClassification>>(new ArrayList<>()));
    }

    public void getForMedicine(
            String medicineId,
            ClassificationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Classification callback is required."
            );
        }


        final String cleanMedicineId;


        try
        {
            cleanMedicineId =
                    requireMedicineId(
                            medicineId
                    );
        }
        catch (IllegalArgumentException exception)
        {
            appExecutors
                    .mainThread()
                    .execute(
                            () ->
                                    callback.onError(
                                            exception
                                    )
                    );

            return;
        }


        final RestrictedClassificationAccessGate.Ticket accessTicket;
        try { accessTicket = captureTicket(); }
        catch (SecurityException exception) { callback.onError(exception); return; }

        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                requireCurrentTicket(accessTicket);
                                MedicineClassificationEntity entity =
                                        medicineClassificationDao
                                                .getForMedicine(
                                                        cleanMedicineId
                                                );


                                MedicineClassification classification =
                                        MedicineClassificationMapper
                                                .toDomain(
                                                        entity
                                                );


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                {
                                                    try { requireCurrentTicket(accessTicket); callback.onResult(classification); }
                                                    catch (SecurityException exception) { callback.onError(exception); }
                                                }
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }



    /**
     * Refreshes a medicine classification from Firestore and guards the asynchronous cache update with its captured access ticket.
     */
    public void refreshFromServer(
            String medicineId,
            RefreshCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Refresh callback is required."
            );
        }


        final String cleanMedicineId;


        try
        {
            cleanMedicineId =
                    requireMedicineId(
                            medicineId
                    );
        }
        catch (IllegalArgumentException exception)
        {
            callback.onError(
                    exception
            );

            return;
        }


        final RestrictedClassificationAccessGate.Ticket accessTicket;
        try { accessTicket = captureTicket(); }
        catch (SecurityException exception) { callback.onError(exception); return; }

        remoteDataSource
                .getForMedicine(
                        cleanMedicineId,
                        new FirestoreMedicineClassificationDataSource
                                .ClassificationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    MedicineClassification classification)
                            {
                                /*
                                 * Cloud says the classification does
                                 * not exist. Remove any stale local
                                 * copy for this medicine.
                                 */
                                if (classification == null)
                                {
                                    deleteAuthoritativeAbsence(
                                            cleanMedicineId,
                                            accessTicket,
                                            callback
                                    );

                                    return;
                                }


                                persistAuthoritativeClassification(
                                        classification,
                                        accessTicket,
                                        callback
                                );
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {

                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }


    private void persistAuthoritativeClassification(
            MedicineClassification classification,
            RestrictedClassificationAccessGate.Ticket accessTicket,
            RefreshCallback callback)
    {
        MedicineClassificationEntity entity =
                MedicineClassificationMapper
                        .toEntity(
                                classification
                        );


        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                mutateWithTicket(accessTicket,
                                        () -> medicineClassificationDao.upsert(entity));


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                {
                                                    try { requireCurrentTicket(accessTicket); callback.onSuccess(classification); }
                                                    catch (SecurityException exception) { callback.onError(exception); }
                                                }
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }


    private void deleteAuthoritativeAbsence(
            String medicineId,
            RestrictedClassificationAccessGate.Ticket accessTicket,
            RefreshCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                mutateWithTicket(accessTicket,
                                        () -> medicineClassificationDao.deleteForMedicine(medicineId));


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                {
                                                    try { requireCurrentTicket(accessTicket); callback.onSuccess(null); }
                                                    catch (SecurityException exception) { callback.onError(exception); }
                                                }
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }


    public void refreshAllFromServer(
            List<String> medicineIds,
            RefreshAllCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Refresh-all callback is required."
            );
        }


        Set<String> uniqueIds =
                new LinkedHashSet<>();


        if (medicineIds != null)
        {
            for (String medicineId :
                    medicineIds)
            {
                if (medicineId == null)
                {
                    continue;
                }


                String clean =
                        medicineId.trim();


                if (!clean.isEmpty())
                {
                    uniqueIds.add(
                            clean
                    );
                }
            }
        }


        List<String> ids =
                new ArrayList<>(
                        uniqueIds
                );


        if (ids.isEmpty())
        {
            callback.onComplete(
                    0,
                    0
            );

            return;
        }


        refreshNextClassification(
                ids,
                0,
                0,
                0,
                callback
        );
    }


    private void refreshNextClassification(
            List<String> medicineIds,
            int index,
            int successful,
            int failed,
            RefreshAllCallback callback)
    {
        if (index
                >=
                medicineIds.size())
        {
            callback.onComplete(
                    successful,
                    failed
            );

            return;
        }


        String medicineId =
                medicineIds.get(
                        index
                );


        refreshFromServer(
                medicineId,
                new RefreshCallback()
                {
                    @Override
                    public void onSuccess(
                            MedicineClassification classification)
                    {

                        refreshNextClassification(
                                medicineIds,
                                index + 1,
                                successful + 1,
                                failed,
                                callback
                        );
                    }


                    @Override
                    public void onError(
                            Exception exception)
                    {

                        refreshNextClassification(
                                medicineIds,
                                index + 1,
                                successful,
                                failed + 1,
                                callback
                        );
                    }
                }
        );
    }


    public interface RefreshAllCallback
    {
        void onComplete(
                int successful,
                int failed
        );
    }

    public interface RefreshCallback
    {
        void onSuccess(
                MedicineClassification classification
        );


        void onError(
                Exception exception
        );
    }


    /** Clears local restricted classification rows when permission or cache ownership is no longer trusted. */
    public void clearRestrictedCache(
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Mutation callback is required."
            );
        }


        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                medicineClassificationDao
                                        .clearAll();


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                callback::onSuccess
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }


    private String requireMedicineId(
            String medicineId)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Medicine ID is required."
            );
        }


        return medicineId.trim();
    }

    /**
     * Sends a classification change through the trusted backend and refreshes the server snapshot after commit.
     * Permission and ticket checks protect the local mutation path; backend authorization remains authoritative.
     */
    public void setClassification(
            String medicineId,
            String abcClassification,
            String vedClassification,
            TrustedMutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Trusted mutation callback is required."
            );
        }


        final String cleanMedicineId;


        try
        {
            cleanMedicineId =
                    requireMedicineId(
                            medicineId
                    );
        }
        catch (IllegalArgumentException exception)
        {
            callback.onError(
                    exception
            );

            return;
        }


        trustedMutationDataSource
                .setClassification(
                        cleanMedicineId,
                        abcClassification,
                        vedClassification,
                        new TrustedMedicineClassificationDataSource
                                .MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    TrustedMedicineClassificationDataSource
                                            .MutationResult result)
                            {

                                refreshFromServer(
                                        cleanMedicineId,
                                        new RefreshCallback()
                                        {
                                            @Override
                                            public void onSuccess(
                                                    MedicineClassification classification)
                                            {
                                                callback.onSuccess(
                                                        result.isChanged(),
                                                        result.isCreated(),
                                                        classification
                                                );
                                            }


                                            @Override
                                            public void onError(
                                                    Exception exception)
                                            {

                                                callback.onCommittedRefreshError(
                                                        result.isChanged(),
                                                        result.isCreated(),
                                                        exception
                                                );
                                            }
                                        }
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedMedicineClassificationDataSource
                                            .ClassificationRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface TrustedMutationCallback
    {
        void onSuccess(
                boolean changed,
                boolean created,
                MedicineClassification classification
        );


        void onCommittedRefreshError(
                boolean changed,
                boolean created,
                Exception exception
        );


        void onError(
                Exception exception
        );
    }

    public interface ClassificationCallback
    {
        void onResult(
                MedicineClassification classification
        );


        void onError(
                Exception exception
        );
    }


    public interface MutationCallback
    {
        void onSuccess();


        void onError(
                Exception exception
        );
    }
}
