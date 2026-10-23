package com.medicationinventory.ui.reports.stock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.StockLevelReportRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.StockLevelReportItem;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.StockLevelStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combines the signed-in profile with local stock-level report rows and applies the report's
 * all/reorder-only filter. The repository and shared recommendation service own stock and reorder
 * calculations; this ViewModel only keeps the displayed subset synchronized with the latest rows
 * and exposes repository errors to the activity.
 */
public class StockLevelReportViewModel
        extends ViewModel
{
    public enum Filter
    {
        ALL,
        REORDER_ONLY
    }


    private final StockLevelReportRepository
            repository;


    private final LiveData<UserProfile>
            userProfile;


    private final MutableLiveData<Filter>
            selectedFilter =
            new MutableLiveData<>(
                    Filter.ALL
            );


    private final MediatorLiveData<
            List<StockLevelReportItem>>
            visibleItems =
            new MediatorLiveData<>();


    private final LiveData<String>
            error;


    private List<StockLevelReportItem>
            latestItems =
            Collections.emptyList();


    private Filter latestFilter =
            Filter.ALL;


    public StockLevelReportViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            StockLevelReportRepository repository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                repository == null)
        {
            throw new IllegalArgumentException(
                    "Stock-level report dependencies are required."
            );
        }


        this.repository =
                repository;


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


        LiveData<List<StockLevelReportItem>>
                sourceItems =
                repository
                        .observeReportItems();


        error =
                repository
                        .observeError();


        visibleItems.addSource(
                sourceItems,
                items ->
                {
                    latestItems =
                            items == null
                                    ? Collections.emptyList()
                                    : items;


                    publishFilteredItems();
                }
        );


        visibleItems.addSource(
                selectedFilter,
                filter ->
                {
                    latestFilter =
                            filter == null
                                    ? Filter.ALL
                                    : filter;


                    publishFilteredItems();
                }
        );
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<List<StockLevelReportItem>>
    getVisibleItems()
    {
        return visibleItems;
    }


    public LiveData<String>
    getError()
    {
        return error;
    }


    public LiveData<Filter>
    getSelectedFilter()
    {
        return selectedFilter;
    }


    public void setFilter(
            Filter filter)
    {
        selectedFilter.setValue(
                filter == null
                        ? Filter.ALL
                        : filter
        );
    }


    public void retry()
    {
        repository.refresh();
    }


    private void publishFilteredItems()
    {
        if (latestFilter == Filter.ALL)
        {
            visibleItems.setValue(
                    new ArrayList<>(
                            latestItems
                    )
            );

            return;
        }


        List<StockLevelReportItem> result =
                new ArrayList<>();


        for (StockLevelReportItem item :
                latestItems)
        {
            if (item == null)
            {
                continue;
            }


            if (item.getStockStatus()
                    != StockLevelStatus.NORMAL)
            {
                result.add(
                        item
                );
            }
        }


        visibleItems.setValue(
                result
        );
    }
}