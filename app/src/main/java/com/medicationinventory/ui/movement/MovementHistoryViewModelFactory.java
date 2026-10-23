package com.medicationinventory.ui.movement;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.UserRepository;


/**
 * Creates the Movement History ViewModel with its application dependencies.
 */
public class MovementHistoryViewModelFactory
        implements ViewModelProvider.Factory
{
    private final String medicineId;

    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MovementHistoryRepository
            movementHistoryRepository;



    public MovementHistoryViewModelFactory(
            String medicineId,
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MovementHistoryRepository movementHistoryRepository)
    {
        this.medicineId =
                medicineId;

        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

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
                MovementHistoryViewModel.class
        ))
        {
            return (T)
                    new MovementHistoryViewModel(
                            medicineId,
                            authenticationRepository,
                            userRepository,
                            movementHistoryRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class."
        );
    }
}