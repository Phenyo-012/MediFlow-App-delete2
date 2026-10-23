package com.medicationinventory.ui.reports.stock;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.StockLevelReportRepository;
import com.medicationinventory.data.repository.UserRepository;

/**
 * Creates the Stock Level Report ViewModel with its application dependencies.
 */
public class StockLevelReportViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final StockLevelReportRepository
            repository;


    public StockLevelReportViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            StockLevelReportRepository repository)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.repository =
                repository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                StockLevelReportViewModel.class
        ))
        {
            return (T)
                    new StockLevelReportViewModel(
                            authenticationRepository,
                            userRepository,
                            repository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}