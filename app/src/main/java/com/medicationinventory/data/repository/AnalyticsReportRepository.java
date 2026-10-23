package com.medicationinventory.data.repository;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.medicationinventory.data.local.dao.AlertDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.MedicineDao;
import com.medicationinventory.data.local.dao.TransactionDao;

import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.AnalyticsReport;

import com.medicationinventory.service.AnalyticsReportService;

import com.medicationinventory.util.AppExecutors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.concurrent.atomic.AtomicInteger;



/**
 * Combines local medicine, batch, transaction, and alert data into analytics report snapshots.
 */
public class AnalyticsReportRepository
{
    public static final int
            DEFAULT_MOVEMENT_WINDOW_DAYS =
            30;


    public static final int
            MAX_MOVEMENT_WINDOW_DAYS =
            365;


    private final MedicineDao
            medicineDao;


    private final BatchDao
            batchDao;


    private final AlertDao
            alertDao;


    private final TransactionDao
            transactionDao;


    private final AnalyticsReportService
            analyticsReportService;


    private final AppExecutors
            appExecutors;


    private final MutableLiveData<String>
            error =
            new MutableLiveData<>(
                    null
            );


    public AnalyticsReportRepository(
            MedicineDao medicineDao,
            BatchDao batchDao,
            AlertDao alertDao,
            TransactionDao transactionDao,
            AnalyticsReportService analyticsReportService,
            AppExecutors appExecutors)
    {
        if (medicineDao == null
                ||
                batchDao == null
                ||
                alertDao == null
                ||
                transactionDao == null
                ||
                analyticsReportService == null
                ||
                appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "Analytics report dependencies are required."
            );
        }


        this.medicineDao =
                medicineDao;

        this.batchDao =
                batchDao;

        this.alertDao =
                alertDao;

        this.transactionDao =
                transactionDao;

        this.analyticsReportService =
                analyticsReportService;

