package com.medicationinventory.ui.sync;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.entity.SyncMetadataEntity;
import com.medicationinventory.data.local.model.SyncStatusRecord;
import com.medicationinventory.data.local.model.SyncStatusSummary;

import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;

import com.medicationinventory.sync.InventorySyncScheduler;

import java.util.List;




/**
 * Exposes the synchronization summary, recent operation status, and refresh actions to the status screen.
 */
public class SyncStatusViewModel
        extends ViewModel
{
    private final String
            userId;


    private final SyncStatusRepository
            syncStatusRepository;


    private final SyncMetadataRepository
            syncMetadataRepository;


    private final InventorySyncScheduler
            inventorySyncScheduler;


    private final LiveData<SyncStatusSummary>
            summary;


    private final LiveData<List<SyncStatusRecord>>
            recentOperations;


    private final LiveData<SyncMetadataEntity>
            syncMetadata;



    public SyncStatusViewModel(
            String userId,
            SyncStatusRepository syncStatusRepository,
            SyncMetadataRepository syncMetadataRepository,
            InventorySyncScheduler inventorySyncScheduler)
    {
        if (userId == null
                ||
                userId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        if (syncStatusRepository == null
                ||
                syncMetadataRepository == null
                ||
                inventorySyncScheduler == null)
        {
            throw new IllegalArgumentException(
                    "Synchronisation dependencies are required."
            );
        }


        this.userId =
                userId.trim();


        this.syncStatusRepository =
                syncStatusRepository;


        this.syncMetadataRepository =
                syncMetadataRepository;


        this.inventorySyncScheduler =
                inventorySyncScheduler;


        summary =
                syncStatusRepository
                        .observeSummary(
                                this.userId
                        );


        recentOperations =
                syncStatusRepository
                        .observeRecent(
                                this.userId
                        );


        syncMetadata =
                syncMetadataRepository
                        .observeInventorySync();
    }


    public LiveData<SyncStatusSummary>
    getSummary()
    {
        return summary;
    }


    public LiveData<List<SyncStatusRecord>>
    getRecentOperations()
    {
        return recentOperations;
    }


    public LiveData<SyncMetadataEntity>
    getSyncMetadata()
    {
        return syncMetadata;
    }



    public void requestSync()
    {
        inventorySyncScheduler
                .schedulePendingSync();
    }
}