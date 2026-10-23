package com.medicationinventory.model.dto;

import com.medicationinventory.model.enums.InventoryOperationStatus;

import java.util.Collections;
import java.util.List;



/**
 * Carries Inventory Reconciliation Result data across the Android and trusted-backend boundary.
 */
public class InventoryReconciliationResult
{

    private final String operationId;
    private final InventoryOperationStatus status;

    private final String resultCode;
    private final String resultMessage;

    private final long serverCreatedAt;
    private final long processedAt;

    private final List<String> affectedBatchIds;
    private final List<AuthoritativeBatch> batches;
    private final List<AuthoritativeTransaction> transactions;



    public InventoryReconciliationResult(String operationId, InventoryOperationStatus status,
                                         String resultCode, String resultMessage, long serverCreatedAt, long processedAt,
                                         List<String> affectedBatchIds, List<AuthoritativeBatch> batches,
                                         List<AuthoritativeTransaction> transactions)
    {

        this.operationId = operationId;
        this.status = status;

        this.resultCode = resultCode;
        this.resultMessage = resultMessage;

        this.serverCreatedAt = serverCreatedAt;
        this.processedAt = processedAt;

        this.affectedBatchIds = affectedBatchIds == null ? Collections.emptyList()
                : Collections.unmodifiableList(affectedBatchIds);

        this.batches = batches == null ? Collections.emptyList() : Collections.unmodifiableList(
                batches);

        this.transactions = transactions == null ? Collections.emptyList()
                : Collections.unmodifiableList(transactions);
    }


    public String getOperationId()
    {
        return operationId;
    }

    public InventoryOperationStatus getStatus()
    {
        return status;
    }

    public String getResultCode()
    {
        return resultCode;
    }

    public String getResultMessage()
    {
        return resultMessage;
    }

    public long getServerCreatedAt()
    {
        return serverCreatedAt;
    }

    public long getProcessedAt()
    {
        return processedAt;
    }

    public List<String> getAffectedBatchIds()
    {
        return affectedBatchIds;
    }

    public List<AuthoritativeBatch> getBatches()
    {
        return batches;
    }

    public List<AuthoritativeTransaction> getTransactions()
    {
        return transactions;
    }


    public static class AuthoritativeBatch
    {

        private final String batchId;
        private final String medicineId;
        private final String batchNumber;

        private final long receivedDate;
        private final long expiryDate;

        private final int initialQuantity;
        private final int quantityAvailable;

        private final String status;

        private final long updatedAt;


        /**
         * Keeps the authoritative batch decision in one place, rather than repeating it in several callers.
         */
        public AuthoritativeBatch(String batchId, String medicineId, String batchNumber,
                                  long receivedDate, long expiryDate, int initialQuantity, int quantityAvailable,
                                  String status, long updatedAt)
        {

            this.batchId = batchId;
            this.medicineId = medicineId;
            this.batchNumber = batchNumber;

            this.receivedDate = receivedDate;
            this.expiryDate = expiryDate;

            this.initialQuantity = initialQuantity;
            this.quantityAvailable = quantityAvailable;

            this.status = status;

            this.updatedAt = updatedAt;
        }


        public String getBatchId()
        {
            return batchId;
        }

        public String getMedicineId()
        {
            return medicineId;
        }

        public String getBatchNumber()
        {
            return batchNumber;
        }

        public long getReceivedDate()
        {
            return receivedDate;
        }

        public long getExpiryDate()
        {
            return expiryDate;
        }

        public int getInitialQuantity()
        {
            return initialQuantity;
        }

        public int getQuantityAvailable()
        {
            return quantityAvailable;
        }

        public String getStatus()
        {
            return status;
        }

        public long getUpdatedAt()
        {
            return updatedAt;
        }
    }


    public static class AuthoritativeTransaction
    {

        private final String transactionId;
        private final String operationId;
        private final String medicineId;
        private final String batchId;

        private final String transactionType;

        private final int quantityChange;

        private final String reason;
        private final String performedBy;

        private final long clientCreatedAt;
        private final long serverCreatedAt;


        /**
         * Keeps the authoritative transaction decision in one place, rather than repeating it in several callers.
         */
        public AuthoritativeTransaction(String transactionId, String operationId, String medicineId,
                                        String batchId, String transactionType, int quantityChange, String reason,
                                        String performedBy, long clientCreatedAt, long serverCreatedAt)
        {

            this.transactionId = transactionId;
            this.operationId = operationId;
            this.medicineId = medicineId;
            this.batchId = batchId;

            this.transactionType = transactionType;

            this.quantityChange = quantityChange;

            this.reason = reason;
            this.performedBy = performedBy;

            this.clientCreatedAt = clientCreatedAt;
            this.serverCreatedAt = serverCreatedAt;
        }


        public String getTransactionId()
        {
            return transactionId;
        }

        public String getOperationId()
        {
            return operationId;
        }

        public String getMedicineId()
        {
            return medicineId;
        }

        public String getBatchId()
        {
            return batchId;
        }

        public String getTransactionType()
        {
            return transactionType;
        }

        public int getQuantityChange()
        {
            return quantityChange;
        }

        public String getReason()
        {
            return reason;
        }

        public String getPerformedBy()
        {
            return performedBy;
        }

        public long getClientCreatedAt()
        {
            return clientCreatedAt;
        }

        public long getServerCreatedAt()
        {
            return serverCreatedAt;
        }
    }
}