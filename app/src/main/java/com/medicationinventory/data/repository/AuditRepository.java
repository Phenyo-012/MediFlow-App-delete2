package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.AuditDao;
import com.medicationinventory.data.local.entity.AuditCacheEntity;

import com.medicationinventory.data.remote.firestore.FirestoreAuditDataSource;

import com.medicationinventory.model.dto.AuditRemoteRecord;

import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;


/**
 * Loads the newest protected audit records from Firestore and stores them in a local Room cache for the audit screen. The screen observes the cache while refresh results update it asynchronously; network failures are returned to the caller without fabricating records.
 */
public class AuditRepository
{
    public static final int DEFAULT_AUDIT_LIMIT =
            100;


    private final AuditDao
            auditDao;


    private final FirestoreAuditDataSource
            remoteDataSource;


    private final AppExecutors
            appExecutors;


    public AuditRepository(
            AuditDao auditDao,
            FirestoreAuditDataSource remoteDataSource,
            AppExecutors appExecutors)
    {
        if (auditDao == null
                ||
                remoteDataSource == null
                ||
                appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "Audit repository dependencies are required."
            );
        }


        this.auditDao =
                auditDao;

        this.remoteDataSource =
                remoteDataSource;

        this.appExecutors =
                appExecutors;
    }


    public LiveData<List<AuditCacheEntity>>
    observeRecent()
    {
        return auditDao
                .observeRecent(
                        DEFAULT_AUDIT_LIMIT
                );
    }


    public LiveData<List<AuditCacheEntity>>
    observeRecent(
            int limit)
    {
        int safeLimit =
                limit <= 0
                        ? DEFAULT_AUDIT_LIMIT
                        : limit;


        return auditDao
                .observeRecent(
                        safeLimit
                );
    }


    /**
     * Refreshes the default recent-record window from the remote audit source into Room.
     * @param callback receives the completed refresh or its error
     */
    public void refreshRecent(
            RefreshCallback callback)
    {
        refreshRecent(
                DEFAULT_AUDIT_LIMIT,
                callback
        );
    }


    /**
     * Refreshes a bounded number of remote audit records and updates their local cache.
     * @param limit maximum records; non-positive values use the default
     * @param callback receives the completed refresh or its error
     */
    public void refreshRecent(
            int limit,
            RefreshCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Audit refresh callback is required."
            );
        }


        int safeLimit =
                limit <= 0
                        ? DEFAULT_AUDIT_LIMIT
                        : limit;


        remoteDataSource
                .loadRecent(
                        safeLimit,
                        new FirestoreAuditDataSource.Callback()
                        {
                            @Override

                            public void onSuccess(
                                    List<AuditRemoteRecord> records)
                            {
                                cacheRemoteRecords(
                                        records,
                                        callback
                                );
                            }


                            @Override

                            public void onError(
                                    Exception exception)
                            {
                                postError(
                                        callback,
                                        exception
                                );
                            }
                        }
                );
    }


    private void cacheRemoteRecords(
            List<AuditRemoteRecord> records,
            RefreshCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                List<AuditCacheEntity> entities =
                                        new ArrayList<>();


                                if (records != null)
                                {
                                    for (AuditRemoteRecord record :
                                            records)
                                    {
                                        if (record == null)
                                        {
                                            continue;
                                        }


                                        entities.add(
                                                new AuditCacheEntity(
                                                        record.getAuditId(),
                                                        record.getActionType(),
                                                        record.getEntityType(),
                                                        record.getEntityId(),
                                                        record.getPerformedBy(),
                                                        record.getPerformedAt(),
                                                        record.getDetailsJson()
                                                )
                                        );
                                    }
                                }


                                if (!entities.isEmpty())
                                {
                                    auditDao
                                            .upsertFromSync(
                                                    entities
                                            );
                                }


                                postSuccess(
                                        callback,
                                        entities.size()
                                );
                            }
                            catch (Exception exception)
                            {
                                postError(
                                        callback,
                                        exception
                                );
                            }
                        }
                );
    }


    private void postSuccess(
            RefreshCallback callback,
            int recordCount)
    {
        appExecutors
                .mainThread()
                .execute(
                        () ->
                                callback.onSuccess(
                                        recordCount
                                )
                );
    }


    private void postError(
            RefreshCallback callback,
            Exception exception)
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


    public interface RefreshCallback
    {
        void onSuccess(
                int recordCount
        );


        void onError(
                Exception exception
        );
    }
}