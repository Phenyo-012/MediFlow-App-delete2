package com.medicationinventory.model.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Carries Inventory Operation Upload Dto data across the Android and trusted-backend boundary.
 */
public class InventoryOperationUploadDto
{
    private final String operationId;
    private final String operationType;
    private final String medicineId;

    private final int requestedQuantity;

    private final List<AllocationDto> allocations;

    private final String reason;

    private final String performedBy;
    private final String deviceId;

    private final long clientCreatedAt;

    private final String relatedOperationId;
    private final String overrideReason;

    private final String adjustmentDirection;

    private final String correctionDirection;
    private final String receiptMode;


    public InventoryOperationUploadDto(
            String operationId,
            String operationType,
            String medicineId,
            int requestedQuantity,
            List<AllocationDto> allocations,
            String reason,
            String performedBy,
            String deviceId,
            long clientCreatedAt,
            String relatedOperationId,
            String overrideReason,
            String adjustmentDirection,
            String correctionDirection)
    {
        this(operationId, operationType, medicineId, requestedQuantity,
                allocations, reason, performedBy, deviceId, clientCreatedAt,
                relatedOperationId, overrideReason, adjustmentDirection,
                correctionDirection, null);
    }

    public InventoryOperationUploadDto(
            String operationId, String operationType, String medicineId,
            int requestedQuantity, List<AllocationDto> allocations,
            String reason, String performedBy, String deviceId, long clientCreatedAt,
            String relatedOperationId, String overrideReason,
            String adjustmentDirection, String correctionDirection, String receiptMode) {
        this.receiptMode = receiptMode;
        this.operationId = operationId;

        this.operationType = operationType;

        this.medicineId = medicineId;

        this.requestedQuantity = requestedQuantity;

        this.allocations = allocations == null ? new ArrayList<>() : new ArrayList<>(allocations);

        this.reason = reason;

        this.performedBy = performedBy;

        this.deviceId = deviceId;

        this.clientCreatedAt = clientCreatedAt;

        this.relatedOperationId = relatedOperationId;

        this.overrideReason = overrideReason;

        this.adjustmentDirection = adjustmentDirection;

        this.correctionDirection = correctionDirection;
    }

    public String getOperationId()
    {
        return operationId;
    }

    public String getOperationType()
    {
        return operationType;
    }

    public String getMedicineId()
    {
        return medicineId;
    }

    public int getRequestedQuantity()
    {
        return requestedQuantity;
    }

    public List<AllocationDto> getAllocations()
    {
        return new ArrayList<>(allocations);
    }

    public String getReason()
    {
        return reason;
    }

    public String getPerformedBy()
    {
        return performedBy;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public long getClientCreatedAt()
    {
        return clientCreatedAt;
    }

    public String getRelatedOperationId()
    {
        return relatedOperationId;
    }

    public String getOverrideReason()
    {
        return overrideReason;
    }

    public String getAdjustmentDirection()
    {
        return adjustmentDirection;
    }

    public String getCorrectionDirection()
    {
        return correctionDirection;
    }


    public Map<String, Object> toFirestoreMap()
    {

        Map<String, Object> data = new HashMap<>();

        data.put("operationId", operationId);

        data.put("operationType", operationType);

        data.put("medicineId", medicineId);

        data.put("requestedQuantity", requestedQuantity);

        List<Map<String, Object>> allocationMaps = new ArrayList<>();

        for (AllocationDto allocation : allocations)
        {
            allocationMaps.add(allocation.toFirestoreMap());
        }

        data.put("allocations", allocationMaps);

        data.put("performedBy", performedBy);

        data.put("deviceId", deviceId);

        data.put("clientCreatedAt", clientCreatedAt);


        data.put("status", "SUBMITTED");

        putOptional(data, "reason", reason);

        putOptional(data, "relatedOperationId", relatedOperationId);

        putOptional(data, "overrideReason", overrideReason);

        putOptional(data, "adjustmentDirection", adjustmentDirection);

        putOptional(data, "correctionDirection", correctionDirection);
        if ("RECEIPT".equals(operationType)) {
            data.put("receiptMode", "EXISTING_BATCH".equals(receiptMode)
                    ? "EXISTING_BATCH" : "NEW_BATCH");
        }

        return data;
    }


    private static void putOptional(Map<String, Object> data, String key, String value)
    {

        if (value == null)
        {
            return;
        }

        String trimmed = value.trim();

        if (!trimmed.isEmpty())
        {
            data.put(key, trimmed);
        }
    }

    public static class AllocationDto
    {

        private final String batchId;

        private final int quantity;
        private final int allocationOrder;


        private final String batchNumber;

        private final Long receivedDate;
        private final Long expiryDate;


        public AllocationDto(String batchId, int quantity, int allocationOrder, String batchNumber,
                             Long receivedDate, Long expiryDate)
        {

            this.batchId = batchId;

            this.quantity = quantity;

            this.allocationOrder = allocationOrder;

            this.batchNumber = batchNumber;

            this.receivedDate = receivedDate;

            this.expiryDate = expiryDate;
        }

        public String getBatchId()
        {
            return batchId;
        }

        public int getQuantity()
        {
            return quantity;
        }

        public int getAllocationOrder()
        {
            return allocationOrder;
        }

        public String getBatchNumber()
        {
            return batchNumber;
        }

        public Long getReceivedDate()
        {
            return receivedDate;
        }

        public Long getExpiryDate()
        {
            return expiryDate;
        }


        public Map<String, Object> toFirestoreMap()
        {
            Map<String, Object> data = new HashMap<>();

            data.put("batchId", batchId);

            data.put("quantity", quantity);

            data.put("allocationOrder", allocationOrder);

            if (batchNumber != null && !batchNumber.trim().isEmpty())
            {

                data.put("batchNumber", batchNumber.trim());
            }

            if (receivedDate != null)
            {
                data.put("receivedDate", receivedDate);
            }

            if (expiryDate != null)
            {
                data.put("expiryDate", expiryDate);
            }

            return data;
        }
    }
}