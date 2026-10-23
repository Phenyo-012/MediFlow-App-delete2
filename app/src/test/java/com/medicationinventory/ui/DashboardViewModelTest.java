package com.medicationinventory.ui.dashboard;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;


import com.medicationinventory.data.local.model.MovementHistoryRecord;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.MovementHistoryRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.SyncMetadataRepository;
import com.medicationinventory.data.repository.SyncStatusRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReorderRecommendationListItem;
import com.medicationinventory.model.domain.UserProfile;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import java.util.concurrent.atomic.AtomicReference;




/**
 * Verifies dashboard streams and low-stock counts, including null recommendations and avoiding user-scoped sync observation for a blank UID.
 */
public class DashboardViewModelTest
{
    private static final String USER_ID =
            "user-1";


    /*
     * MOCKED DEPENDENCIES
     */

    private AuthenticationRepository
            authenticationRepository;


    private UserRepository
            userRepository;


    private MedicineRepository
            medicineRepository;


    private ReorderRecommendationsRepository
            reorderRecommendationsRepository;


    private AlertRepository
            alertRepository;


    private SyncStatusRepository
            syncStatusRepository;


    private SyncMetadataRepository
            syncMetadataRepository;


    private MovementHistoryRepository
            movementHistoryRepository;



    // LIVE DATA SOURCES


    private MutableLiveData<UserProfile>
            userProfileLiveData;


    private MutableLiveData<Integer>
            totalMedicinesLiveData;


    private MutableLiveData<
            List<ReorderRecommendationListItem>>
            recommendationsLiveData;


    private MutableLiveData<Integer>
            nearExpiryLiveData;


    private MutableLiveData<Integer>
            activeAlertsLiveData;


    private MutableLiveData<Integer>
            expiredAlertsLiveData;


    private MutableLiveData<Integer>
            pendingSyncLiveData;


    private MutableLiveData<Integer>
            attentionLiveData;


    private MutableLiveData<Long>
            lastSyncLiveData;


    private MutableLiveData<
            List<MovementHistoryRecord>>
            movementsLiveData;



     // SETUP


    @Before
    public void setUp()
    {
        /*
         * Run Architecture Components synchronously.
         *
         * This keeps LiveData and Transformations.map()
         * deterministic in local JVM tests.
         */
        ArchTaskExecutor
                .getInstance()
                .setDelegate(
                        new TaskExecutor()
                        {
                            @Override

                            public void executeOnDiskIO(
                                    Runnable runnable)
                            {
                                runnable.run();
                            }


                            @Override

                            public void postToMainThread(
                                    Runnable runnable)
                            {
                                runnable.run();
                            }


                            @Override
                            public boolean isMainThread()
                            {
                                return true;
                            }
                        }
                );




        authenticationRepository =
                mock(
                        AuthenticationRepository.class
                );


        userRepository =
                mock(
                        UserRepository.class
                );


        medicineRepository =
                mock(
                        MedicineRepository.class
                );


        reorderRecommendationsRepository =
                mock(
                        ReorderRecommendationsRepository.class
                );


        alertRepository =
                mock(
                        AlertRepository.class
                );


        syncStatusRepository =
                mock(
                        SyncStatusRepository.class
                );


        syncMetadataRepository =
                mock(
                        SyncMetadataRepository.class
                );


        movementHistoryRepository =
                mock(
                        MovementHistoryRepository.class
                );



        userProfileLiveData =
                new MutableLiveData<>(
                        mock(
                                UserProfile.class
                        )
                );


        totalMedicinesLiveData =
                new MutableLiveData<>(
                        12
                );



        recommendationsLiveData =
                new MutableLiveData<>(
                        Arrays.asList(
                                mock(
                                        ReorderRecommendationListItem.class
                                ),
                                mock(
                                        ReorderRecommendationListItem.class
                                )
                        )
                );


        nearExpiryLiveData =
                new MutableLiveData<>(
                        3
                );


        activeAlertsLiveData =
                new MutableLiveData<>(
                        5
                );


        expiredAlertsLiveData =
                new MutableLiveData<>(
                        1
                );


        pendingSyncLiveData =
                new MutableLiveData<>(
                        2
                );


        attentionLiveData =
                new MutableLiveData<>(
                        1
                );


        lastSyncLiveData =
                new MutableLiveData<>(
                        123456789L
                );


        movementsLiveData =
                new MutableLiveData<>(
                        Collections.singletonList(
                                new MovementHistoryRecord()
                        )
                );



        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        when(
                userRepository
                        .observeUser(
                                USER_ID
                        )
        )
                .thenReturn(
                        userProfileLiveData
                );


        when(
                medicineRepository
                        .observeActiveMedicineCount()
        )
                .thenReturn(
                        totalMedicinesLiveData
                );


        when(
                reorderRecommendationsRepository
                        .observeRecommendations()
        )
                .thenReturn(
                        recommendationsLiveData
                );


        when(
                alertRepository
                        .observeOpenNearExpiryCount()
        )
                .thenReturn(
                        nearExpiryLiveData
                );


        when(
                alertRepository
                        .observeActiveAlertCount()
        )
                .thenReturn(
                        activeAlertsLiveData
                );


        when(
                alertRepository
                        .observeOpenExpiredCount()
        )
                .thenReturn(
                        expiredAlertsLiveData
                );


        when(
                syncStatusRepository
                        .observePendingCount(
                                USER_ID
                        )
        )
                .thenReturn(
                        pendingSyncLiveData
                );


        when(
                syncStatusRepository
                        .observeRequiresAttentionCount(
                                USER_ID
                        )
        )
                .thenReturn(
                        attentionLiveData
                );


        when(
                syncMetadataRepository
                        .observeLastSuccessfulInventorySyncAt()
        )
                .thenReturn(
                        lastSyncLiveData
                );


        when(
                movementHistoryRepository
                        .observeRecentAll(
                                3
                        )
        )
                .thenReturn(
                        movementsLiveData
                );
    }


