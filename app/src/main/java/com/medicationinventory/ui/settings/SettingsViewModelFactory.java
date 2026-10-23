package com.medicationinventory.ui.settings;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.UserRepository;




/**
 * Creates the Settings ViewModel with its application dependencies.
 */
public class SettingsViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;


    private final UserRepository
            userRepository;


    private final InventorySettingsRepository
            inventorySettingsRepository;



    public SettingsViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            InventorySettingsRepository inventorySettingsRepository)
    {
        this.authenticationRepository =
                authenticationRepository;


        this.userRepository =
                userRepository;


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
                SettingsViewModel.class
        ))
        {
            return (T)
                    new SettingsViewModel(
                            authenticationRepository,
                            userRepository,
                            inventorySettingsRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class."
        );
    }
}