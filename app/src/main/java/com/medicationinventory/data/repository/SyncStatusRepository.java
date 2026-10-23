package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.InventoryOperationDao;

import com.medicationinventory.data.local.model.SyncStatusRecord;
import com.medicationinventory.data.local.model.SyncStatusSummary;

import java.util.List;




/**
 * Reads operation synchronization statuses and summary counts from the local inventory operation DAO.
 */
public class SyncStatusRepository
{
    public static final int DEFAULT_RECENT_LIMIT =
            100;


    private final InventoryOperationDao
            inventoryOperationDao;


    public SyncStatusRepository(
            InventoryOperationDao inventoryOperationDao)
    {
        if (inventoryOperationDao == null)
        {
            throw new IllegalArgumentException(
                    "Inventory operation DAO is required."
            );
        }


        this.inventoryOperationDao =
                inventoryOperationDao;
    }


    public LiveData<SyncStatusSummary>
    observeSummary(
            String performedBy)
    {
        return inventoryOperationDao
                .observeSyncStatusSummary(
                        requireUserId(
                                performedBy
                        )
                );
    }


    public LiveData<List<SyncStatusRecord>>
    observeRecent(
            String performedBy)
    {
        return observeRecent(
                performedBy,
                DEFAULT_RECENT_LIMIT
        );
    }


    public LiveData<List<SyncStatusRecord>>
    observeRecent(
            String performedBy,
            int limit)
    {
        int safeLimit =
                limit <= 0
                        ? DEFAULT_RECENT_LIMIT
                        : limit;


        return inventoryOperationDao
                .observeSyncStatusRecords(
                        requireUserId(
                                performedBy
                        ),
                        safeLimit
                );
    }

    public LiveData<Integer> observePendingCount(String performedBy)
    {
        return inventoryOperationDao
                .observePendingSyncCount(
                        requireUserId(
                                performedBy
                        )
                );
    }

    public LiveData<Integer> observeRequiresAttentionCount(String performedBy)
    {
        return inventoryOperationDao
                .observeSyncAttentionCount(
                        requireUserId(
                                performedBy
                        )
                );
    }


    public LiveData<SyncStatusRecord>
    observeById(
            String performedBy,
            String operationId)
    {
        String safeUserId =
                requireUserId(
                        performedBy
                );


        if (operationId == null
                ||
                operationId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Operation ID is required."
            );
        }


        return inventoryOperationDao
                .observeSyncStatusRecordById(
                        operationId.trim(),
                        safeUserId
                );
    }

    private String requireUserId(
            String performedBy)
    {
        if (performedBy == null
                ||
                performedBy.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        return performedBy.trim();
    }
}