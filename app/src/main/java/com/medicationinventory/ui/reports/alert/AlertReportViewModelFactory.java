package com.medicationinventory.ui.reports.alert;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AlertReportRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.service.ReportDateRangeService;


/**
 * Creates the Alert Report ViewModel with its application dependencies.
 */
public class AlertReportViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final AlertReportRepository
            alertReportRepository;

    private final ReportDateRangeService
            reportDateRangeService;



    public AlertReportViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AlertReportRepository alertReportRepository,
            ReportDateRangeService reportDateRangeService)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.alertReportRepository =
                alertReportRepository;

        this.reportDateRangeService =
                reportDateRangeService;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                AlertReportViewModel.class
        ))
        {
            return (T)
                    new AlertReportViewModel(
                            authenticationRepository,
                            userRepository,
                            alertReportRepository,
                            reportDateRangeService
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}