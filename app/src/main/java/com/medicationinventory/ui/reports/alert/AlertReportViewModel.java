package com.medicationinventory.ui.reports.alert;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.AlertReportRecord;
import com.medicationinventory.data.local.model.AlertReportBatchOption;
import com.medicationinventory.data.local.model.AlertReportMedicineOption;

import com.medicationinventory.data.repository.AlertReportRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReportDateRange;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;

import com.medicationinventory.service.ReportDateRangeService;

import java.time.LocalDate;
import java.util.List;


/**
 * Coordinates the local alert report with date, medicine, batch, alert-type, and status selections.
 * It derives preset ranges through {@link ReportDateRangeService}, swaps the repository's LiveData
 * sources as filters change, and exposes medicine/batch options for dependent selectors. Report
 * rows remain a Room-backed view of locally reconciled alerts rather than a separate cloud query.
 */
public class AlertReportViewModel
        extends ViewModel
{
    private final AlertReportRepository
            repository;

    private final ReportDateRangeService
            dateRangeService;


    private final LiveData<UserProfile>
            userProfile;


    private final MediatorLiveData<List<AlertReportRecord>>
            reportItems =
            new MediatorLiveData<>();

    private final MediatorLiveData<List<AlertReportMedicineOption>>
            medicineOptions =
            new MediatorLiveData<>();


    private final MediatorLiveData<List<AlertReportBatchOption>>
            batchOptions =
            new MediatorLiveData<>();


    private LiveData<List<AlertReportMedicineOption>>
            currentMedicineOptionSource;


    private LiveData<List<AlertReportBatchOption>>
            currentBatchOptionSource;


    private String
            currentMedicineId;

    private String
            currentBatchId;


    private LiveData<List<AlertReportRecord>>
            currentSource;


    private ReportDateRange
            currentDateRange;

    private AlertType
            currentAlertType;

    private AlertStatus
            currentStatus;


    public AlertReportViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            AlertReportRepository repository,
            ReportDateRangeService dateRangeService)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                repository == null
                ||
                dateRangeService == null)
        {
            throw new IllegalArgumentException(
                    "Alert report dependencies are required."
            );
        }


        this.repository =
                repository;

        this.dateRangeService =
                dateRangeService;


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null)
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
                                    uid
                            );
        }


        currentDateRange =
                dateRangeService
                        .last30Days();


        reload();
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<List<AlertReportRecord>>
    getReportItems()
    {
        return reportItems;
    }


    public ReportDateRange
    getCurrentDateRange()
    {
        return currentDateRange;
    }


    public void useToday()
    {
        currentDateRange =
                dateRangeService.today();

        reloadAllForDateRange();
    }


    public void useYesterday()
    {
        currentDateRange =
                dateRangeService.yesterday();

        reloadAllForDateRange();
    }



    public void useLast7Days()
    {
        currentDateRange =
                dateRangeService.last7Days();

        reloadAllForDateRange();
    }



    public void useLast30Days()
    {
        currentDateRange =
                dateRangeService.last30Days();

        reloadAllForDateRange();
    }


    public void useThisMonth()
    {
        currentDateRange =
                dateRangeService.thisMonth();

        reloadAllForDateRange();
    }



    public void usePreviousMonth()
    {
        currentDateRange =
                dateRangeService.previousMonth();

        reloadAllForDateRange();
    }



    public void useCustomRange(
            LocalDate startDate,
            LocalDate endDate)
    {
        currentDateRange =
                dateRangeService
                        .fromDates(
                                startDate,
                                endDate
                        );

        reloadAllForDateRange();
    }


    public void setAlertType(
            AlertType alertType)
    {
        currentAlertType =
                alertType;

        reload();
    }


    public void setStatus(
            AlertStatus status)
    {
        currentStatus =
                status;

        reloadMedicineOptions();

        reloadBatchOptions();

        reload();
    }


    private void reload()
    {
        if (currentSource != null)
        {
            reportItems.removeSource(
                    currentSource
            );
        }


        currentSource =
                repository.observeReport(
                        currentDateRange,
                        currentMedicineId,
                        currentBatchId,
                        currentAlertType,
                        currentStatus
                );


        reportItems.addSource(
                currentSource,
                reportItems::setValue
        );
    }


    private void reloadMedicineOptions()
    {
        if (currentMedicineOptionSource != null)
        {
            medicineOptions.removeSource(
                    currentMedicineOptionSource
            );
        }


        currentMedicineOptionSource =
                repository
                        .observeMedicineOptions(
                                currentDateRange
                        );


        medicineOptions.addSource(
                currentMedicineOptionSource,
                medicineOptions::setValue
        );
    }

    private void reloadAllForDateRange()
    {
        /*
         * Clear selections that might not exist in
         * the new reporting period.
         */
        currentMedicineId =
                null;

        currentBatchId =
                null;


        reloadMedicineOptions();

        reloadBatchOptions();

        reload();
    }

    private void reloadBatchOptions()
    {
        if (currentBatchOptionSource != null)
        {
            batchOptions.removeSource(
                    currentBatchOptionSource
            );
        }


        currentBatchOptionSource =
                repository
                        .observeBatchOptions(
                                currentDateRange,
                                currentMedicineId
                        );


        batchOptions.addSource(
                currentBatchOptionSource,
                batchOptions::setValue
        );
    }

    public LiveData<List<AlertReportMedicineOption>>
    getMedicineOptions()
    {
        return medicineOptions;
    }


    public LiveData<List<AlertReportBatchOption>>
    getBatchOptions()
    {
        return batchOptions;
    }


    public String
    getCurrentMedicineId()
    {
        return currentMedicineId;
    }


    public String
    getCurrentBatchId()
    {
        return currentBatchId;
    }

    public void setMedicine(
            String medicineId)
    {
        currentMedicineId =
                normalizeOptional(
                        medicineId
                );



        currentBatchId =
                null;


        reloadBatchOptions();

        reload();
    }


    public void setBatch(
            String batchId)
    {
        currentBatchId =
                normalizeOptional(
                        batchId
                );


        reload();
    }


    private String normalizeOptional(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim();


        return normalized.isEmpty()
                ? null
                : normalized;
    }
}