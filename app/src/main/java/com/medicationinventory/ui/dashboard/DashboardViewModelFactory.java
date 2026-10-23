package com.medicationinventory.ui.dashboard;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.UserRepository;




/**
 * Creates the Dashboard ViewModel with its application dependencies.
 */
public class DashboardViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MedicineRepository
            medicineRepository;

    private final ReorderRecommendationsRepository
            reorderRecommendationsRepository;

    private final AlertRepository
            alertRepository;

    private final SyncStatusRepository
            syncStatusRepository;

    private final SyncMetadataRepository
            syncMetadataRepository;

    private final MovementHistoryRepository
            movementHistoryRepository;



    public DashboardViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            ReorderRecommendationsRepository
                    reorderRecommendationsRepository,
            AlertRepository alertRepository,
            SyncStatusRepository syncStatusRepository,
            SyncMetadataRepository syncMetadataRepository,
            MovementHistoryRepository movementHistoryRepository)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.medicineRepository =
                medicineRepository;

        this.reorderRecommendationsRepository =
                reorderRecommendationsRepository;

        this.alertRepository =
                alertRepository;

        this.syncStatusRepository =
                syncStatusRepository;

        this.syncMetadataRepository =
                syncMetadataRepository;

        this.movementHistoryRepository =
                movementHistoryRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                DashboardViewModel.class
        ))
        {
            return (T)
                    new DashboardViewModel(
                            authenticationRepository,
                            userRepository,
                            medicineRepository,
                            reorderRecommendationsRepository,
                            alertRepository,
                            syncStatusRepository,
                            syncMetadataRepository,
                            movementHistoryRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}