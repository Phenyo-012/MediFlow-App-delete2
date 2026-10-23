package com.medicationinventory.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicationinventory.data.local.dao.AllocationDao;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.dao.InventoryOperationDao;
import com.medicationinventory.data.local.dao.SyncQueueDao;
import com.medicationinventory.data.local.dao.TransactionDao;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.remote.firestore.FirestoreInventoryOperationDataSource;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.util.AppExecutors;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;



/**
 * Verifies that queued work is restricted to the active account and that signed-out or mid-enumeration account changes fail closed.
 */
public class InventorySyncRepositoryOwnerFilterTest {

    private InventoryOperationDao operations;
    private SyncQueueDao queue;
    private AuthenticationRepository authentication;
    private InventorySyncRepository repository;

    @Before

    public void setup() {
        operations = mock(InventoryOperationDao.class);
        queue = mock(SyncQueueDao.class);
        authentication = mock(AuthenticationRepository.class);
        repository = new InventorySyncRepository(
                operations,
                mock(AllocationDao.class),
                mock(BatchDao.class),
                mock(TransactionDao.class),
                queue,
                mock(FirestoreInventoryOperationDataSource.class),
                authentication,
                mock(AppExecutors.class));
    }


    private void operationOwnedBy(String operationId, String uid) {
        InventoryOperationEntity operation = mock(InventoryOperationEntity.class);
        when(operation.getPerformedBy()).thenReturn(uid);
        when(operations.getById(operationId)).thenReturn(operation);
    }

    @Test

    public void pendingQueueDoesNotExposeAnotherAccount() {
        when(authentication.getCurrentUserId()).thenReturn("accountA");
        when(queue.getDueLocalPendingOperationIds(123L))
                .thenReturn(Arrays.asList("A-pending", "B-pending"));
        operationOwnedBy("A-pending", "accountA");
        operationOwnedBy("B-pending", "accountB");

        assertEquals(Collections.singletonList("A-pending"),
                repository.getDueLocalPendingOperationIdsForWorker(123L));
    }

    @Test

    public void submittedQueueDoesNotProcessAnotherAccount() {
        when(authentication.getCurrentUserId()).thenReturn("accountB");
        when(queue.getDueSubmittedOperationIds(456L))
                .thenReturn(Arrays.asList("A-submitted", "B-submitted"));
        operationOwnedBy("A-submitted", "accountA");
        operationOwnedBy("B-submitted", "accountB");

        assertEquals(Collections.singletonList("B-submitted"),
                repository.getDueSubmittedOperationIdsForWorker(456L));
    }

    @Test

    public void signedOutWorkerDoesNotReadOrProcessQueueOperations() {
        when(authentication.getCurrentUserId()).thenReturn(null);
        when(queue.getDueLocalPendingOperationIds(123L))
                .thenReturn(Collections.singletonList("A-pending"));

        assertTrue(repository.getDueLocalPendingOperationIdsForWorker(123L).isEmpty());
        verify(operations, never()).getById("A-pending");
    }

    @Test

    public void ownerCheckRejectsOtherAccount() {
        when(authentication.getCurrentUserId()).thenReturn("accountB");
        operationOwnedBy("A-pending", "accountA");

        assertFalse(repository.isOwnedBySessionForWorker("A-pending", "accountB"));
    }

    @Test

    public void ownerCheckAcceptsMatchingSession() {
        when(authentication.getCurrentUserId()).thenReturn("accountA");
        operationOwnedBy("A-pending", "accountA");

        assertTrue(repository.isOwnedBySessionForWorker("A-pending", "accountA"));
    }

    @Test

    public void accountChangeDuringEnumerationFailsClosed() {
        when(authentication.getCurrentUserId())
                .thenReturn("accountA", "accountA", "accountB");
        when(queue.getDueLocalPendingOperationIds(123L))
                .thenReturn(Arrays.asList("A1", "A2"));
        operationOwnedBy("A1", "accountA");
        operationOwnedBy("A2", "accountA");

        assertTrue(repository.getDueLocalPendingOperationIdsForWorker(123L).isEmpty());
    }
}
