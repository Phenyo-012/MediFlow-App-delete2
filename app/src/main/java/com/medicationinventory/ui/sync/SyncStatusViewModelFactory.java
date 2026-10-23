package com.medicationinventory.ui.sync;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;

import com.medicationinventory.sync.InventorySyncScheduler;




/**
 * Builds the status ViewModel with the signed-in user and its repository and scheduler dependencies.
 */
public class SyncStatusViewModelFactory
        implements ViewModelProvider.Factory
{
    private final String
            userId;


    private final SyncStatusRepository
            syncStatusRepository;


    private final SyncMetadataRepository
            syncMetadataRepository;


    private final InventorySyncScheduler
            inventorySyncScheduler;



    public SyncStatusViewModelFactory(
            String userId,
            SyncStatusRepository syncStatusRepository,
            SyncMetadataRepository syncMetadataRepository,
            InventorySyncScheduler inventorySyncScheduler)
    {
        this.userId =
                userId;

        this.syncStatusRepository =
                syncStatusRepository;

        this.syncMetadataRepository =
                syncMetadataRepository;

        this.inventorySyncScheduler =
                inventorySyncScheduler;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                SyncStatusViewModel.class
        ))
        {
            return (T)
                    new SyncStatusViewModel(
                            userId,
                            syncStatusRepository,
                            syncMetadataRepository,
                            inventorySyncScheduler
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}