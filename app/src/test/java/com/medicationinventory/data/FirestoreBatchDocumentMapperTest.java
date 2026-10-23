package com.medicationinventory.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.firebase.Timestamp;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.model.enums.BatchStatus;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;



/**
 * Checks valid and legacy batch fields and rejects mismatched IDs, invalid quantities, unsupported statuses, and missing required values.
 */
public final class FirestoreBatchDocumentMapperTest {

    private Map<String, Object> valid() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("batchId", "batch-1");
        fields.put("medicineId", "medicine-1");
        fields.put("batchNumber", "LOT-123");
        fields.put("receivedDate", new Timestamp(new Date(1_700_000_000_000L)));
        fields.put("expiryDate", new Timestamp(new Date(1_900_000_000_000L)));
        fields.put("quantityAvailable", 8L);
        fields.put("initialQuantity", 10L);
        fields.put("status", "ACTIVE");
        fields.put("updatedAt", new Timestamp(new Date(1_750_000_000_000L)));
        return fields;
    }

    @Test public void validFirestoreBatch() {
        BatchEntity batch = FirestoreBatchDocumentMapper.fromDocument("batch-1", valid());
        assertNotNull(batch);
        assertEquals("medicine-1", batch.getMedicineId());
        assertEquals("LOT-123", batch.getBatchNumber());
        assertEquals(8, batch.getQuantityAvailable());
        assertEquals(BatchStatus.ACTIVE, batch.getStatus());
        assertEquals(1_750_000_000_000L, batch.getUpdatedAt());
    }

    @Test public void adjustmentsMayExceedReceiptInitialQuantity() {
        Map<String,Object> f = valid();
        f.put("quantityAvailable", 15L);
        assertEquals(15, FirestoreBatchDocumentMapper.fromDocument("batch-1", f)
                .getQuantityAvailable());
    }

    @Test public void acceptsEpochMillisFromLegacyDocument() {
        Map<String,Object> f = valid();
        f.put("receivedDate", 1_700_000_000_000L);
        f.put("expiryDate", 1_900_000_000_000L);
        f.put("updatedAt", 1_750_000_000_000L);
        assertEquals(1_900_000_000_000L,
                FirestoreBatchDocumentMapper.fromDocument("batch-1", f).getExpiryDate());
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesIdMismatch() {
        Map<String,Object> f = valid(); f.put("batchId", "another-id");
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesNegativeQuantity() {
        Map<String,Object> f = valid(); f.put("quantityAvailable", -1L);
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesFractionalQuantity() {
        Map<String,Object> f = valid(); f.put("initialQuantity", 2.4);
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesUnsupportedBatchStatus() {
        Map<String,Object> f = valid(); f.put("status", "UNKNOWN");
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesMissingRequiredTimestamp() {
        Map<String,Object> f = valid(); f.remove("updatedAt");
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void refusesEmptyMedicineId() {
        Map<String,Object> f = valid(); f.put("medicineId", "   ");
        FirestoreBatchDocumentMapper.fromDocument("batch-1", f);
    }
}
