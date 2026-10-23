package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementBatchOption;

import java.util.List;


/**
 * Queries the local movement history records and related filter options for the movement history screen. It exposes observable data for the UI; cloud import and reconciliation populate the local history separately.
 */
public class MovementHistoryRepository
{
    private static final int DEFAULT_HISTORY_LIMIT =
            100;


    private final TransactionDao
            transactionDao;


    public MovementHistoryRepository(
            TransactionDao transactionDao)
    {
        this.transactionDao =
                transactionDao;
    }


    public LiveData<List<MovementHistoryRecord>>
    observeForMedicine(
            String medicineId,
            String transactionType)
    {
        return observeForMedicine(
                medicineId,
                transactionType,
                null,
                0L,
                0L,
                DEFAULT_HISTORY_LIMIT
        );
    }


    public LiveData<List<MovementHistoryRecord>>
    observeForMedicine(
            String medicineId,
            String transactionType,
            String batchId,
            long startInclusive,
            long endExclusive,
            int limit)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Medicine ID is required."
            );
        }


        String normalizedType =
                normalizeOptional(
                        transactionType
                );


        String normalizedBatchId =
                normalizeOptional(
                        batchId
                );


        int safeLimit =
                limit <= 0
                        ? DEFAULT_HISTORY_LIMIT
                        : limit;


        return transactionDao
                .observeFilteredHistoryForMedicine(
                        medicineId,
                        normalizedType,
                        normalizedBatchId,
                        startInclusive,
                        endExclusive,
                        safeLimit
                );
    }

    public LiveData<List<MovementHistoryRecord>>
    observeRecentAll(
            int limit)
    {
        int safeLimit =
                limit <= 0
                        ? 3
                        : limit;


        return transactionDao
                .observeRecentMovement(
                        safeLimit
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

    public LiveData<List<MovementBatchOption>>
    observeBatchOptions(
            String medicineId)
    {
        if (medicineId == null
                || medicineId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Medicine ID is required."
            );
        }


        return transactionDao
                .observeHistoryBatchOptions(
                        medicineId
                );
    }
}