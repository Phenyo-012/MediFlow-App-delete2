package com.medicationinventory.ui.reports.movement;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementReportRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.service.ReportDateRangeService;



/**
 * Creates the Movement Report ViewModel with its application dependencies.
 */
public class MovementReportViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MovementReportRepository
            repository;

    private final ReportDateRangeService
            dateRangeService;


    public MovementReportViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MovementReportRepository repository,
            ReportDateRangeService dateRangeService)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.repository =
                repository;

        this.dateRangeService =
                dateRangeService;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                MovementReportViewModel.class
        ))
        {
            return (T)
                    new MovementReportViewModel(
                            authenticationRepository,
                            userRepository,
                            repository,
                            dateRangeService
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}