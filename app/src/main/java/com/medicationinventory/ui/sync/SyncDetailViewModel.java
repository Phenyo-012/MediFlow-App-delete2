package com.medicationinventory.ui.sync;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.SyncStatusRecord;
import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.sync.InventorySyncScheduler;




/**
 * Exposes the selected operation status and retry action to the synchronization detail screen.
 */
public class SyncDetailViewModel
        extends ViewModel
{
    private final LiveData<SyncStatusRecord>
            operation;

    private final String userId;
    private final String operationId;
    private final InventorySyncRepository inventorySyncRepository;


    private final InventorySyncScheduler
            inventorySyncScheduler;



    public SyncDetailViewModel(
            String userId,
            String operationId,
            SyncStatusRepository syncStatusRepository,
            InventorySyncRepository inventorySyncRepository,
            InventorySyncScheduler inventorySyncScheduler)
    {
        if (syncStatusRepository == null
                || inventorySyncRepository == null
                || inventorySyncScheduler == null)
        {
            throw new IllegalArgumentException(
                    "Synchronisation dependencies are required."
            );
        }


        this.userId = userId;
        this.operationId = operationId;
        this.inventorySyncRepository = inventorySyncRepository;

        operation =
                syncStatusRepository
                        .observeById(
                                userId,
                                operationId
                        );


        this.inventorySyncScheduler =
                inventorySyncScheduler;
    }


    public LiveData<SyncStatusRecord>
    getOperation()
    {
        return operation;
    }



    public void retryHeldPermissionDeniedUpload(
            InventorySyncRepository.ManualRetryCallback callback)
    {
        inventorySyncRepository.retryHeldPermissionDeniedUpload(
                operationId,
                userId,
                new InventorySyncRepository.ManualRetryCallback()
                {
                    @Override

                    public void onReady()
                    {
                        inventorySyncScheduler.schedulePendingSync();
                        callback.onReady();
                    }

                    @Override

                    public void onError(String message)
                    {
                        callback.onError(message);
                    }
                });
    }


    public void requestSync()
    {
        inventorySyncScheduler
                .schedulePendingSync();
    }
}