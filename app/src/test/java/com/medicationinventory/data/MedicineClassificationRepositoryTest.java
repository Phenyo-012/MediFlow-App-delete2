package com.medicationinventory.data;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


import com.medicationinventory.data.local.dao.MedicineClassificationDao;
import com.medicationinventory.data.local.entity.MedicineClassificationEntity;

import com.medicationinventory.data.remote.backend.TrustedMedicineClassificationDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreMedicineClassificationDataSource;

import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.model.domain.MedicineClassification;

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
 * Covers successful classification mutation and refresh, committed changes with refresh failure, and rejected mutations that must not refresh.
 */
public class MedicineClassificationRepositoryTest
{
    private MedicineClassificationDao dao;

    private FirestoreMedicineClassificationDataSource
            firestoreDataSource;

    private TrustedMedicineClassificationDataSource
            trustedDataSource;

    private DirectExecutorService databaseExecutor;

    private MedicineClassificationRepository repository;


    @Before
    public void setUp()
    {
        dao =
                mock(
                        MedicineClassificationDao.class
                );


        firestoreDataSource =
                mock(
                        FirestoreMedicineClassificationDataSource.class
                );


        trustedDataSource =
                mock(
                        TrustedMedicineClassificationDataSource.class
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
                new MedicineClassificationRepository(
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
    public void setClassification_mutationAndRefreshSuccess_returnsSuccess()
    {
        TrustedMedicineClassificationDataSource
                .MutationResult mutationResult =
                new TrustedMedicineClassificationDataSource
                        .MutationResult(
                        true,
                        true,
                        "medicine-1",
                        "A",
                        "V"
                );


        MedicineClassification authoritative =
                new MedicineClassification(
                        "medicine-1",
                        "A",
                        "V",
                        123456789L
                );


        doAnswer(
                invocation ->
                {
                    TrustedMedicineClassificationDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    3
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
                .setClassification(
                        eq("medicine-1"),
                        eq("A"),
                        eq("V"),
                        any(
                                TrustedMedicineClassificationDataSource
                                        .MutationCallback.class
                        )
                );


        doAnswer(
                invocation ->
                {
                    FirestoreMedicineClassificationDataSource
                            .ClassificationCallback callback =
                            invocation.getArgument(
                                    1
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
                .getForMedicine(
                        eq("medicine-1"),
                        any(
                                FirestoreMedicineClassificationDataSource
                                        .ClassificationCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setClassification(
                "medicine-1",
                "A",
                "V",
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


        assertTrue(
                callback.created
        );


        assertSame(
                authoritative,
                callback.classification
        );


        verify(
                dao
        )
                .upsert(
                        any(
                                MedicineClassificationEntity.class
                        )
                );


        verify(
                firestoreDataSource
        )
                .getForMedicine(
                        eq("medicine-1"),
                        any(
                                FirestoreMedicineClassificationDataSource
                                        .ClassificationCallback.class
                        )
                );
    }


    @Test
    public void setClassification_mutationCommittedButRefreshFails_returnsCommittedRefreshError()
    {
        TrustedMedicineClassificationDataSource
                .MutationResult mutationResult =
                new TrustedMedicineClassificationDataSource
                        .MutationResult(
                        true,
                        false,
                        "medicine-1",
                        "B",
                        "E"
                );


        Exception refreshFailure =
                new Exception(
                        "Server refresh unavailable."
                );


        doAnswer(
                invocation ->
                {
                    TrustedMedicineClassificationDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    3
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
                .setClassification(
                        eq("medicine-1"),
                        eq("B"),
                        eq("E"),
                        any(
                                TrustedMedicineClassificationDataSource
                                        .MutationCallback.class
                        )
                );


        doAnswer(
                invocation ->
                {
                    FirestoreMedicineClassificationDataSource
                            .ClassificationCallback callback =
                            invocation.getArgument(
                                    1
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
                .getForMedicine(
                        eq("medicine-1"),
                        any(
                                FirestoreMedicineClassificationDataSource
                                        .ClassificationCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setClassification(
                "medicine-1",
                "B",
                "E",
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
                                MedicineClassificationEntity.class
                        )
                );
    }


    @Test
    public void setClassification_mutationRejected_returnsErrorAndDoesNotRefresh()
    {
        TrustedMedicineClassificationDataSource
                .ClassificationRemoteException mutationFailure =
                new TrustedMedicineClassificationDataSource
                        .ClassificationRemoteException(
                        403,
                        "PERMISSION_DENIED",
                        "You do not have permission."
                );


        doAnswer(
                invocation ->
                {
                    TrustedMedicineClassificationDataSource
                            .MutationCallback callback =
                            invocation.getArgument(
                                    3
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
                .setClassification(
                        eq("medicine-1"),
                        eq("A"),
                        eq("D"),
                        any(
                                TrustedMedicineClassificationDataSource
                                        .MutationCallback.class
                        )
                );


        TestTrustedMutationCallback callback =
                new TestTrustedMutationCallback();


        repository.setClassification(
                "medicine-1",
                "A",
                "D",
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
                .getForMedicine(
                        any(),
                        any(
                                FirestoreMedicineClassificationDataSource
                                        .ClassificationCallback.class
                        )
                );


        verify(
                dao,
                never()
        )
                .upsert(
                        any(
                                MedicineClassificationEntity.class
                        )
                );
    }



    private static class TestTrustedMutationCallback
            implements MedicineClassificationRepository
            .TrustedMutationCallback
    {
        boolean successCalled;

        boolean committedRefreshErrorCalled;

        boolean errorCalled;

        boolean changed;

        boolean created;

        MedicineClassification classification;

        Exception exception;


        @Override
        public void onSuccess(
                boolean changed,
                boolean created,
                MedicineClassification classification)
        {
            this.successCalled =
                    true;


            this.changed =
                    changed;


            this.created =
                    created;


            this.classification =
                    classification;
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