package com.medicationinventory.service;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.enums.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.List;



/**
 * Calculates usable and near-expiry stock from batch records and derives reorder thresholds and quantities from each medicine reorder and target levels. Usable stock excludes non-positive, expired, or non-active batches; near-expiry stock remains part of the usable total.
 */
public class ReorderRecommendationService
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );



    /**
     * Totals active, positive, non-expired batch quantities using the South African business date.
     * @param batches candidate batches; null is treated as empty
     * @return usable units
     * @throws IllegalStateException when the total exceeds the supported integer range
     */
    public int calculateUsableStock(
            List<Batch> batches)
    {
        if (batches == null
                || batches.isEmpty())
        {
            return 0;
        }


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        long total =
                0L;


        for (Batch batch :
                batches)
        {
            if (!isUsableBatch(
                    batch,
                    today
            ))
            {
                continue;
            }


            total +=
                    batch.getQuantityAvailable();



            if (total >
                    Integer.MAX_VALUE)
            {
                throw new IllegalStateException(
                        "Usable stock exceeds supported quantity range."
                );
            }
        }


        return (int) total;
    }


    /** Returns true when usable stock is at or below the medicine reorder level. */
    public boolean isReorderRequired(
            Medicine medicine,
            int usableStock)
    {
        validateMedicine(
                medicine
        );


        if (usableStock < 0)
        {
            throw new IllegalArgumentException(
                    "Usable stock cannot be negative."
            );
        }


        return usableStock
                <= medicine.getReorderLevel();
    }


    /**
     * Returns the gap to target stock when usable stock is at or below the reorder level.
     * @throws IllegalArgumentException for invalid medicine thresholds or negative stock
     * @throws IllegalStateException when the calculated quantity exceeds the supported range
     */
    public int calculateRecommendedQuantity(
            Medicine medicine,
            int usableStock)
    {
        validateMedicine(
                medicine
        );


        if (usableStock < 0)
        {
            throw new IllegalArgumentException(
                    "Usable stock cannot be negative."
            );
        }


        boolean reorderRequired =
                isReorderRequired(
                        medicine,
                        usableStock
                );


        if (!reorderRequired)
        {
            return 0;
        }


        long recommended =
                (long) medicine.getTargetStockLevel()
                        - usableStock;


        if (recommended <= 0)
        {
            return 0;
        }


        if (recommended >
                Integer.MAX_VALUE)
        {
            throw new IllegalStateException(
                    "Recommended reorder quantity exceeds supported quantity range."
            );
        }


        return (int) recommended;
    }


    public int calculateNearExpiryStock(
            List<Batch> batches,
            int warningDays)
    {
        if (warningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        if (batches == null
                || batches.isEmpty())
        {
            return 0;
        }


        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        LocalDate warningEnd =
                today.plusDays(
                        warningDays
                );


        long total =
                0L;


        for (Batch batch :
                batches)
        {
            if (!isUsableBatch(
                    batch,
                    today
            ))
            {
                continue;
            }


            LocalDate expiryDate =
                    toBusinessDate(
                            batch.getExpiryDate()
                    );


            if (!expiryDate.isAfter(
                    warningEnd
            ))
            {
                total +=
                        batch.getQuantityAvailable();


                if (total >
                        Integer.MAX_VALUE)
                {
                    throw new IllegalStateException(
                            "Near-expiry stock exceeds supported quantity range."
                    );
                }
            }
        }


        return (int) total;
    }


    public ReorderRecommendation
    buildReorderRecommendation(
            Medicine medicine,
            List<Batch> batches,
            int nearExpiryWarningDays)
    {
        validateMedicine(
                medicine
        );


        int usableStock =
                calculateUsableStock(
                        batches
                );


        int nearExpiryStock =
                calculateNearExpiryStock(
                        batches,
                        nearExpiryWarningDays
                );


        boolean reorderRequired =
                isReorderRequired(
                        medicine,
                        usableStock
                );


        int recommendedQuantity =
                calculateRecommendedQuantity(
                        medicine,
                        usableStock
                );


        return new ReorderRecommendation(
                medicine.getMedicineId(),
                usableStock,
                nearExpiryStock,
                medicine.getReorderLevel(),
                medicine.getTargetStockLevel(),
                reorderRequired,
                recommendedQuantity
        );
    }


    private boolean isUsableBatch(
            Batch batch,
            LocalDate today)
    {
        if (batch == null)
        {
            return false;
        }


        if (batch.getQuantityAvailable()
                <= 0)
        {
            return false;
        }


        if (batch.getStatus()
                == BatchStatus.EXPIRED)
        {
            return false;
        }


        LocalDate expiryDate =
                toBusinessDate(
                        batch.getExpiryDate()
                );


        return !expiryDate.isBefore(
                today
        );
    }


    private LocalDate toBusinessDate(
            long timestamp)
    {
        if (timestamp <= 0)
        {
            throw new IllegalArgumentException(
                    "Batch expiry date is invalid."
            );
        }


        return Instant
                .ofEpochMilli(
                        timestamp
                )
                .atZone(
                        BUSINESS_ZONE
                )
                .toLocalDate();
    }



    private void validateMedicine(
            Medicine medicine)
    {
        if (medicine == null)
        {
            throw new IllegalArgumentException(
                    "Medicine is required."
            );
        }


        if (medicine.getReorderLevel()
                < 0)
        {
            throw new IllegalArgumentException(
                    "Medicine reorder level cannot be negative."
            );
        }


        if (medicine.getTargetStockLevel()
                < medicine.getReorderLevel())
        {
            throw new IllegalArgumentException(
                    "Target stock level cannot be below reorder level."
            );
        }
    }
}