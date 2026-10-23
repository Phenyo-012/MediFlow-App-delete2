package com.medicationinventory.data;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


import com.medicationinventory.data.local.dao.InventorySettingsDao;
import com.medicationinventory.data.local.entity.InventorySettingsEntity;

import com.medicationinventory.data.remote.backend
        .TrustedInventorySettingsDataSource;

import com.medicationinventory.data.remote.firestore
        .FirestoreInventorySettingsDataSource;

import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.model.domain.InventorySettings;

import com.medicationinventory.util.AppExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;



/**
 * Covers trusted settings mutation outcomes, including committed refresh errors, rejected writes, and refresh after an idempotent update.
 */
public class InventorySettingsRepositoryTest
{
    private InventorySettingsDao dao;


    private FirestoreInventorySettingsDataSource
            firestoreDataSource;


    private TrustedInventorySettingsDataSource
            trustedDataSource;


    private DirectExecutorService
            databaseExecutor;



    private InventorySettingsRepository
            repository;


    @Before
    public void setUp()
    {
        dao =
                mock(
                        InventorySettingsDao.class
                );


        firestoreDataSource =
                mock(
                        FirestoreInventorySettingsDataSource.class
                );


        trustedDataSource =
                mock(
                        TrustedInventorySettingsDataSource.class
                );


        databaseExecutor =
                new DirectExecutorService();


        Executor directMainThread =
                Runnable::run;


        AppExecutors appExecutors =
                new AppExecutors(
                        databaseExecutor,
                        directMainThread
                );

        repository =
                new InventorySettingsRepository(
                        dao,
                        firestoreDataSource,
                        trustedDataSource,
                        appExecutors
                );
    }


    @After

    public void tearDown()
    {
        databaseExecutor.shutdown();
    }


    @Test
    public void setInventorySettings_mutationAndRefreshSuccess_returnsSuccess()
    {
        TrustedInventorySettingsDataSource
                .MutationResult mutationResult =
                new TrustedInventorySettingsDataSource
                        .MutationResult(
                        true,
                        false,
                        "inventory",
                        30
                );


        InventorySettings authoritative =
                new InventorySettings(
                        "inventory",
                        30,
                        123456789L,
                        "user-1"
                );


        doAnswer(
                invocation ->
                {
                    TrustedInventorySettingsDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onSuccess(
                            mutationResult
                    );


                    return null;
                }
        )
                .when(
                        trustedDataSource
                )
                .setInventorySettings(
                        eq(30),
                        any(
                                TrustedInventorySettingsDataSource
                                        .MutationCallback.class
                        )
                );


        doAnswer(
                invocation ->
                {
                    FirestoreInventorySettingsDataSource
                            .SettingsCallback callback =
                            invocation.getArgument(
                                    0
                            );


                    callback.onSuccess(
                            authoritative
                    );


                    return null;
                }
        )
                .when(
                        firestoreDataSource
                )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setInventorySettings(
                30,
                callback
        );


        assertTrue(
                callback.successCalled
        );


        assertFalse(
                callback.committedRefreshErrorCalled
        );


        assertFalse(
                callback.errorCalled
        );


        assertTrue(
                callback.changed
        );


        assertFalse(
                callback.created
        );


        assertSame(
                authoritative,
                callback.settings
        );


        verify(
                firestoreDataSource
        )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        verify(
                dao
        )
                .upsert(
                        any(
                                InventorySettingsEntity.class
                        )
                );
    }


    @Test
    public void setInventorySettings_mutationCommittedButRefreshFails_returnsCommittedRefreshError()
    {
        TrustedInventorySettingsDataSource
                .MutationResult mutationResult =
                new TrustedInventorySettingsDataSource
                        .MutationResult(
                        true,
                        false,
                        "inventory",
                        45
                );


        Exception refreshFailure =
                new Exception(
                        "Server refresh unavailable."
                );


        doAnswer(
                invocation ->
                {
                    TrustedInventorySettingsDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onSuccess(
                            mutationResult
                    );


                    return null;
                }
        )
                .when(
                        trustedDataSource
                )
                .setInventorySettings(
                        eq(45),
                        any(
                                TrustedInventorySettingsDataSource
                                        .MutationCallback.class
                        )
                );


        doAnswer(
                invocation ->
                {
                    FirestoreInventorySettingsDataSource
                            .SettingsCallback callback =
                            invocation.getArgument(
                                    0
                            );


                    callback.onError(
                            refreshFailure
                    );


                    return null;
                }
        )
                .when(
                        firestoreDataSource
                )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setInventorySettings(
                45,
                callback
        );


        assertFalse(
                callback.successCalled
        );


        assertTrue(
                callback.committedRefreshErrorCalled
        );


        assertFalse(
                callback.errorCalled
        );


        assertTrue(
                callback.changed
        );


        assertFalse(
                callback.created
        );


        assertSame(
                refreshFailure,
                callback.exception
        );



        verify(
                dao,
                never()
        )
                .upsert(
                        any(
                                InventorySettingsEntity.class
                        )
                );
    }


