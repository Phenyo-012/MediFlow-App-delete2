package com.medicationinventory.ui.reorder;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.UserRepository;


/**
 * Creates the Reorder Recommendations ViewModel with its application dependencies.
 */
public class ReorderRecommendationsViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final ReorderRecommendationsRepository
            reorderRecommendationsRepository;



    public ReorderRecommendationsViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            ReorderRecommendationsRepository
                    reorderRecommendationsRepository)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.reorderRecommendationsRepository =
                reorderRecommendationsRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                ReorderRecommendationsViewModel.class
        ))
        {
            return (T)
                    new ReorderRecommendationsViewModel(
                            authenticationRepository,
                            userRepository,
                            reorderRecommendationsRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}