    @After

    public void tearDown()
    {
        ArchTaskExecutor
                .getInstance()
                .setDelegate(
                        null
                );
    }




    @Test

    public void constructor_exposesExpectedDashboardStreams()
    {
        DashboardViewModel viewModel =
                createViewModel();


        assertSame(
                userProfileLiveData,
                viewModel
                        .getUserProfile()
        );


        assertSame(
                totalMedicinesLiveData,
                viewModel
                        .getTotalMedicines()
        );


        assertSame(
                nearExpiryLiveData,
                viewModel
                        .getNearExpiryCount()
        );


        assertSame(
                activeAlertsLiveData,
                viewModel
                        .getActiveAlertCount()
        );


        assertSame(
                expiredAlertsLiveData,
                viewModel
                        .getExpiredAlertCount()
        );


        assertSame(
                pendingSyncLiveData,
                viewModel
                        .getPendingSyncCount()
        );


        assertSame(
                attentionLiveData,
                viewModel
                        .getSyncAttentionCount()
        );


        assertSame(
                lastSyncLiveData,
                viewModel
                        .getLastSuccessfulSyncAt()
        );


        assertSame(
                movementsLiveData,
                viewModel
                        .getRecentMovements()
        );


        verify(
                syncStatusRepository
        )
                .observePendingCount(
                        USER_ID
                );


        verify(
                syncStatusRepository
        )
                .observeRequiresAttentionCount(
                        USER_ID
                );


        verify(
                movementHistoryRepository
        )
                .observeRecentAll(
                        3
                );
    }



    @Test

    public void lowStockCount_tracksRecommendationListSize()
    {
        DashboardViewModel viewModel =
                createViewModel();


        AtomicReference<Integer> observed =
                new AtomicReference<>();


        Observer<Integer> observer =
                observed::set;


        viewModel
                .getLowStockCount()
                .observeForever(
                        observer
                );


        assertEquals(
                Integer.valueOf(
                        2
                ),
                observed.get()
        );


        recommendationsLiveData
                .setValue(
                        Arrays.asList(
                                mock(
                                        ReorderRecommendationListItem.class
                                ),
                                mock(
                                        ReorderRecommendationListItem.class
                                ),
                                mock(
                                        ReorderRecommendationListItem.class
                                ),
                                mock(
                                        ReorderRecommendationListItem.class
                                )
                        )
                );


        assertEquals(
                Integer.valueOf(
                        4
                ),
                observed.get()
        );



        recommendationsLiveData
                .setValue(
                        Collections.emptyList()
                );


        assertEquals(
                Integer.valueOf(
                        0
                ),
                observed.get()
        );


        viewModel
                .getLowStockCount()
                .removeObserver(
                        observer
                );
    }




    @Test

    public void lowStockCount_nullRecommendationList_returnsZero()
    {
        recommendationsLiveData
                .setValue(
                        null
                );


        DashboardViewModel viewModel =
                createViewModel();


        AtomicReference<Integer> observed =
                new AtomicReference<>();


        Observer<Integer> observer =
                observed::set;


        viewModel
                .getLowStockCount()
                .observeForever(
                        observer
                );


        assertEquals(
                Integer.valueOf(
                        0
                ),
                observed.get()
        );


        viewModel
                .getLowStockCount()
                .removeObserver(
                        observer
                );
    }


    /*
     * TEST:
     * NO VALID AUTHENTICATED UID
     */

    @Test
    public void blankAuthenticatedUid_doesNotObserveUserScopedSyncData()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        "   "
                );


        DashboardViewModel viewModel =
                createViewModel();


        /*
         * No signed-in user profile should be exposed.
         */
        assertNull(
                viewModel
                        .getUserProfile()
                        .getValue()
        );


        /*
         * User-scoped sync values must safely fall
         * back to zero.
         */
        assertEquals(
                Integer.valueOf(
                        0
                ),
                viewModel
                        .getPendingSyncCount()
                        .getValue()
        );


        assertEquals(
                Integer.valueOf(
                        0
                ),
                viewModel
                        .getSyncAttentionCount()
                        .getValue()
        );


        /*
         * No user-specific Room queries should run
         * without a valid UID.
         */
        verify(
                userRepository,
                never()
        )
                .observeUser(
                        anyString()
                );


        verify(
                syncStatusRepository,
                never()
        )
                .observePendingCount(
                        anyString()
                );


        verify(
                syncStatusRepository,
                never()
        )
                .observeRequiresAttentionCount(
                        anyString()
                );
    }


    /*
     * VIEW MODEL FACTORY HELPER
     */


    private DashboardViewModel createViewModel()
    {
        return new DashboardViewModel(
                authenticationRepository,
                userRepository,
                medicineRepository,
                reorderRecommendationsRepository,
                alertRepository,
                syncStatusRepository,
                syncMetadataRepository,
                movementHistoryRepository
        );
    }
}