        this.appExecutors =
                appExecutors;
    }


    public LiveData<AnalyticsReport>
    observeReport()
    {
        return observeReport(
                DEFAULT_MOVEMENT_WINDOW_DAYS
        );
    }


    public LiveData<AnalyticsReport>
    observeReport(
            int movementWindowDays)
    {
        validateMovementWindow(
                movementWindowDays
        );


        long nowMillis =
                System.currentTimeMillis();


        MovementWindow window =
                buildMovementWindow(
                        movementWindowDays,
                        nowMillis
                );


        return new ReportCoordinator(
                movementWindowDays,
                nowMillis,
                window.startInclusive,
                window.endExclusive
        )
                .getReport();
    }


    public LiveData<String>
    observeError()
    {
        return error;
    }


    private void validateMovementWindow(
            int movementWindowDays)
    {
        if (movementWindowDays <= 0
                ||
                movementWindowDays
                        >
                        MAX_MOVEMENT_WINDOW_DAYS)
        {
            throw new IllegalArgumentException(
                    "Analytics movement window must be between 1 and "
                            +
                            MAX_MOVEMENT_WINDOW_DAYS
                            +
                            " days."
            );
        }
    }


    private MovementWindow buildMovementWindow(
            int movementWindowDays,
            long nowMillis)
    {
        ZoneId zoneId =
                ZoneId.systemDefault();


        LocalDate today =
                Instant
                        .ofEpochMilli(
                                nowMillis
                        )
                        .atZone(
                                zoneId
                        )
                        .toLocalDate();


        LocalDate firstDay =
                today.minusDays(
                        movementWindowDays
                                -
                                1L
                );


        long startInclusive =
                firstDay
                        .atStartOfDay(
                                zoneId
                        )
                        .toInstant()
                        .toEpochMilli();


        long endExclusive =
                today
                        .plusDays(
                                1L
                        )
                        .atStartOfDay(
                                zoneId
                        )
                        .toInstant()
                        .toEpochMilli();


        return new MovementWindow(
                startInclusive,
                endExclusive
        );
    }


    private final class ReportCoordinator
    {
        private final int
                movementWindowDays;


        private final long
                nowMillis;


        private final long
                movementStartInclusive;


        private final long
                movementEndExclusive;


        private final MediatorLiveData<AnalyticsReport>
                report =
                new MediatorLiveData<>();


        private final AtomicInteger
                generation =
                new AtomicInteger(
                        0
                );


        private List<MedicineEntity>
                latestMedicines =
                Collections.emptyList();


        private List<BatchEntity>
                latestBatches =
                Collections.emptyList();


        private List<AlertEntity>
                latestAlerts =
                Collections.emptyList();


        private List<InventoryTransactionEntity>
                latestTransactions =
                Collections.emptyList();


        private boolean
                medicinesLoaded =
                false;


        private boolean
                batchesLoaded =
                false;


        private boolean
                alertsLoaded =
                false;


        private boolean
                transactionsLoaded =
                false;


        private ReportCoordinator(
                int movementWindowDays,
                long nowMillis,
                long movementStartInclusive,
                long movementEndExclusive)
        {
            this.movementWindowDays =
                    movementWindowDays;

            this.nowMillis =
                    nowMillis;

            this.movementStartInclusive =
                    movementStartInclusive;

            this.movementEndExclusive =
                    movementEndExclusive;


            LiveData<List<MedicineEntity>>
                    medicineSource =
                    medicineDao
                            .observeAllForAnalytics();


            LiveData<List<BatchEntity>>
                    batchSource =
                    batchDao
                            .observeAllForReorderRecommendations();


            LiveData<List<AlertEntity>>
                    alertSource =
                    alertDao
                            .observeOpenAlerts();


            LiveData<List<InventoryTransactionEntity>>
                    transactionSource =
                    transactionDao
                            .observeConfirmedForAnalytics(
                                    movementStartInclusive,
                                    movementEndExclusive
                            );


            report.addSource(
                    medicineSource,
                    medicines ->
                    {
                        latestMedicines =
                                medicines == null
                                        ? Collections
                                        .emptyList()
                                        : medicines;


                        medicinesLoaded =
                                true;


                        requestRebuild();
                    }
            );


            report.addSource(
                    batchSource,
                    batches ->
                    {
                        latestBatches =
                                batches == null
                                        ? Collections
                                        .emptyList()
                                        : batches;


                        batchesLoaded =
                                true;


                        requestRebuild();
                    }
            );


            report.addSource(
                    alertSource,
                    alerts ->
                    {
                        latestAlerts =
                                alerts == null
                                        ? Collections
                                        .emptyList()
                                        : alerts;


                        alertsLoaded =
                                true;


                        requestRebuild();
                    }
            );


            report.addSource(
                    transactionSource,
                    transactions ->
                    {
                        latestTransactions =
                                transactions == null
                                        ? Collections
                                        .emptyList()
                                        : transactions;


                        transactionsLoaded =
                                true;


                        requestRebuild();
                    }
            );
        }


        private LiveData<AnalyticsReport>
        getReport()
        {
            return report;
        }


        private void requestRebuild()
        {
            if (!medicinesLoaded
                    ||
                    !batchesLoaded
                    ||
                    !alertsLoaded
                    ||
                    !transactionsLoaded)
            {
                return;
            }


            int currentGeneration =
                    generation
                            .incrementAndGet();


            List<MedicineEntity>
                    medicineSnapshot =
                    new ArrayList<>(
                            latestMedicines
                    );


            List<BatchEntity>
                    batchSnapshot =
                    new ArrayList<>(
                            latestBatches
                    );


            List<AlertEntity>
                    alertSnapshot =
                    new ArrayList<>(
                            latestAlerts
                    );


            List<InventoryTransactionEntity>
                    transactionSnapshot =
                    new ArrayList<>(
                            latestTransactions
                    );


            appExecutors
                    .databaseIO()
                    .execute(
                            () ->
                            {
                                try
                                {
                                    AnalyticsReport result =
                                            analyticsReportService
                                                    .buildReport(
                                                            medicineSnapshot,
                                                            batchSnapshot,
                                                            alertSnapshot,
                                                            transactionSnapshot,
                                                            movementWindowDays,
                                                            nowMillis,
                                                            movementStartInclusive,
                                                            movementEndExclusive
                                                    );


                                    if (currentGeneration
                                            !=
                                            generation.get())
                                    {
                                        return;
                                    }


                                    error.postValue(
                                            null
                                    );


                                    report.postValue(
                                            result
                                    );
                                }
                                catch (Exception exception)
                                {
                                    if (currentGeneration
                                            !=
                                            generation.get())
                                    {
                                        return;
                                    }


                                    error.postValue(
                                            "Unable to generate analytics report."
                                    );
                                }
                            }
                    );
        }
    }


    private static final class MovementWindow
    {
        private final long
                startInclusive;


        private final long
                endExclusive;


        private MovementWindow(
                long startInclusive,
                long endExclusive)
        {
            this.startInclusive =
                    startInclusive;

            this.endExclusive =
                    endExclusive;
        }
    }
}