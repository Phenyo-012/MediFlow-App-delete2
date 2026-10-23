package com.medicationinventory.data.mapper;

import com.google.firebase.Timestamp;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Validates Firestore operation and transaction documents and maps them to local history records.
 */
public final class FirestoreHistoryDocumentMapper {

    private FirestoreHistoryDocumentMapper() { }

    public static final class ConfirmedOperation {
        public final InventoryOperationEntity operation;
        public final List<OperationAllocationEntity> allocations;

        ConfirmedOperation(InventoryOperationEntity operation,
                           List<OperationAllocationEntity> allocations) {
            this.operation = operation;
            this.allocations = Collections.unmodifiableList(new ArrayList<>(allocations));
        }
    }

    public static ConfirmedOperation confirmedOperation(String documentId,
                                                        Map<String, Object> fields) {
        String id = documentId(documentId, fields, "operationId");
        String status = requiredText(fields, "status");
        if (!"CONFIRMED".equals(status)) {
            if ("SUBMITTED".equals(status) || "REJECTED".equals(status)
                    || "CONFLICT".equals(status)) return null;
            throw new IllegalArgumentException("Unknown cloud operation status.");
        }
        InventoryOperationType type;
        try {
            type = InventoryOperationType.valueOf(requiredText(fields, "operationType"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown operation type.", exception);
        }
        String medicineId = requiredText(fields, "medicineId");
        int requested = positiveInteger(fields, "requestedQuantity");
        String performer = requiredText(fields, "performedBy");
        String device = requiredText(fields, "deviceId");
        long created = timestamp(fields, "clientCreatedAt", false);
        List<OperationAllocationEntity> allocations = new ArrayList<>();
        Object raw = fields.get("allocations");
        if (!(raw instanceof List)) throw new IllegalArgumentException("allocations must be a list.");
        List<?> entries = (List<?>) raw;
        if (entries.isEmpty() || entries.size() > 100) {
            throw new IllegalArgumentException("allocations count outside accepted range.");
        }
        for (int i = 0; i < entries.size(); i++) {
            if (!(entries.get(i) instanceof Map)) {
                throw new IllegalArgumentException("Invalid allocation object.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> allocation = (Map<String, Object>) entries.get(i);
            String batchId = requiredText(allocation, "batchId");
            int qty = positiveInteger(allocation, "quantity");

            allocations.add(new OperationAllocationEntity(id + ":history:" + i,
                    id, batchId, qty, i));
        }
        return new ConfirmedOperation(new InventoryOperationEntity(id, type, medicineId,
                requested, optionalText(fields, "reason"), performer, created,
                InventoryOperationStatus.CONFIRMED,
                optionalText(fields, "relatedOperationId"),
                optionalText(fields, "overrideReason"), device,
                optionalText(fields, "resultCode"),
                optionalText(fields, "resultMessage")), allocations);
    }

    public static InventoryTransactionEntity confirmedTransaction(String documentId,
                                                                  Map<String, Object> fields) {
        String id = documentId(documentId, fields, "transactionId");
        String operation = requiredText(fields, "operationId");
        String medicine = requiredText(fields, "medicineId");
        String batch = requiredText(fields, "batchId");
        String type = requiredText(fields, "transactionType");
        if (!("RECEIPT".equals(type) || "ISSUE".equals(type)
                || "ADJUSTMENT".equals(type) || "CORRECTION".equals(type))) {
            throw new IllegalArgumentException("Unknown transaction type.");
        }
        int change = signedInteger(fields, "quantityChange");
        if (change == 0 || ("RECEIPT".equals(type) && change < 0)
                || ("ISSUE".equals(type) && change > 0)) {
            throw new IllegalArgumentException("Invalid signed stock movement.");
        }
        Object provisional = fields.get("provisional");
        if (Boolean.TRUE.equals(provisional)) {
            throw new IllegalArgumentException("Provisional transaction is not cloud history.");
        }
        if (provisional != null && !(provisional instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid provisional flag.");
        }
        return new InventoryTransactionEntity(id, operation, medicine, batch, type,
                change, optionalText(fields, "reason"), requiredText(fields, "performedBy"),
                timestamp(fields, "clientCreatedAt", false),
                timestamp(fields, "serverCreatedAt", false), false);
    }

    private static String documentId(String id, Map<String, Object> fields, String key) {
        if (fields == null || id == null || id.trim().isEmpty() || id.length() > 250) {
            throw new IllegalArgumentException("Document ID/data missing.");
        }
        Object embedded = fields.get(key);
        if (embedded != null && (!(embedded instanceof String) || !id.equals(embedded))) {
            throw new IllegalArgumentException("Document ID conflicts with " + key);
        }
        return id;
    }

    private static String requiredText(Map<String, Object> fields, String key) {
        String text = optionalText(fields, key);
        if (text == null) throw new IllegalArgumentException(key + " is required.");
        return text;
    }

    private static String optionalText(Map<String, Object> fields, String key) {
        Object raw = fields.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String)) throw new IllegalArgumentException(key + " must be text.");
        String value = ((String) raw).trim();
        if (value.isEmpty()) return null;
        if (value.length() > 1000) throw new IllegalArgumentException(key + " is too long.");
        return value;
    }

    private static int positiveInteger(Map<String, Object> fields, String key) {
        int value = signedInteger(fields, key);
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive.");
        return value;
    }

    private static int signedInteger(Map<String, Object> fields, String key) {
        Object raw = fields.get(key);
        if (!(raw instanceof Number)) throw new IllegalArgumentException(key + " must be numeric.");
        double number = ((Number) raw).doubleValue();
        if (!Double.isFinite(number) || number > Integer.MAX_VALUE
                || number < Integer.MIN_VALUE || number != Math.rint(number)) {
            throw new IllegalArgumentException(key + " must be a 32-bit integer.");
        }
        return ((Number) raw).intValue();
    }

    private static long timestamp(Map<String, Object> fields, String key, boolean optional) {
        Object raw = fields.get(key);
        if (raw == null && optional) return 0;
        long millis;
        if (raw instanceof Timestamp) {
            millis = ((Timestamp) raw).toDate().getTime();
        } else if (raw instanceof Date) {
            millis = ((Date) raw).getTime();
        } else if (raw instanceof Number) {
            double value = ((Number) raw).doubleValue();
            if (!Double.isFinite(value) || value != Math.rint(value)
                    || value > Long.MAX_VALUE || value < 0) {
                throw new IllegalArgumentException(key + " must be timestamp/epoch millis.");
            }
            millis = ((Number) raw).longValue();
        } else {
            throw new IllegalArgumentException(key + " must be a timestamp/epoch millis.");
        }
        if (millis <= 0) throw new IllegalArgumentException(key + " must be positive.");
        return millis;
    }
}
