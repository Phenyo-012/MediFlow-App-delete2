package com.medicationinventory.data.mapper;

import com.google.firebase.Timestamp;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;



/**
 * Verifies confirmed operation and transaction imports, movement signs and timestamps, and rejection of incomplete or forged history.
 */
public class FirestoreHistoryDocumentMapperTest {

    private static Map<String, Object> operation(String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("operationId", "op-1");
        m.put("operationType", type);
        m.put("medicineId", "med-1");
        m.put("requestedQuantity", 8L);
        m.put("performedBy", "user-1");
        m.put("deviceId", "device-1");
        m.put("clientCreatedAt", 1600000000000L);
        m.put("status", "CONFIRMED");
        Map<String, Object> a = new HashMap<>();
        a.put("batchId", "batch-1");
        a.put("quantity", 8L);
        m.put("allocations", Arrays.asList(a));
        return m;
    }

    private static Map<String, Object> transaction(String type, int quantity) {
        Map<String, Object> m = new HashMap<>();
        m.put("transactionId", "tx-1");
        m.put("operationId", "op-1");
        m.put("medicineId", "med-1");
        m.put("batchId", "batch-1");
        m.put("transactionType", type);
        m.put("quantityChange", quantity);
        m.put("performedBy", "user-1");
        m.put("clientCreatedAt", 1600000000000L);
        m.put("serverCreatedAt", new Timestamp(new Date(1600000001000L)));
        return m;
    }

    @Test public void confirmedOperationImportsAllocationsAsHistoricalRows() {
        FirestoreHistoryDocumentMapper.ConfirmedOperation r =
                FirestoreHistoryDocumentMapper.confirmedOperation("op-1", operation("RECEIPT"));
        assertNotNull(r);
        assertEquals(InventoryOperationStatus.CONFIRMED, r.operation.getStatus());
        assertEquals(InventoryOperationType.RECEIPT, r.operation.getOperationType());
        assertEquals("batch-1", r.allocations.get(0).getBatchId());
        assertEquals(8, r.allocations.get(0).getQuantity());
    }

    @Test public void nonConfirmedOperationsAreSkipped() {
        Map<String,Object> m = operation("ISSUE");
        m.put("status", "SUBMITTED");
        assertNull(FirestoreHistoryDocumentMapper.confirmedOperation("op-1", m));
    }

    @Test public void rejectedCloudOperationIsSkipped() {
        Map<String,Object> m = operation("ADJUSTMENT");
        m.put("status", "REJECTED");
        assertNull(FirestoreHistoryDocumentMapper.confirmedOperation("op-1", m));
    }

    @Test(expected = IllegalArgumentException.class)

    public void mismatchedOperationIdIsRejected() {
        FirestoreHistoryDocumentMapper.confirmedOperation("different", operation("RECEIPT"));
    }

    @Test(expected = IllegalArgumentException.class)

    public void missingOperationAllocationIsRejected() {
        Map<String,Object> m = operation("RECEIPT");
        m.put("allocations", new ArrayList<>());
        FirestoreHistoryDocumentMapper.confirmedOperation("op-1", m);
    }

    @Test public void confirmedReceiptTransactionHasCorrectTimestampAndSign() {
        InventoryTransactionEntity result = FirestoreHistoryDocumentMapper.confirmedTransaction(
                "tx-1", transaction("RECEIPT", 8));
        assertEquals(1600000001000L, result.getServerCreatedAt());
        assertEquals(8, result.getQuantityChange());
        assertFalse(result.isProvisional());
    }

    @Test public void confirmedIssueHasNegativeMovement() {
        InventoryTransactionEntity result = FirestoreHistoryDocumentMapper.confirmedTransaction(
                "tx-1", transaction("ISSUE", -3));
        assertEquals(-3, result.getQuantityChange());
    }

    @Test public void adjustmentAllowsNegativeMovement() {
        InventoryTransactionEntity result = FirestoreHistoryDocumentMapper.confirmedTransaction(
                "tx-1", transaction("ADJUSTMENT", -3));
        assertEquals(-3, result.getQuantityChange());
    }

    @Test(expected = IllegalArgumentException.class)

    public void positiveIssueIsRejected() {
        FirestoreHistoryDocumentMapper.confirmedTransaction("tx-1", transaction("ISSUE", 3));
    }

    @Test(expected = IllegalArgumentException.class)

    public void provisionalCloudTransactionIsRejected() {
        Map<String,Object> m = transaction("ISSUE", -3);
        m.put("provisional", true);
        FirestoreHistoryDocumentMapper.confirmedTransaction("tx-1", m);
    }

    @Test(expected = IllegalArgumentException.class)

    public void forgedTransactionIdIsRejected() {
        FirestoreHistoryDocumentMapper.confirmedTransaction("different", transaction("RECEIPT", 8));
    }

    @Test(expected = IllegalArgumentException.class)

    public void missingServerTimeIsRejected() {
        Map<String,Object> m = transaction("RECEIPT", 8);
        m.remove("serverCreatedAt");
        FirestoreHistoryDocumentMapper.confirmedTransaction("tx-1", m);
    }
}
