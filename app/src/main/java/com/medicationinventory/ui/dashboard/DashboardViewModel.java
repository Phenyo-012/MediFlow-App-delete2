package com.medicationinventory.ui.dashboard;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.model.MovementHistoryRecord;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;

import java.util.List;




/**
 * Combines the current profile with inventory summaries for the dashboard: medicine totals,
 * low-stock and expiry alerts, pending sync work, and recent movements. The values are exposed as
 * LiveData so the activity can render Room-backed changes without owning repository logic; report
 * services provide the derived calculations while account/profile changes update the greeting and
 * available actions.
 */
public class DashboardViewModel
        extends ViewModel
{
    private static final int
            RECENT_MOVEMENT_LIMIT =
            3;


    private final LiveData<UserProfile>
            userProfile;


    private final LiveData<Integer>
            totalMedicines;


    private final LiveData<Integer>
            lowStockCount;


    private final LiveData<Integer>
            nearExpiryCount;


    private final LiveData<Integer>
            activeAlertCount;


    private final LiveData<Integer>
            expiredAlertCount;


    private final LiveData<Integer>
            pendingSyncCount;


    private final LiveData<Integer>
            syncAttentionCount;


    private final LiveData<Long>
            lastSuccessfulSyncAt;


    private final LiveData<List<MovementHistoryRecord>>
            recentMovements;



    public DashboardViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            ReorderRecommendationsRepository
                    reorderRecommendationsRepository,
            AlertRepository alertRepository,
            SyncStatusRepository syncStatusRepository,
            SyncMetadataRepository syncMetadataRepository,
            MovementHistoryRepository movementHistoryRepository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                medicineRepository == null
                ||
                reorderRecommendationsRepository == null
                ||
                alertRepository == null
                ||
                syncStatusRepository == null
                ||
                syncMetadataRepository == null
                ||
                movementHistoryRepository == null)
        {
            throw new IllegalArgumentException(
                    "Dashboard dependencies are required."
            );
        }


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


            pendingSyncCount =
                    new MutableLiveData<>(
                            0
                    );


            syncAttentionCount =
                    new MutableLiveData<>(
                            0
                    );
        }
        else
        {
            String safeUid =
                    uid.trim();


            userProfile =
                    userRepository
                            .observeUser(
                                    safeUid
                            );


            pendingSyncCount =
                    syncStatusRepository
                            .observePendingCount(
                                    safeUid
                            );


            syncAttentionCount =
                    syncStatusRepository
                            .observeRequiresAttentionCount(
                                    safeUid
                            );
        }


        totalMedicines =
                medicineRepository
                        .observeActiveMedicineCount();


        lowStockCount =
                Transformations.map(
                        reorderRecommendationsRepository
                                .observeRecommendations(),
                        recommendations ->
                                recommendations == null
                                        ? 0
                                        : recommendations.size()
                );


        nearExpiryCount =
                alertRepository
                        .observeOpenNearExpiryCount();


        activeAlertCount =
                alertRepository
                        .observeActiveAlertCount();


        expiredAlertCount =
                alertRepository
                        .observeOpenExpiredCount();


        lastSuccessfulSyncAt =
                syncMetadataRepository
                        .observeLastSuccessfulInventorySyncAt();


        recentMovements =
                movementHistoryRepository
                        .observeRecentAll(
                                RECENT_MOVEMENT_LIMIT
                        );
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<Integer>
    getTotalMedicines()
    {
        return totalMedicines;
    }


    public LiveData<Integer>
    getLowStockCount()
    {
        return lowStockCount;
    }


    public LiveData<Integer>
    getNearExpiryCount()
    {
        return nearExpiryCount;
    }


    public LiveData<Integer>
    getActiveAlertCount()
    {
        return activeAlertCount;
    }


    public LiveData<Integer>
    getExpiredAlertCount()
    {
        return expiredAlertCount;
    }


    public LiveData<Integer>
    getPendingSyncCount()
    {
        return pendingSyncCount;
    }


    public LiveData<Integer>
    getSyncAttentionCount()
    {
        return syncAttentionCount;
    }


    public LiveData<Long>
    getLastSuccessfulSyncAt()
    {
        return lastSuccessfulSyncAt;
    }


    public LiveData<List<MovementHistoryRecord>>
    getRecentMovements()
    {
        return recentMovements;
    }
}