package com.medicationinventory.data;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.remote.firestore.FirestoreInventoryOperationDataSource;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.util.AppExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;




/**
 * Checks owner and permission rules for manual retry, including that a failed conditional queue update is not reported as successful.
 */
public class InventorySyncManualRetryTest
{
    private ExecutorService databaseExecutor;
    private SyncQueueDao queue;
    private AuthenticationRepository authentication;
    private InventorySyncRepository repository;

    @Before
    public void setUp() {
        databaseExecutor = Executors.newSingleThreadExecutor();
        queue = mock(SyncQueueDao.class);
        authentication = mock(AuthenticationRepository.class);
        repository = new InventorySyncRepository(
                mock(InventoryOperationDao.class),
                mock(AllocationDao.class),
                mock(BatchDao.class),
                mock(TransactionDao.class),
                queue,
                mock(FirestoreInventoryOperationDataSource.class),
                authentication,
                new AppExecutors(databaseExecutor, Runnable::run));
    }

    @After

    public void tearDown() {
        databaseExecutor.shutdownNow();
    }

    @Test

    public void heldPermissionDeniedOwnerCanRequestRetry() throws Exception {
        when(authentication.getCurrentUserId()).thenReturn("user-a");
        when(queue.retryHeldPermissionDeniedUpload("op-a", "user-a"))
                .thenReturn(1);

        Result result = request("op-a", "user-a");
        assertTrue(result.success.get());
        assertNull(result.error.get());
        verify(queue, times(1))
                .retryHeldPermissionDeniedUpload("op-a", "user-a");
        verify(queue, never()).enqueue(any());
    }

    @Test

    public void wrongSignedInAccountCannotUnblockOperation() throws Exception {
        when(authentication.getCurrentUserId()).thenReturn("user-b");
        Result result = request("op-a", "user-a");
        assertFalse(result.success.get());
        assertTrue(result.error.get().contains("account changed"));
        verify(queue, never()).retryHeldPermissionDeniedUpload(anyString(), anyString());
    }

    @Test

    public void failedConditionalUpdateDoesNotClaimRetrySucceeded() throws Exception {
        when(authentication.getCurrentUserId()).thenReturn("user-a");
        when(queue.retryHeldPermissionDeniedUpload("terminal-op", "user-a"))
                .thenReturn(0); // terminal, not held, wrong error, or already resumed
        Result result = request("terminal-op", "user-a");
        assertFalse(result.success.get());
        assertTrue(result.error.get().contains("not an eligible held upload"));
    }


    private Result request(String operationId, String ownerId) throws Exception {
        Result result = new Result();
        CountDownLatch latch = new CountDownLatch(1);
        repository.retryHeldPermissionDeniedUpload(operationId, ownerId,
                new InventorySyncRepository.ManualRetryCallback() {
                    @Override public void onReady() {
                        result.success.set(true);
                        latch.countDown();
                    }
                    @Override public void onError(String message) {
                        result.error.set(message);
                        latch.countDown();
                    }
                });
        assertTrue("Timed out waiting for retry callback",
                latch.await(5, TimeUnit.SECONDS));
        return result;
    }

    private static class Result {
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicReference<String> error = new AtomicReference<>();
    }
}
