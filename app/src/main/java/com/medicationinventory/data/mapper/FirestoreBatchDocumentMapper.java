package com.medicationinventory.data.mapper;

import com.google.firebase.Timestamp;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.model.enums.BatchStatus;

import java.util.Date;
import java.util.Map;


/**
 * Validates Firestore batch document fields and maps them to a local BatchEntity.
 */
public final class FirestoreBatchDocumentMapper {

    private FirestoreBatchDocumentMapper() {
    }


    public static BatchEntity fromDocument(String documentId, Map<String, Object> fields) {
        if (documentId == null || documentId.trim().isEmpty() || fields == null) {
            throw new IllegalArgumentException("Batch document ID and data are required.");
        }
        String id = documentId.trim();
        Object declaredId = fields.get("batchId");
        if (declaredId != null && (!(declaredId instanceof String)
                || !id.equals(declaredId))) {
            throw new IllegalArgumentException("batchId does not match document ID.");
        }
        String medicineId = text(fields, "medicineId");
        String batchNumber = text(fields, "batchNumber");
        long receivedDate = requiredTimestamp(fields, "receivedDate");
        long expiryDate = requiredTimestamp(fields, "expiryDate");
        int available = quantity(fields, "quantityAvailable");
        int initial = quantity(fields, "initialQuantity");
        String statusText = text(fields, "status");
        BatchStatus status;
        try {
            status = BatchStatus.valueOf(statusText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported batch status: " + statusText);
        }
        long updatedAt = requiredTimestamp(fields, "updatedAt");

        return new BatchEntity(id, medicineId, batchNumber, receivedDate,
                expiryDate, available, initial, status, updatedAt);
    }

    private static String text(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (!(value instanceof String))
            throw new IllegalArgumentException(key + " must be text.");
        String result = ((String) value).trim();
        if (result.isEmpty() || result.length() > 250)
            throw new IllegalArgumentException(key + " must be 1-250 characters.");
        return result;
    }

    private static int quantity(Map<String, Object> fields, String key) {
        Object raw = fields.get(key);
        if (!(raw instanceof Number))
            throw new IllegalArgumentException(key + " must be a number.");
        double number = ((Number) raw).doubleValue();
        if (!Double.isFinite(number) || number < 0 || number > Integer.MAX_VALUE
                || number != Math.rint(number)) {
            throw new IllegalArgumentException(key + " must be a nonnegative integer.");
        }
        return ((Number) raw).intValue();
    }

    private static long requiredTimestamp(Map<String, Object> fields, String key) {
        Object raw = fields.get(key);
        long millis;
        if (raw instanceof Timestamp) {
            millis = ((Timestamp) raw).toDate().getTime();
        } else if (raw instanceof Date) {
            millis = ((Date) raw).getTime();
        } else if (raw instanceof Number) {
            double number = ((Number) raw).doubleValue();
            if (!Double.isFinite(number) || number < 0 || number > Long.MAX_VALUE
                    || number != Math.rint(number)) {
                throw new IllegalArgumentException(key + " is invalid.");
            }
            millis = ((Number) raw).longValue();
        } else {
            throw new IllegalArgumentException(key + " must be a Firestore Timestamp or " +
                    "epoch milliseconds.");
        }
        if (millis < 0) throw new IllegalArgumentException(key + " must be nonnegative.");
        return millis;
    }
}
