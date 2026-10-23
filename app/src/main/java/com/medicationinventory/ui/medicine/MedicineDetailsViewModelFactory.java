package com.medicationinventory.ui.medicine;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.service.ReorderRecommendationService;


/**
 * Creates the Medicine Details ViewModel with its application dependencies.
 */
public class MedicineDetailsViewModelFactory
        implements ViewModelProvider.Factory
{
    private final String medicineId;

    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MedicineRepository
            medicineRepository;

    private final BatchRepository
            batchRepository;

    private final MedicineClassificationRepository
            medicineClassificationRepository;

    private final ReorderRecommendationService
            reorderRecommendationService;

    private final InventorySettingsRepository
            inventorySettingsRepository;


    public MedicineDetailsViewModelFactory(
            String medicineId,
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            BatchRepository batchRepository,
            MedicineClassificationRepository medicineClassificationRepository,
            ReorderRecommendationService reorderRecommendationService,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Medicine ID is required."
            );
        }


        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                medicineRepository == null
                ||
                batchRepository == null
                ||
                medicineClassificationRepository == null
                ||
                reorderRecommendationService == null
                ||
                inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Medicine details dependencies are required."
            );
        }


        this.medicineId =
                medicineId.trim();


        this.authenticationRepository =
                authenticationRepository;


        this.userRepository =
                userRepository;


        this.medicineRepository =
                medicineRepository;


        this.batchRepository =
                batchRepository;


        this.medicineClassificationRepository =
                medicineClassificationRepository;


        this.reorderRecommendationService =
                reorderRecommendationService;


        this.inventorySettingsRepository =
                inventorySettingsRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                MedicineDetailsViewModel.class
        ))
        {
            return (T) new MedicineDetailsViewModel(
                    medicineId,
                    authenticationRepository,
                    userRepository,
                    medicineRepository,
                    batchRepository,
                    medicineClassificationRepository,
                    reorderRecommendationService,
                    inventorySettingsRepository
            );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}