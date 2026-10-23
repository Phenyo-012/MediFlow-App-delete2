package com.medicationinventory.ui.movement;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.MovementBatchOption;
import com.medicationinventory.data.local.model.MovementHistoryRecord;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.enums.Permission;

import java.util.List;
import java.util.ArrayList;

/**
 * Builds a permission-aware movement history for one medicine, with selectable batch and
 * transaction-type filters, an inclusive date range, and incremental local paging. It switches
 * Room sources when filter state changes and exposes whether history can be viewed separately from
 * the rows, allowing the activity to avoid presenting a denied query as an empty ledger.
 */
public class MovementHistoryViewModel
        extends ViewModel
{
    private static final int PAGE_SIZE =
            50;

    private int visibleLimit =
            PAGE_SIZE;

    private final MutableLiveData<Boolean>
            canLoadMore =
            new MutableLiveData<>(
                    false
            );

    private final String medicineId;

    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MovementHistoryRepository
            movementHistoryRepository;


    private final MutableLiveData<Boolean>
            canViewHistory =
            new MutableLiveData<>();


    private final MediatorLiveData<List<MovementHistoryRecord>>
            history =
            new MediatorLiveData<>();


    private final MediatorLiveData<List<MovementBatchOption>>
            batchOptions =
            new MediatorLiveData<>();


    private LiveData<List<MovementHistoryRecord>>
            currentHistorySource;


    private LiveData<List<MovementBatchOption>>
            currentBatchOptionsSource;


    private String transactionTypeFilter;

    private String batchIdFilter;

    private long startInclusive;

    private long endExclusive;

    private boolean historyStarted;


    public MovementHistoryViewModel(
            String medicineId,
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MovementHistoryRepository movementHistoryRepository)
    {
        this.medicineId =
                medicineId;

        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.movementHistoryRepository =
                movementHistoryRepository;
    }


    public LiveData<Boolean> getCanViewHistory()
    {
        return canViewHistory;
    }


    public LiveData<List<MovementHistoryRecord>>
    getHistory()
    {
        return history;
    }

    public LiveData<Boolean> getCanLoadMore()
    {
        return canLoadMore;
    }

    public LiveData<List<MovementBatchOption>>
    getBatchOptions()
    {
        return batchOptions;
    }


    public void checkAccess()
    {
        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                || uid.trim().isEmpty())
        {
            canViewHistory.postValue(
                    false
            );

            return;
        }


        userRepository.getUser(
                uid,
                userProfile ->
                {
                    boolean allowed =
                            userProfile != null
                                    && userProfile.isActive()
                                    && userProfile.can(
                                    Permission.VIEW_INVENTORY_HISTORY
                            );


                    canViewHistory.postValue(
                            allowed
                    );
                }
        );
    }


    public void startHistory()
    {
        if (historyStarted)
        {
            return;
        }


        historyStarted =
                true;


        currentBatchOptionsSource =
                movementHistoryRepository
                        .observeBatchOptions(
                                medicineId
                        );


        batchOptions.addSource(
                currentBatchOptionsSource,
                batchOptions::setValue
        );


        refreshHistorySource();
    }


    public void setTransactionTypeFilter(
            String transactionType)
    {
        if (sameValue(
                transactionTypeFilter,
                transactionType
        ))
        {
            return;
        }


        transactionTypeFilter =
                transactionType;


        resetPagingAndRefresh();;
    }


    public void setBatchFilter(
            String batchId)
    {
        if (sameValue(
                batchIdFilter,
                batchId
        ))
        {
            return;
        }


        batchIdFilter =
                batchId;


        resetPagingAndRefresh();;
    }


    public void setDateRange(
            long startInclusive,
            long endExclusive)
    {
        if (startInclusive < 0
                || endExclusive < 0)
        {
            throw new IllegalArgumentException(
                    "History timestamps cannot be negative."
            );
        }


        if (startInclusive > 0
                && endExclusive > 0
                && startInclusive >= endExclusive)
        {
            throw new IllegalArgumentException(
                    "History start must be before history end."
            );
        }


        this.startInclusive =
                startInclusive;

        this.endExclusive =
                endExclusive;


        resetPagingAndRefresh();;
    }


    public void clearFilters()
    {
        transactionTypeFilter =
                null;

        batchIdFilter =
                null;

        startInclusive =
                0L;

        endExclusive =
                0L;


        resetPagingAndRefresh();;
    }


    private void resetPagingAndRefresh()
    {
        visibleLimit =
                PAGE_SIZE;


        if (historyStarted)
        {
            refreshHistorySource();
        }
    }



    private void refreshHistorySource()
    {
        if (currentHistorySource != null)
        {
            history.removeSource(
                    currentHistorySource
            );
        }



        currentHistorySource =
                movementHistoryRepository
                        .observeForMedicine(
                                medicineId,
                                transactionTypeFilter,
                                batchIdFilter,
                                startInclusive,
                                endExclusive,
                                visibleLimit + 1
                        );


        history.addSource(
                currentHistorySource,
                records ->
                {
                    if (records == null)
                    {
                        canLoadMore.setValue(
                                false
                        );


                        history.setValue(
                                new ArrayList<>()
                        );


                        return;
                    }


                    boolean moreAvailable =
                            records.size()
                                    >
                                    visibleLimit;


                    canLoadMore.setValue(
                            moreAvailable
                    );


                    int visibleCount =
                            Math.min(
                                    records.size(),
                                    visibleLimit
                            );


                    history.setValue(
                            new ArrayList<>(
                                    records.subList(
                                            0,
                                            visibleCount
                                    )
                            )
                    );
                }
        );
    }


    public void loadMore()
    {
        if (!historyStarted)
        {
            return;
        }


        Boolean moreAvailable =
                canLoadMore.getValue();


        if (!Boolean.TRUE.equals(
                moreAvailable
        ))
        {
            return;
        }


        visibleLimit +=
                PAGE_SIZE;


        refreshHistorySource();
    }


    private boolean sameValue(
            String first,
            String second)
    {
        if (first == null)
        {
            return second == null;
        }


        return first.equals(
                second
        );
    }
}