    @Test
    public void setInventorySettings_mutationRejected_returnsErrorAndDoesNotRefresh()
    {
        TrustedInventorySettingsDataSource
                .SettingsRemoteException mutationFailure =
                new TrustedInventorySettingsDataSource
                        .SettingsRemoteException(
                        403,
                        "PERMISSION_DENIED",
                        "You do not have permission."
                );


        doAnswer(
                invocation ->
                {
                    TrustedInventorySettingsDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onError(
                            mutationFailure
                    );


                    return null;
                }
        )
                .when(
                        trustedDataSource
                )
                .setInventorySettings(
                        eq(30),
                        any(
                                TrustedInventorySettingsDataSource
                                        .MutationCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setInventorySettings(
                30,
                callback
        );


        assertFalse(
                callback.successCalled
        );


        assertFalse(
                callback.committedRefreshErrorCalled
        );


        assertTrue(
                callback.errorCalled
        );


        assertSame(
                mutationFailure,
                callback.exception
        );


        verify(
                firestoreDataSource,
                never()
        )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        verify(
                dao,
                never()
        )
                .upsert(
                        any(
                                InventorySettingsEntity.class
                        )
                );
    }


    @Test
    public void setInventorySettings_idempotentMutationStillRefreshesAuthoritativeSettings()
    {

        TrustedInventorySettingsDataSource
                .MutationResult mutationResult =
                new TrustedInventorySettingsDataSource
                        .MutationResult(
                        false,
                        false,
                        "inventory",
                        90
                );


        InventorySettings authoritative =
                new InventorySettings(
                        "inventory",
                        90,
                        987654321L,
                        "previous-admin"
                );


        doAnswer(
                invocation ->
                {
                    TrustedInventorySettingsDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onSuccess(
                            mutationResult
                    );


                    return null;
                }
        )
                .when(
                        trustedDataSource
                )
                .setInventorySettings(
                        eq(90),
                        any(
                                TrustedInventorySettingsDataSource
                                        .MutationCallback.class
                        )
                );


        doAnswer(
                invocation ->
                {
                    FirestoreInventorySettingsDataSource
                            .SettingsCallback callback =
                            invocation.getArgument(
                                    0
                            );


                    callback.onSuccess(
                            authoritative
                    );


                    return null;
                }
        )
                .when(
                        firestoreDataSource
                )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setInventorySettings(
                90,
                callback
        );


        assertTrue(
                callback.successCalled
        );


        assertFalse(
                callback.committedRefreshErrorCalled
        );


        assertFalse(
                callback.errorCalled
        );


        assertFalse(
                callback.changed
        );


        assertFalse(
                callback.created
        );


        assertSame(
                authoritative,
                callback.settings
        );


        assertEquals(
                90,
                callback.settings
                        .getNearExpiryWarningDays()
        );


        assertEquals(
                987654321L,
                callback.settings
                        .getUpdatedAt()
        );


        assertEquals(
                "previous-admin",
                callback.settings
                        .getUpdatedBy()
        );



        verify(
                firestoreDataSource
        )
                .getInventorySettings(
                        any(
                                FirestoreInventorySettingsDataSource
                                        .SettingsCallback.class
                        )
                );


        verify(
                dao
        )
                .upsert(
                        any(
                                InventorySettingsEntity.class
                        )
                );
    }




    private static class TestTrustedMutationCallback
            implements InventorySettingsRepository
            .TrustedMutationCallback
    {
        boolean successCalled;

        boolean committedRefreshErrorCalled;

        boolean errorCalled;

        boolean changed;

        boolean created;

        InventorySettings settings;

        Exception exception;


        @Override

        public void onSuccess(
                boolean changed,
                boolean created,
                InventorySettings settings)
        {
            this.successCalled =
                    true;


            this.changed =
                    changed;


            this.created =
                    created;


            this.settings =
                    settings;
        }


        @Override

        public void onCommittedRefreshError(
                boolean changed,
                boolean created,
                Exception exception)
        {
            this.committedRefreshErrorCalled =
                    true;


            this.changed =
                    changed;


            this.created =
                    created;


            this.exception =
                    exception;
        }


        @Override

        public void onError(
                Exception exception)
        {
            this.errorCalled =
                    true;


            this.exception =
                    exception;
        }
    }




    private static class DirectExecutorService
            extends AbstractExecutorService
    {
        private boolean shutdown;


        @Override

        public void shutdown()
        {
            shutdown =
                    true;
        }


        @Override

        public List<Runnable> shutdownNow()
        {
            shutdown =
                    true;


            return Collections.emptyList();
        }


        @Override
        public boolean isShutdown()
        {
            return shutdown;
        }


        @Override
        public boolean isTerminated()
        {
            return shutdown;
        }


        @Override

        public boolean awaitTermination(
                long timeout,
                TimeUnit unit)
        {
            return true;
        }


        @Override

        public void execute(
                Runnable command)
        {
            if (shutdown)
            {
                throw new IllegalStateException(
                        "Executor is shut down."
                );
            }


            command.run();
        }
    }
}