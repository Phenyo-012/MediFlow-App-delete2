package com.medicationinventory.ui.reports.analytics;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AnalyticsReportRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.AbcVedAnalytics;
import com.medicationinventory.model.domain.AnalyticsReport;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.MedicineClassification;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.service.AbcVedAnalyticsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Combines the local analytics report with the optional ABC/VED classification summary shown on the
 * report screen. Movement-window selection refreshes the report source, while classification reads
 * and refresh status are gated by the current profile's restricted-data permission. Account changes
 * clear that restricted result so a prior user's classifications are not retained in visible state.
 */
public class AnalyticsReportViewModel
        extends ViewModel
{
    public static final int WINDOW_7_DAYS =
            7;

    public static final int WINDOW_30_DAYS =
            30;

    public static final int WINDOW_90_DAYS =
            90;


    private final AnalyticsReportRepository
            analyticsReportRepository;


    private final MedicineClassificationRepository
            medicineClassificationRepository;


    private final LiveData<UserProfile>
            userProfile;


    private final LiveData<Long>
            lastSuccessfulSyncAt;


    private final LiveData<List<Medicine>>
            activeMedicinesSource;


    private final AbcVedAnalyticsService
            abcVedAnalyticsService =
            new AbcVedAnalyticsService();


    private final MediatorLiveData<AnalyticsReport>
            report =
            new MediatorLiveData<>();


    private final MediatorLiveData<AbcVedAnalytics>
            abcVedAnalytics =
            new MediatorLiveData<>();


    private final MutableLiveData<String>
            abcVedRefreshStatus =
            new MutableLiveData<>(
                    null
            );


    private final MutableLiveData<Integer>
            selectedMovementWindowDays =
            new MutableLiveData<>(
                    WINDOW_30_DAYS
            );


    private LiveData<AnalyticsReport>
            currentReportSource;


    private LiveData<List<MedicineClassification>>
            currentClassificationSource;


    private List<Medicine>
            latestActiveMedicines =
            Collections.emptyList();


    private boolean started =
            false;


    private boolean abcVedAccessEnabled =
            false;


    private boolean abcVedInitialRefreshRequested =
            false;


    private boolean abcVedRefreshInProgress =
            false;


    public AnalyticsReportViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AnalyticsReportRepository analyticsReportRepository,
            SyncMetadataRepository syncMetadataRepository,
            MedicineRepository medicineRepository,
            MedicineClassificationRepository medicineClassificationRepository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                analyticsReportRepository == null
                ||
                syncMetadataRepository == null
                ||
                medicineRepository == null
                ||
                medicineClassificationRepository == null)
        {
            throw new IllegalArgumentException(
                    "Analytics report dependencies are required."
            );
        }


        this.analyticsReportRepository =
                analyticsReportRepository;


        this.medicineClassificationRepository =
                medicineClassificationRepository;


        activeMedicinesSource =
                medicineRepository
                        .observeActiveMedicines();


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            userProfile =
                    new MutableLiveData<>(
                            null
                    );
        }
        else
        {
            userProfile =
                    userRepository
                            .observeUser(
                                    uid.trim()
                            );
        }


        lastSuccessfulSyncAt =
                syncMetadataRepository
                        .observeLastSuccessfulInventorySyncAt();
    }


    public void start()
    {
        if (started)
        {
            return;
        }


        started =
                true;


        Integer selected =
                selectedMovementWindowDays
                        .getValue();


        attachReportSource(
                selected == null
                        ? WINDOW_30_DAYS
                        : selected
        );
    }


    public void setMovementWindowDays(
            int days)
    {
        if (days != WINDOW_7_DAYS
                &&
                days != WINDOW_30_DAYS
                &&
                days != WINDOW_90_DAYS)
        {
            throw new IllegalArgumentException(
                    "Unsupported analytics movement window."
            );
        }


        Integer current =
                selectedMovementWindowDays
                        .getValue();


        if (current != null
                &&
                current == days)
        {
            return;
        }


        selectedMovementWindowDays
                .setValue(
                        days
                );


        if (started)
        {
            attachReportSource(
                    days
            );
        }
    }


    public void refresh()
    {
        if (started)
        {
            Integer selected =
                    selectedMovementWindowDays
                            .getValue();


            attachReportSource(
                    selected == null
                            ? WINDOW_30_DAYS
                            : selected
            );
        }


        if (abcVedAccessEnabled)
        {
            refreshAbcVedFromServer();
        }
    }


    private void attachReportSource(
            int days)
    {
        if (currentReportSource != null)
        {
            report.removeSource(
                    currentReportSource
            );
        }


        currentReportSource =
                analyticsReportRepository
                        .observeReport(
                                days
                        );


        report.addSource(
                currentReportSource,
                report::setValue
        );
    }


    /*
     * RESTRICTED ABC/VED ANALYTICS
     */
    public void setAbcVedAccess(
            boolean allowed)
    {
        if (abcVedAccessEnabled
                ==
                allowed)
        {
            return;
        }


        abcVedAccessEnabled =
                allowed;


        if (!allowed)
        {
            detachAbcVedSources();

            latestActiveMedicines =
                    Collections.emptyList();


            abcVedInitialRefreshRequested =
                    false;


            abcVedRefreshInProgress =
                    false;


            abcVedAnalytics.setValue(
                    null
            );


            abcVedRefreshStatus.setValue(
                    null
            );


            return;
        }


        abcVedInitialRefreshRequested =
                false;


        abcVedAnalytics.addSource(
                activeMedicinesSource,
                medicines ->
                {
                    latestActiveMedicines =
                            medicines == null
                                    ? Collections.emptyList()
                                    : new ArrayList<>(
                                    medicines
                            );


                    if (!abcVedInitialRefreshRequested)
                    {
                        abcVedInitialRefreshRequested =
                                true;


                        refreshAbcVedFromServer();
                    }
                }
        );


        currentClassificationSource =
                medicineClassificationRepository
                        .observeActiveForAnalytics();


        abcVedAnalytics.addSource(
                currentClassificationSource,
                classifications ->
                {
                    if (!abcVedAccessEnabled)
                    {
                        return;
                    }


                    abcVedAnalytics.setValue(
                            abcVedAnalyticsService
                                    .build(
                                            classifications
                                    )
                    );
                }
        );
    }


    private void detachAbcVedSources()
    {
        abcVedAnalytics.removeSource(
                activeMedicinesSource
        );


        if (currentClassificationSource != null)
        {
            abcVedAnalytics.removeSource(
                    currentClassificationSource
            );


            currentClassificationSource =
                    null;
        }
    }


    public void refreshAbcVedFromServer()
    {
        if (!abcVedAccessEnabled
                ||
                abcVedRefreshInProgress)
        {
            return;
        }


        if (latestActiveMedicines == null
                ||
                latestActiveMedicines.isEmpty())
        {
            abcVedRefreshStatus.setValue(
                    "No active medicines require ABC-VED refresh."
            );

            return;
        }


        List<String> medicineIds =
                new ArrayList<>();


        for (Medicine medicine :
                latestActiveMedicines)
        {
            if (medicine == null
                    ||
                    medicine.getMedicineId() == null
                    ||
                    medicine.getMedicineId()
                            .trim()
                            .isEmpty())
            {
                continue;
            }


            medicineIds.add(
                    medicine.getMedicineId()
                            .trim()
            );
        }


        if (medicineIds.isEmpty())
        {
            abcVedRefreshStatus.setValue(
                    "No active medicines require ABC-VED refresh."
            );

            return;
        }


        abcVedRefreshInProgress =
                true;


        abcVedRefreshStatus.setValue(
                "Refreshing restricted ABC-VED classifications..."
        );


        medicineClassificationRepository
                .refreshAllFromServer(
                        medicineIds,
                        (
                                successful,
                                failed
                        ) ->
                        {
                            abcVedRefreshInProgress =
                                    false;



                            if (!abcVedAccessEnabled)
                            {
                                return;
                            }


                            if (failed == 0)
                            {
                                abcVedRefreshStatus.setValue(
                                        "ABC-VED classifications refreshed from the server."
                                );
                            }
                            else if (successful > 0)
                            {
                                abcVedRefreshStatus.setValue(
                                        "ABC-VED refresh was partially successful. "
                                                +
                                                "Cached data is being used for "
                                                +
                                                failed
                                                +
                                                " medicine(s)."
                                );
                            }
                            else
                            {
                                abcVedRefreshStatus.setValue(
                                        "Unable to refresh ABC-VED classifications. "
                                                +
                                                "Showing the restricted local cache."
                                );
                            }
                        }
                );
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<AnalyticsReport>
    getReport()
    {
        return report;
    }


    public LiveData<String>
    getError()
    {
        return analyticsReportRepository
                .observeError();
    }


    public LiveData<Long>
    getLastSuccessfulSyncAt()
    {
        return lastSuccessfulSyncAt;
    }


    public LiveData<Integer>
    getSelectedMovementWindowDays()
    {
        return selectedMovementWindowDays;
    }


    public LiveData<AbcVedAnalytics>
    getAbcVedAnalytics()
    {
        return abcVedAnalytics;
    }


    public LiveData<String>
    getAbcVedRefreshStatus()
    {
        return abcVedRefreshStatus;
    }
}