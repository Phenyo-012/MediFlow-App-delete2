package com.medicationinventory.ui.inventory;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.UserRepository;



/**
 * Creates the Inventory ViewModel with its application dependencies.
 */
public class InventoryViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository authenticationRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineClassificationRepository medicineClassificationRepository;

    public InventoryViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            MedicineClassificationRepository medicineClassificationRepository
    )
    {
        this.authenticationRepository = authenticationRepository;
        this.userRepository = userRepository;
        this.medicineRepository = medicineRepository;
        this.medicineClassificationRepository = medicineClassificationRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(InventoryViewModel.class))
        {

            return (T) new InventoryViewModel(
                    authenticationRepository, userRepository, medicineRepository,
                    medicineClassificationRepository);
        }

        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName()
        );
    }
}
