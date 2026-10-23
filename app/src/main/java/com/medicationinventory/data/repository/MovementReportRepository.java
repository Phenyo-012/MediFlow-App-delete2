package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.TransactionDao;

import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementReportBatchOption;
import com.medicationinventory.data.local.model.MovementReportMedicineOption;

import com.medicationinventory.model.domain.ReportDateRange;

import java.util.List;



/**
 * Builds movement report rows and medicine or batch filter choices from local transaction history. Report queries use the selected date range and filters, while synchronization is responsible for importing confirmed cloud history into the local store.
 */
public class MovementReportRepository
{
    public static final int DEFAULT_PAGE_SIZE =
            50;


    private final TransactionDao
            transactionDao;


    public MovementReportRepository(
            TransactionDao transactionDao)
    {
        if (transactionDao == null)
        {
            throw new IllegalArgumentException(
                    "Transaction DAO is required."
            );
        }


        this.transactionDao =
                transactionDao;
    }


    public LiveData<List<MovementHistoryRecord>>
    observeReport(
            ReportDateRange dateRange,
            String medicineId,
            String batchId,
            String transactionType,
            int limit)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        int safeLimit =
                limit <= 0
                        ? DEFAULT_PAGE_SIZE
                        : limit;


        return transactionDao
                .observeMovementReport(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive(),
                        normalizeOptional(
                                medicineId
                        ),
                        normalizeOptional(
                                batchId
                        ),
                        normalizeOptional(
                                transactionType
                        ),
                        safeLimit
                );
    }


    public LiveData<List<MovementReportMedicineOption>>
    observeMedicineOptions(
            ReportDateRange dateRange)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        return transactionDao
                .observeMovementReportMedicineOptions(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive()
                );
    }


    public LiveData<List<MovementReportBatchOption>>
    observeBatchOptions(
            ReportDateRange dateRange,
            String medicineId)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        return transactionDao
                .observeMovementReportBatchOptions(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive(),
                        normalizeOptional(
                                medicineId
                        )
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