package com.medicationinventory.model.domain;

import com.medicationinventory.service.FefoAllocationService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



/**
 * Serializable snapshot passed from FEFO review to stock issue submission. It
 * preserves the user's requested quantity, reason, candidate batches, and
 * proposed allocations so the activity can review the selection before the
 * service revalidates it against current stock. Carrying the proposal does not
 * reserve batches or make the issue authoritative.
 */
public class IssueFefoDraft
        implements Serializable
{

    private static final long serialVersionUID = 1L;
    private final ArrayList<EligibleBatch> eligibleBatches;


    private final String medicineId;
    private final String medicineName;

    private final int requestedQuantity;
    private final int totalEligibleStock;

    private final String reason;

    private final ArrayList<Allocation> allocations;


    public IssueFefoDraft(
            String medicineId,
            String medicineName,
            int requestedQuantity,
            int totalEligibleStock,
            String reason,
            ArrayList<Allocation> allocations,
            ArrayList<EligibleBatch> eligibleBatches)
    {
        this.medicineId =
                medicineId;

        this.medicineName =
                medicineName;

        this.requestedQuantity =
                requestedQuantity;

        this.totalEligibleStock =
                totalEligibleStock;

        this.reason =
                reason;

        this.allocations =
                allocations == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        allocations
                );

        this.eligibleBatches =
                eligibleBatches == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        eligibleBatches
                );
    }


    public static IssueFefoDraft from(
            String medicineId,
            String medicineName,
            String reason,
            FefoAllocationService.Result result
    ) {

        ArrayList<Allocation> allocations =
                new ArrayList<>();


        for (
                FefoAllocationService.Allocation allocation :
                result.getAllocations())
        {
            allocations.add(
                    new Allocation(
                            allocation.getBatchId(),
                            allocation.getBatchNumber(),
                            allocation.getExpiryDate(),
                            allocation.getReceivedDate(),
                            allocation.getAvailableBeforeIssue(),
                            allocation.getAllocatedQuantity()
                    )
            );
        }


        ArrayList<EligibleBatch> eligibleBatches =
                new ArrayList<>();


        for (
                FefoAllocationService.EligibleBatch batch :
                result.getEligibleBatches())
        {
            eligibleBatches.add(
                    new EligibleBatch(
                            batch.getBatchId(),
                            batch.getBatchNumber(),
                            batch.getExpiryDate(),
                            batch.getReceivedDate(),
                            batch.getAvailableQuantity()
                    )
            );
        }


        return new IssueFefoDraft(
                medicineId,
                medicineName,
                result.getRequestedQuantity(),
                result.getTotalEligibleStock(),
                reason,
                allocations,
                eligibleBatches
        );
    }


    public String getMedicineId() {
        return medicineId;
    }


    public String getMedicineName() {
        return medicineName;
    }


    public int getRequestedQuantity() {
        return requestedQuantity;
    }


    public int getTotalEligibleStock() {
        return totalEligibleStock;
    }


    public String getReason() {
        return reason;
    }


    public ArrayList<Allocation> getAllocations() {

        return new ArrayList<>(
                allocations
        );
    }


    public ArrayList<EligibleBatch> getEligibleBatches()
    {
        return new ArrayList<>(
                eligibleBatches
        );
    }

    public static class EligibleBatch
            implements Serializable
    {
        private static final long serialVersionUID =
                1L;


        private final String batchId;
        private final String batchNumber;

        private final long expiryDate;
        private final long receivedDate;

        private final int availableQuantity;


        public EligibleBatch(
                String batchId,
                String batchNumber,
                long expiryDate,
                long receivedDate,
                int availableQuantity)
        {
            this.batchId =
                    batchId;

            this.batchNumber =
                    batchNumber;

            this.expiryDate =
                    expiryDate;

            this.receivedDate =
                    receivedDate;

            this.availableQuantity =
                    availableQuantity;
        }


        public String getBatchId()
        {
            return batchId;
        }


        public String getBatchNumber()
        {
            return batchNumber;
        }


        public long getExpiryDate()
        {
            return expiryDate;
        }


        public long getReceivedDate()
        {
            return receivedDate;
        }


        public int getAvailableQuantity()
        {
            return availableQuantity;
        }
    }

    public static class Allocation
            implements Serializable {

        private static final long serialVersionUID = 1L;


        private final String batchId;
        private final String batchNumber;

        private final long expiryDate;
        private final long receivedDate;

        private final int availableBeforeIssue;
        private final int allocatedQuantity;


        public Allocation(
                String batchId,
                String batchNumber,
                long expiryDate,
                long receivedDate,
                int availableBeforeIssue,
                int allocatedQuantity
        ) {

            this.batchId =
                    batchId;

            this.batchNumber =
                    batchNumber;

            this.expiryDate =
                    expiryDate;

            this.receivedDate =
                    receivedDate;

            this.availableBeforeIssue =
                    availableBeforeIssue;

            this.allocatedQuantity =
                    allocatedQuantity;
        }


        public String getBatchId() {
            return batchId;
        }


        public String getBatchNumber() {
            return batchNumber;
        }


        public long getExpiryDate() {
            return expiryDate;
        }


        public long getReceivedDate() {
            return receivedDate;
        }


        public int getAvailableBeforeIssue() {
            return availableBeforeIssue;
        }


        public int getAllocatedQuantity() {
            return allocatedQuantity;
        }
    }
}