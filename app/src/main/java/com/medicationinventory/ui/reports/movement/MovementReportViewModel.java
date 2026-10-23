package com.medicationinventory.ui.reports.movement;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementReportBatchOption;
import com.medicationinventory.data.local.model.MovementReportMedicineOption;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementReportRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReportDateRange;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.service.ReportDateRangeService;

import java.time.LocalDate;
import java.util.List;


/**
 * Coordinates a local movement report across date range, medicine, batch, and transaction-type
 * filters. It replaces the Room LiveData source as the selected range or filters change, exposes
 * dependent medicine/batch options, and increases the query limit for incremental loading; it does
 * not merge server history directly into the report.
 */
public class MovementReportViewModel
        extends ViewModel
{
    private final MovementReportRepository
            repository;

    private final ReportDateRangeService
            dateRangeService;


    private final LiveData<UserProfile>
            userProfile;


    private final MediatorLiveData<List<MovementHistoryRecord>>
            reportItems =
            new MediatorLiveData<>();


    private final MediatorLiveData<List<MovementReportMedicineOption>>
            medicineOptions =
            new MediatorLiveData<>();


    private final MediatorLiveData<List<MovementReportBatchOption>>
            batchOptions =
            new MediatorLiveData<>();


    private final MutableLiveData<Boolean>
            canLoadMore =
            new MutableLiveData<>(
                    false
            );


    private LiveData<List<MovementHistoryRecord>>
            currentReportSource;

    private LiveData<List<MovementReportMedicineOption>>
            currentMedicineOptionSource;

    private LiveData<List<MovementReportBatchOption>>
            currentBatchOptionSource;


    private ReportDateRange
            currentDateRange;

    private String
            currentMedicineId;

    private String
            currentBatchId;

    private String
            currentTransactionType;


    private int currentLimit =
            MovementReportRepository.DEFAULT_PAGE_SIZE;


    public MovementReportViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MovementReportRepository repository,
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
                    "Movement report dependencies are required."
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


        reloadMedicineOptions();

        reloadBatchOptions();

        reloadReport();
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<List<MovementHistoryRecord>>
    getReportItems()
    {
        return reportItems;
    }


    public LiveData<List<MovementReportMedicineOption>>
    getMedicineOptions()
    {
        return medicineOptions;
    }


    public LiveData<List<MovementReportBatchOption>>
    getBatchOptions()
    {
        return batchOptions;
    }


    public LiveData<Boolean>
    getCanLoadMore()
    {
        return canLoadMore;
    }


    public ReportDateRange
    getCurrentDateRange()
    {
        return currentDateRange;
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


    public void useToday()
    {
        currentDateRange =
                dateRangeService.today();

        dateRangeChanged();
    }


    public void useYesterday()
    {
        currentDateRange =
                dateRangeService.yesterday();

        dateRangeChanged();
    }



    public void useLast7Days()
    {
        currentDateRange =
                dateRangeService.last7Days();

        dateRangeChanged();
    }


    public void useLast30Days()
    {
        currentDateRange =
                dateRangeService.last30Days();

        dateRangeChanged();
    }


    public void useThisMonth()
    {
        currentDateRange =
                dateRangeService.thisMonth();

        dateRangeChanged();
    }


    public void usePreviousMonth()
    {
        currentDateRange =
                dateRangeService.previousMonth();

        dateRangeChanged();
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

        dateRangeChanged();
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


        resetLimit();

        reloadBatchOptions();

        reloadReport();
    }


    public void setBatch(
            String batchId)
    {
        currentBatchId =
                normalizeOptional(
                        batchId
                );


        resetLimit();

        reloadReport();
    }


    public void setTransactionType(
            String transactionType)
    {
        currentTransactionType =
                normalizeOptional(
                        transactionType
                );


        resetLimit();

        reloadReport();
    }


    public void loadMore()
    {
        currentLimit +=
                MovementReportRepository.DEFAULT_PAGE_SIZE;


        reloadReport();
    }


    private void dateRangeChanged()
    {
        currentMedicineId =
                null;

        currentBatchId =
                null;


        resetLimit();

        reloadMedicineOptions();

        reloadBatchOptions();

        reloadReport();
    }


    private void resetLimit()
    {
        currentLimit =
                MovementReportRepository.DEFAULT_PAGE_SIZE;
    }


    private void reloadReport()
    {
        if (currentReportSource != null)
        {
            reportItems.removeSource(
                    currentReportSource
            );
        }


        reportItems.setValue(
                null
        );


        canLoadMore.setValue(
                false
        );


        currentReportSource =
                repository.observeReport(
                        currentDateRange,
                        currentMedicineId,
                        currentBatchId,
                        currentTransactionType,
                        currentLimit
                );


        reportItems.addSource(
                currentReportSource,
                items ->
                {
                    reportItems.setValue(
                            items
                    );


                    int size =
                            items == null
                                    ? 0
                                    : items.size();

                    canLoadMore.setValue(
                            size >= currentLimit
                    );
                }
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