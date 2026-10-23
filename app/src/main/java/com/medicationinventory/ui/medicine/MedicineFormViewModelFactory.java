package com.medicationinventory.ui.medicine;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

/**
 * Creates the Medicine Form ViewModel with its application dependencies.
 */
public class MedicineFormViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository authenticationRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;

    public MedicineFormViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository)
    {
        this.authenticationRepository = authenticationRepository;
        this.userRepository = userRepository;
        this.medicineRepository = medicineRepository;
    }
    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass)
    {

        if (modelClass.isAssignableFrom(MedicineFormViewModel.class))
        {

            return (T) new MedicineFormViewModel(
                    authenticationRepository, userRepository, medicineRepository);
        }

        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}