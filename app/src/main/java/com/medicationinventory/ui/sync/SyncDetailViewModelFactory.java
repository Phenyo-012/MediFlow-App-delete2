package com.medicationinventory.ui.sync;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.sync.InventorySyncScheduler;




/**
 * Builds the detail ViewModel with operation identifiers and the required repository and scheduler dependencies.
 */
public class SyncDetailViewModelFactory
        implements ViewModelProvider.Factory
{
    private final String userId;
    private final String operationId;

    private final SyncStatusRepository
            syncStatusRepository;

    private final InventorySyncRepository inventorySyncRepository;

    private final InventorySyncScheduler
            inventorySyncScheduler;


    public SyncDetailViewModelFactory(
            String userId,
            String operationId,
            SyncStatusRepository syncStatusRepository,
            InventorySyncRepository inventorySyncRepository,
            InventorySyncScheduler inventorySyncScheduler)
    {
        this.userId =
                userId;

        this.operationId =
                operationId;

        this.syncStatusRepository =
                syncStatusRepository;

        this.inventorySyncRepository = inventorySyncRepository;

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
                SyncDetailViewModel.class
        ))
        {
            return (T)
                    new SyncDetailViewModel(
                            userId,
                            operationId,
                            syncStatusRepository,
                            inventorySyncRepository,
                            inventorySyncScheduler
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}