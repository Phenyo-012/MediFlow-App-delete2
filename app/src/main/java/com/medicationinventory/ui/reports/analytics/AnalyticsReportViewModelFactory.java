package com.medicationinventory.ui.reports.analytics;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AnalyticsReportRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.UserRepository;


/**
 * Creates the Analytics Report ViewModel with its application dependencies.
 */
public class AnalyticsReportViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final AnalyticsReportRepository
            analyticsReportRepository;

    private final SyncMetadataRepository
            syncMetadataRepository;

    private final MedicineRepository
            medicineRepository;

    private final MedicineClassificationRepository
            medicineClassificationRepository;


    public AnalyticsReportViewModelFactory(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AnalyticsReportRepository analyticsReportRepository,
            SyncMetadataRepository syncMetadataRepository,
            MedicineRepository medicineRepository,
            MedicineClassificationRepository medicineClassificationRepository)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.analyticsReportRepository =
                analyticsReportRepository;

        this.syncMetadataRepository =
                syncMetadataRepository;

        this.medicineRepository =
                medicineRepository;

        this.medicineClassificationRepository =
                medicineClassificationRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                AnalyticsReportViewModel.class
        ))
        {
            return (T)
                    new AnalyticsReportViewModel(
                            authenticationRepository,
                            userRepository,
                            analyticsReportRepository,
                            syncMetadataRepository,
                            medicineRepository,
                            medicineClassificationRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        +
                        modelClass.getName()
        );
    }
}