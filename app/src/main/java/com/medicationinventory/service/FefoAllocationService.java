package com.medicationinventory.service;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.model.enums.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;



/**
 * Builds a deterministic first-expiry-first-out allocation from candidate batch snapshots. Only positive-quantity, active, non-expired batches are eligible; allocation order is expiry date, receipt date, then batch ID. The result reports insufficient stock without returning a partial allocation, allowing the caller to decide whether to stop or begin the separately authorized override flow.
 */
public class FefoAllocationService
{
    private final ZoneId businessZoneId;



    public FefoAllocationService()
    {
        this(
                ZoneId.of(
                        "Africa/Johannesburg"
                )
        );
    }


    /**
     * Creates the allocator with the timezone used to interpret expiry timestamps as calendar dates.
     * @param businessZoneId timezone used for the expiry-day eligibility boundary
     * @throws IllegalArgumentException when the timezone is null
     */
    public FefoAllocationService(
            ZoneId businessZoneId)
    {
        if (businessZoneId == null)
        {
            throw new IllegalArgumentException(
                    "Business timezone is required."
            );
        }


        this.businessZoneId =
                businessZoneId;
    }


    /**
     * Selects active, non-expired batches with positive stock and allocates in expiry order.
     * Expiry ties use receipt date and then batch ID so the result is deterministic.
     *
     * @param batches candidate local batches; null is treated as an empty list
     * @param requestedQuantity positive quantity requested by the issuing workflow
     * @return eligible batches and allocations, or an insufficient-stock result with no allocations
     * @throws IllegalArgumentException if the quantity is not positive
     */
    public Result allocate(
            List<BatchEntity> batches,
            int requestedQuantity)
    {
        return allocate(
                batches,
                requestedQuantity,
                LocalDate.now(
                        businessZoneId
                )
        );
    }


    /** Uses an explicit business date so callers and tests can evaluate expiry boundaries deterministically. */
    Result allocate(
            List<BatchEntity> batches,
            int requestedQuantity,
            LocalDate currentDate)
    {
        if (requestedQuantity <= 0)
        {
            throw new IllegalArgumentException(
                    "Requested quantity must be greater than zero."
            );
        }


        if (currentDate == null)
        {
            throw new IllegalArgumentException(
                    "Current date is required."
            );
        }


        /*
         * 1. DETERMINE ELIGIBLE BATCHES
         */
        List<BatchEntity> eligibleBatches =
                new ArrayList<>();


        if (batches != null)
        {
            for (BatchEntity batch :
                    batches)
            {
                if (isEligible(
                        batch,
                        currentDate
                ))
                {
                    eligibleBatches.add(
                            batch
                    );
                }
            }
        }



        eligibleBatches.sort(
                Comparator
                        .comparingLong(
                                BatchEntity::getExpiryDate
                        )
                        .thenComparingLong(
                                BatchEntity::getReceivedDate
                        )
                        .thenComparing(
                                BatchEntity::getBatchId
                        )
        );



        List<EligibleBatch> eligibleBatchOptions =
                new ArrayList<>();


        for (BatchEntity batch :
                eligibleBatches)
        {
            eligibleBatchOptions.add(
                    new EligibleBatch(
                            batch.getBatchId(),
                            batch.getBatchNumber(),
                            batch.getExpiryDate(),
                            batch.getReceivedDate(),
                            batch.getQuantityAvailable()
                    )
            );
        }

        int totalAvailable =
                0;


        for (BatchEntity batch :
                eligibleBatches)
        {
            totalAvailable +=
                    batch.getQuantityAvailable();
        }


        if (totalAvailable <
                requestedQuantity)
        {
            return Result.insufficient(
                    requestedQuantity,
                    totalAvailable,
                    eligibleBatchOptions
            );
        }


        List<Allocation> allocations =
                new ArrayList<>();


        int remaining =
                requestedQuantity;


        for (BatchEntity batch :
                eligibleBatches)
        {
            if (remaining <= 0)
            {
                break;
            }


            int allocationQuantity =
                    Math.min(
                            batch.getQuantityAvailable(),
                            remaining
                    );


            if (allocationQuantity <= 0)
            {
                continue;
            }


            allocations.add(
                    new Allocation(
                            batch.getBatchId(),
                            batch.getBatchNumber(),
                            batch.getExpiryDate(),
                            batch.getReceivedDate(),
                            batch.getQuantityAvailable(),
                            allocationQuantity
                    )
            );


            remaining -=
                    allocationQuantity;
        }


        if (remaining != 0)
        {
            throw new IllegalStateException(
                    "FEFO allocation did not satisfy the requested quantity."
            );
        }


        return Result.success(
                requestedQuantity,
                totalAvailable,
                allocations,
                eligibleBatchOptions
        );
    }



    private boolean isEligible(
            BatchEntity batch,
            LocalDate currentDate)
    {
        if (batch == null)
        {
            return false;
        }


        if (batch.getQuantityAvailable() <= 0)
        {
            return false;
        }


        if (batch.getStatus() !=
                BatchStatus.ACTIVE)
        {
            return false;
        }


        LocalDate expiryDate =
                toLocalDate(
                        batch.getExpiryDate()
                );


        return !expiryDate.isBefore(
                currentDate
        );
    }


    private LocalDate toLocalDate(
            long epochMillis)
    {
        return Instant
                .ofEpochMilli(
                        epochMillis
                )
                .atZone(
                        businessZoneId
                )
                .toLocalDate();
    }



    public static class EligibleBatch
    {
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
    {
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
                int allocatedQuantity)
        {
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


        public int getAvailableBeforeIssue()
        {
            return availableBeforeIssue;
        }


        public int getAllocatedQuantity()
        {
            return allocatedQuantity;
        }
    }



    public static class Result
    {
        private final boolean sufficientStock;

        private final int requestedQuantity;
        private final int totalEligibleStock;

        private final List<Allocation> allocations;

        private final List<EligibleBatch> eligibleBatches;


        private Result(
                boolean sufficientStock,
                int requestedQuantity,
                int totalEligibleStock,
                List<Allocation> allocations,
                List<EligibleBatch> eligibleBatches)
        {
            this.sufficientStock =
                    sufficientStock;

            this.requestedQuantity =
                    requestedQuantity;

            this.totalEligibleStock =
                    totalEligibleStock;

            this.allocations =
                    allocations;

            this.eligibleBatches =
                    eligibleBatches;
        }


        static Result success(
                int requestedQuantity,
                int totalEligibleStock,
                List<Allocation> allocations,
                List<EligibleBatch> eligibleBatches)
        {
            return new Result(
                    true,
                    requestedQuantity,
                    totalEligibleStock,
                    new ArrayList<>(
                            allocations
                    ),
                    new ArrayList<>(
                            eligibleBatches
                    )
            );
        }


        static Result insufficient(
                int requestedQuantity,
                int totalEligibleStock,
                List<EligibleBatch> eligibleBatches)
        {
            return new Result(
                    false,
                    requestedQuantity,
                    totalEligibleStock,
                    new ArrayList<>(),
                    new ArrayList<>(
                            eligibleBatches
                    )
            );
        }


        public boolean hasSufficientStock()
        {
            return sufficientStock;
        }


        public int getRequestedQuantity()
        {
            return requestedQuantity;
        }


        public int getTotalEligibleStock()
        {
            return totalEligibleStock;
        }


        public List<Allocation> getAllocations()
        {
            return new ArrayList<>(
                    allocations
            );
        }


        public List<EligibleBatch> getEligibleBatches()
        {
            return new ArrayList<>(
                    eligibleBatches
            );
        }

    }
}