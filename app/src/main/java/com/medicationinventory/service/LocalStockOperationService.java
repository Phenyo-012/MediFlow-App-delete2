package com.medicationinventory.service;

import com.medicationinventory.data.local.dao.StockOperationDao;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;
import com.medicationinventory.util.AppExecutors;

import java.util.List;



/**
 * Runs local inventory commits away from the UI thread and returns their outcome on the main
 * thread. Each DAO call writes an operation together with its allocation and transaction records
 * plus the queue row that makes it eligible for trusted-backend processing. The DAO owns the Room
 * transaction and rechecks stock constraints; this service is the asynchronous boundary used by
 * inventory workflows and does not treat a local commit as cloud confirmation.
 */
public class LocalStockOperationService
{
    private final StockOperationDao
            stockOperationDao;

    private final AppExecutors
            appExecutors;

    public LocalStockOperationService(
            StockOperationDao stockOperationDao,
            AppExecutors appExecutors)
    {
        this.stockOperationDao =
                stockOperationDao;

        this.appExecutors =
                appExecutors;
    }


    /**
     * Stores the new-batch operation, allocation, provisional transaction, and sync row
     * together so a partially written local receipt cannot be uploaded.
     */
    public void commitNewBatchReceipt(
            InventoryOperationEntity operation,
            BatchEntity batch,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity transaction,
            SyncQueueEntity queueItem,
            CommitCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        stockOperationDao
                                .commitNewBatchReceipt(
                                        operation,
                                        batch,
                                        allocation,
                                        transaction,
                                        queueItem
                                );


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
                                .execute(() ->
                                        callback.onError(
                                                exception
                                        )
                                );
                    }
                });
    }


    /**
     * Records an explicit top-up against the selected batch while retaining its batch identity.
     */
    public void commitExistingBatchReceipt(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            SyncQueueEntity queueItem,
            CommitCallback callback) {
        appExecutors.databaseIO().execute(() -> {
            try {
                stockOperationDao.commitExistingBatchReceipt(operation, allocation, queueItem);
                appExecutors.mainThread().execute(callback::onSuccess);
            } catch (Exception exception) {
                appExecutors.mainThread().execute(() -> callback.onError(exception));
            }
        });
    }

    /**
     * Persists the issue, allocations, transactions, and queue entry as pending local work.
     * This overload records no authorized FEFO override.
     */
    public void commitIssue(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations,
            List<InventoryTransactionEntity> transactions,
            SyncQueueEntity queueItem,
            CommitCallback callback)
    {
        commitIssue(
                operation,
                allocations,
                transactions,
                queueItem,
                false,
                callback
        );
    }


    /**
     * Persists the issue and records whether a FEFO override was authorized for this request.
     * The authorization flag is passed to the DAO, which validates any override reason and selected allocations before saving.
     */
    public void commitIssue(
            InventoryOperationEntity operation,
            List<OperationAllocationEntity> allocations,
            List<InventoryTransactionEntity> transactions,
            SyncQueueEntity queueItem,
            boolean fefoOverrideAuthorized,
            CommitCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        stockOperationDao
                                .commitIssue(
                                        operation,
                                        allocations,
                                        transactions,
                                        queueItem,
                                        fefoOverrideAuthorized
                                );


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
                                .execute(() ->
                                        callback.onError(
                                                exception
                                        )
                                );
                    }
                });
    }

    /**
     * Stores an adjustment and its audit transaction as pending local work. The DAO applies the
     * change atomically with the batch quantity and sync queue entry; a database failure is
     * delivered through {@code callback.onError} on the main thread.
     */
    public void commitAdjustment(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity transaction,
            SyncQueueEntity queueItem,
            CommitCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        stockOperationDao
                                .commitAdjustment(
                                        operation,
                                        allocation,
                                        transaction,
                                        queueItem
                                );


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
                                .execute(() ->
                                        callback.onError(
                                                exception
                                        )
                                );
                    }
                });
    }

    /**
     * Persists a compensating correction against its referenced stock operation. The local DAO
     * checks the relationship to the original confirmed operation before changing the batch, and
     * the new operation remains queued for trusted processing.
     */
    public void commitCorrection(
            InventoryOperationEntity operation,
            OperationAllocationEntity allocation,
            InventoryTransactionEntity transaction,
            SyncQueueEntity queueItem,
            CommitCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        stockOperationDao
                                .commitCorrection(
                                        operation,
                                        allocation,
                                        transaction,
                                        queueItem
                                );


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
                                .execute(() ->
                                        callback.onError(
                                                exception
                                        )
                                );
                    }
                });
    }


    /** Receives the result of a background Room commit on the main thread. */
    public interface CommitCallback
    {
        void onSuccess();


        void onError(
                Exception exception
        );
    }
}