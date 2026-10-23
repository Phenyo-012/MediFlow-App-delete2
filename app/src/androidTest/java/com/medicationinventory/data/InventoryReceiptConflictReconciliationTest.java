package com.medicationinventory.data.repository;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;
import com.medicationinventory.model.dto.InventoryReconciliationResult;
import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.BatchStatus;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


/**
 * Checks that rejected provisional receipts remove only their own batch, movement, and alerts while preserving the operation record.
 */
@RunWith(AndroidJUnit4.class)
public final class InventoryReceiptConflictReconciliationTest {
    private static final String MEDICINE_ID = "qa-medicine";
    private static final String OPERATION_ID = "qa-rejected-receipt";
    private static final String PROVISIONAL_BATCH_ID = "qa-losing-device-batch";
    private static final String BATCH_NUMBER = "QA-DUP-001";
    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        database.medicineDao().upsert(new MedicineEntity(
                MEDICINE_ID, "QA-MED-001", "QA Medicine", null, "Antibiotics",
                "250 mg", "Tablet", "Tablet", 10, 50, true, 1000L, 1L));

        InventoryOperationEntity operation = new InventoryOperationEntity(
                OPERATION_ID, InventoryOperationType.RECEIPT, MEDICINE_ID, 20,
                null, "qa-user", 1000L, InventoryOperationStatus.SUBMITTED,
                null, null, "device-B", null, null);
        BatchEntity provisional = new BatchEntity(
                PROVISIONAL_BATCH_ID, MEDICINE_ID, BATCH_NUMBER, 1000L,
                1800000000000L, 20, 20, BatchStatus.ACTIVE, 1000L);
        OperationAllocationEntity allocation = new OperationAllocationEntity(
                "qa-allocation", OPERATION_ID, PROVISIONAL_BATCH_ID, 20, 0);
        InventoryTransactionEntity movement = new InventoryTransactionEntity(
                "qa-provisional-transaction", OPERATION_ID, MEDICINE_ID,
                PROVISIONAL_BATCH_ID, "RECEIPT", 20, null, "qa-user",
                1000L, 0L, true);
        SyncQueueEntity queue = new SyncQueueEntity(
                OPERATION_ID, 5, 0L, 1000L, "ROOM_RECONCILIATION_FAILED");
        database.stockOperationDao().commitNewBatchReceipt(
                operation, provisional, allocation, movement, queue);
    }

    @After

    public void tearDown() {
        if (database != null) database.close();
    }

    @Test

    public void conflictAlsoRemovesOnlyAlertsForTheRejectedProvisionalBatch() {
        BatchEntity unrelated = new BatchEntity(
                "qa-unrelated-batch", MEDICINE_ID, "QA-OTHER-BATCH", 1000L,
                1800000000000L, 10, 10, BatchStatus.ACTIVE, 1000L);
        database.batchDao().upsert(unrelated);

        // An expiry alert created locally for the losing provisional batch
        // holds an additional NO_ACTION foreign key to batches.batchId.
        database.alertDao().upsert(new AlertEntity(
                "qa-provisional-alert", AlertType.NEAR_EXPIRY, MEDICINE_ID,
                PROVISIONAL_BATCH_ID, AlertSeverity.MEDIUM, AlertStatus.ACTIVE,
                "Temporary QA expiry alert", 1000L, 1000L));
        database.alertDao().upsert(new AlertEntity(
                "qa-unrelated-alert", AlertType.NEAR_EXPIRY, MEDICINE_ID,
                "qa-unrelated-batch", AlertSeverity.MEDIUM, AlertStatus.ACTIVE,
                "Retain this alert", 1000L, 1000L));
        database.alertDao().upsert(new AlertEntity(
                "qa-medicine-alert", AlertType.LOW_STOCK, MEDICINE_ID,
                null, AlertSeverity.HIGH, AlertStatus.ACTIVE,
                "Retain medicine alert", 1000L, 1000L));

        new InventoryReconciliationRepository(database).reconcile(
                new InventoryReconciliationResult(
                        OPERATION_ID, InventoryOperationStatus.CONFLICT,
                        "BATCH_ALREADY_EXISTS", "This batch already exists.",
                        1100L, 1200L,
                        Collections.singletonList(PROVISIONAL_BATCH_ID),
                        Collections.emptyList(), Collections.emptyList()));

        assertNull(database.batchDao().getById(PROVISIONAL_BATCH_ID));
        assertNull(database.alertDao().getById("qa-provisional-alert"));
        assertNotNull(database.alertDao().getById("qa-unrelated-alert"));
        assertNotNull(database.alertDao().getById("qa-medicine-alert"));
        assertNotNull(database.batchDao().getById("qa-unrelated-batch"));
        assertEquals(InventoryOperationStatus.CONFLICT, database
                .inventoryOperationDao().getById(OPERATION_ID).getStatus());
        assertTrue(database.syncQueueDao().getDueItems(Long.MAX_VALUE).isEmpty());
    }

    @Test

    public void conflictRemovesOnlyProvisionalBatchAndMovementAndKeepsOperation() {
        new InventoryReconciliationRepository(database).reconcile(
                new InventoryReconciliationResult(
                        OPERATION_ID, InventoryOperationStatus.CONFLICT,
                        "BATCH_ALREADY_EXISTS", "This medicine already has a batch with that number.",
                        1100L, 1200L,
                        Collections.singletonList(PROVISIONAL_BATCH_ID),
                        Collections.emptyList(), Collections.emptyList()));

        assertNull(database.batchDao().getById(PROVISIONAL_BATCH_ID));
        assertTrue(database.transactionDao().getForOperation(OPERATION_ID).isEmpty());
        assertTrue(database.allocationDao().getForOperation(OPERATION_ID).isEmpty());
        assertTrue(database.syncQueueDao().getDueItems(Long.MAX_VALUE).isEmpty());

        InventoryOperationEntity operation = database.inventoryOperationDao()
                .getById(OPERATION_ID);
        assertNotNull(operation);
        assertEquals(InventoryOperationStatus.CONFLICT, operation.getStatus());
        assertEquals("BATCH_ALREADY_EXISTS", operation.getResultCode());

        // A server-confirmed batch with the same business batch number but
        // the winning device's ID can now be imported without a UNIQUE error.
        BatchEntity winner = new BatchEntity(
                "qa-winning-device-batch", MEDICINE_ID, BATCH_NUMBER, 1000L,
                1800000000000L, 20, 20, BatchStatus.ACTIVE, 1200L);
        database.batchDao().upsert(winner);
        assertNotNull(database.batchDao().getById("qa-winning-device-batch"));
        assertNull(database.batchDao().getById(PROVISIONAL_BATCH_ID));
    }
}
