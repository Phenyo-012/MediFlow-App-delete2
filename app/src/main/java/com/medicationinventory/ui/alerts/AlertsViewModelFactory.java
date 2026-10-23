package com.medicationinventory.ui.alerts;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;



/**
 * Creates the Alerts ViewModel with its application dependencies.
 */
public class AlertsViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final AlertRepository
            alertRepository;



    public AlertsViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AlertRepository alertRepository)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.alertRepository =
                alertRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                AlertsViewModel.class
        ))
        {
            return (T)
                    new AlertsViewModel(
                            authenticationRepository,
                            userRepository,
                            alertRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}