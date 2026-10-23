package com.medicationinventory.data.repository;

import android.content.Context;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;
import com.medicationinventory.model.dto.InventoryReconciliationResult;
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
import static org.junit.Assert.assertTrue;


/**
 * Covers duplicate batch receipt conflicts and confirms that an approved top-up replaces cloud stock exactly once.
 */
@RunWith(AndroidJUnit4.class)
public final class InventoryExistingBatchReceiptTest {
    private AppDatabase database;
    private static final String BATCH = "established-batch";
    private static final String OP = "topup-operation";
    private static final long EXPIRY = 2051222400000L; // 2035

    @Before public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries().build();
        database.medicineDao().upsert(new MedicineEntity(
                "med-1", "MED-001", "QA Medicine", null, "Antibiotics",
                "250 mg", "Tablet", "Tablet", 10, 50, true, 1000L, 1L));
        database.batchDao().upsert(new BatchEntity(BATCH, "med-1", "LOT-A",
                1000L, EXPIRY, 100, 100, BatchStatus.ACTIVE, 1000L));
        InventoryOperationEntity operation = new InventoryOperationEntity(OP,
                InventoryOperationType.RECEIPT, "med-1", 50,
                null, "qa-user", 2000L, InventoryOperationStatus.LOCAL_PENDING,
                null, null, "test-device", null, null);
        operation.setReceiptMode("EXISTING_BATCH");
        operation.setReceiptReceivedDate(2000L);
        database.stockOperationDao().commitExistingBatchReceipt(operation,
                new OperationAllocationEntity("allocation-1", OP, BATCH, 50, 0),
                new SyncQueueEntity(OP, 0, 2000L, 0L, null));
    }

    @After public void tearDown() {
        if (database != null) database.close();
    }

    @Test public void conflictKeepsEstablishedBatchAndDoesNotIncreaseStock() {
        new InventoryReconciliationRepository(database).reconcile(
                new InventoryReconciliationResult(OP, InventoryOperationStatus.CONFLICT,
                        "BATCH_DETAILS_CHANGED", "Batch details changed.", 3000L, 4000L,
                        Collections.singletonList(BATCH), Collections.emptyList(),
                        Collections.emptyList()));
        BatchEntity preserved = database.batchDao().getById(BATCH);
        assertNotNull(preserved);
        assertEquals(100, preserved.getQuantityAvailable());
        assertEquals(InventoryOperationStatus.CONFLICT,
                database.inventoryOperationDao().getById(OP).getStatus());
        assertTrue(database.syncQueueDao().getDueItems(Long.MAX_VALUE).isEmpty());
    }

    @Test public void confirmedTopUpReplacesStockFromCloudExactlyOnce() {
        InventoryReconciliationResult.AuthoritativeBatch cloud =
                new InventoryReconciliationResult.AuthoritativeBatch(BATCH,
                        "med-1", "LOT-A", 1000L, EXPIRY, 150, 150, "ACTIVE", 4000L);
        InventoryReconciliationResult.AuthoritativeTransaction txn =
                new InventoryReconciliationResult.AuthoritativeTransaction(
                        "cloud-txn-1", OP, "med-1", BATCH,
                        "RECEIPT", 50, null, "qa-user", 2000L, 4000L);
        InventoryReconciliationResult result = new InventoryReconciliationResult(
                OP, InventoryOperationStatus.CONFIRMED,
                "EXISTING_BATCH_RECEIPT_CONFIRMED", "Received.", 3000L, 4000L,
                Collections.singletonList(BATCH), Collections.singletonList(cloud),
                Collections.singletonList(txn));
        new InventoryReconciliationRepository(database).reconcile(result);
        BatchEntity batch = database.batchDao().getById(BATCH);
        assertNotNull(batch);
        assertEquals(150, batch.getQuantityAvailable());
        assertEquals(150, batch.getInitialQuantity());
        assertEquals(1, database.transactionDao().getForOperation(OP).size());
        assertEquals(InventoryOperationStatus.CONFIRMED,
                database.inventoryOperationDao().getById(OP).getStatus());
    }
}
