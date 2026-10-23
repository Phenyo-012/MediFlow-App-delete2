package com.medicationinventory.service;


import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.AnalyticsReport;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;



/**
 * Builds the dashboard analytics snapshot from local medicine, batch, alert, and transaction
 * entities. The report combines current catalog and stock summaries with movement trends and top
 * issued items; it does not query Firestore or independently reconcile pending operations. Inputs
 * may be absent and are treated as empty collections, while invalid movement windows are rejected.
 * Date grouping uses the device's system time zone, matching the current implementation's report
 * semantics.
 */
public class AnalyticsReportService
{
    private static final String
            UNCATEGORISED =
            "Uncategorised";


    private static final int
            TOP_ISSUED_LIMIT =
            5;


    /**
     * Aggregates the supplied local snapshot into report cards, distributions, alerts, and movement
     * summaries. Only active medicines contribute to current catalog and stock totals; historical
     * transactions are separately constrained by the requested half-open movement interval.
     *
     * @param medicineEntities local medicine master rows
     * @param batchEntities local batch quantities and expiry dates
     * @param alertEntities locally reconciled alert rows
     * @param transactionEntities local movement history, including its current provisional state
     * @param movementWindowDays number of calendar days used for trend buckets; must be positive
     * @param nowMillis reference instant for current stock and alert calculations
     * @param movementStartInclusive inclusive lower movement timestamp
     * @param movementEndExclusive exclusive upper movement timestamp; must exceed the start
     * @return assembled analytics report
     * @throws IllegalArgumentException if the movement window or interval is invalid
     */
    public AnalyticsReport buildReport(
            List<MedicineEntity> medicineEntities,
            List<BatchEntity> batchEntities,
            List<AlertEntity> alertEntities,
            List<InventoryTransactionEntity> transactionEntities,
            int movementWindowDays,
            long nowMillis,
            long movementStartInclusive,
            long movementEndExclusive)
    {
        if (movementWindowDays <= 0)
        {
            throw new IllegalArgumentException(
                    "Movement window days must be positive."
            );
        }


        if (movementStartInclusive
                >=
                movementEndExclusive)
        {
            throw new IllegalArgumentException(
                    "Analytics movement date range is invalid."
            );
        }


        List<MedicineEntity> medicines =
                medicineEntities == null
                        ? Collections.emptyList()
                        : medicineEntities;


        List<BatchEntity> batches =
                batchEntities == null
                        ? Collections.emptyList()
                        : batchEntities;


        List<AlertEntity> alerts =
                alertEntities == null
                        ? Collections.emptyList()
                        : alertEntities;


        List<InventoryTransactionEntity> transactions =
                transactionEntities == null
                        ? Collections.emptyList()
                        : transactionEntities;


        ZoneId zoneId =
                ZoneId.systemDefault();


        LocalDate today =
                Instant
                        .ofEpochMilli(
                                nowMillis
                        )
                        .atZone(
                                zoneId
                        )
                        .toLocalDate();


        Map<String, MedicineEntity>
                medicinesById =
                new HashMap<>();


        Set<String>
                activeMedicineIds =
                new HashSet<>();


        List<MedicineEntity>
                activeMedicines =
                new ArrayList<>();


        Map<String, Long>
                categoryCounts =
                new TreeMap<>(
                        String.CASE_INSENSITIVE_ORDER
                );


        for (MedicineEntity medicine :
                medicines)
        {
            if (medicine == null)
            {
                continue;
            }


            medicinesById.put(
                    medicine.getMedicineId(),
                    medicine
            );


            if (!medicine.isActive())
            {
                continue;
            }


            activeMedicines.add(
                    medicine
            );


            activeMedicineIds.add(
                    medicine.getMedicineId()
            );


            String category =
                    normalizeCategory(
                            medicine.getCategory()
                    );


            categoryCounts.merge(
                    category,
                    1L,
                    Long::sum
            );
        }


        Map<String, Long>
                usableStockByMedicine =
                new HashMap<>();


        long totalUsableStock =
                0L;


        long expiredStockQuantity =
                0L;


        long expiryZeroToFourteen =
                0L;


        long expiryFifteenToThirty =
                0L;


        long expiryThirtyOneToNinety =
                0L;


        long expiryOverNinety =
                0L;


        for (BatchEntity batch :
                batches)
        {
            if (batch == null
                    ||
                    !activeMedicineIds.contains(
                            batch.getMedicineId()
                    )
                    ||
                    batch.getQuantityAvailable() <= 0
                    ||
                    batch.getExpiryDate() <= 0L)
            {
                continue;
            }


            long quantity =
                    batch.getQuantityAvailable();


            LocalDate expiryDate =
                    Instant
                            .ofEpochMilli(
                                    batch.getExpiryDate()
                            )
                            .atZone(
                                    zoneId
                            )
                            .toLocalDate();


            long daysUntilExpiry =
                    ChronoUnit.DAYS.between(
                            today,
                            expiryDate
                    );


            if (daysUntilExpiry < 0L)
            {
                expiredStockQuantity +=
                        quantity;

                continue;
            }


            usableStockByMedicine.merge(
                    batch.getMedicineId(),
                    quantity,
                    Long::sum
            );


            totalUsableStock +=
                    quantity;


            if (daysUntilExpiry <= 14L)
            {
                expiryZeroToFourteen +=
                        quantity;
            }
            else if (daysUntilExpiry <= 30L)
            {
                expiryFifteenToThirty +=
                        quantity;
            }
            else if (daysUntilExpiry <= 90L)
            {
                expiryThirtyOneToNinety +=
                        quantity;
            }
            else
            {

                expiryOverNinety +=
                        quantity;
            }
        }


        int inStockMedicineCount =
                0;


        int lowStockMedicineCount =
                0;


        int outOfStockMedicineCount =
                0;


        for (MedicineEntity medicine :
                activeMedicines)
        {
            long usableStock =
                    usableStockByMedicine
                            .getOrDefault(
                                    medicine.getMedicineId(),
                                    0L
                            );


            if (usableStock <= 0L)
            {
                outOfStockMedicineCount++;

                continue;
            }


            if (usableStock
                    <=
                    medicine.getReorderLevel())
            {
                lowStockMedicineCount++;

                continue;
            }


            inStockMedicineCount++;
        }


        long expiringWithin30DaysQuantity =
                expiryZeroToFourteen
                        +
                        expiryFifteenToThirty;


        int openAlertCount =
                0;


        int activeAlertCount =
                0;


        long lowStockAlertCount =
                0L;


        long nearExpiryAlertCount =
                0L;


        long expiredAlertCount =
                0L;


        for (AlertEntity alert :
                alerts)
        {
            if (alert == null
                    ||
                    !activeMedicineIds.contains(
                            alert.getMedicineId()
                    )
                    ||
                    alert.getStatus()
                            ==
                            AlertStatus.RESOLVED)
            {
                continue;
            }


            openAlertCount++;


            if (alert.getStatus()
                    ==
                    AlertStatus.ACTIVE)
            {
                activeAlertCount++;
            }


            AlertType type =
                    alert.getAlertType();


            if (type == AlertType.LOW_STOCK)
            {
                lowStockAlertCount++;
            }
            else if (type
                    ==
                    AlertType.NEAR_EXPIRY)
            {
                nearExpiryAlertCount++;
            }
            else if (type
                    ==
                    AlertType.EXPIRED)
            {
                expiredAlertCount++;
            }
        }


        AnalyticsReport.Summary summary =
                new AnalyticsReport.Summary(
                        activeMedicines.size(),
                        totalUsableStock,
                        inStockMedicineCount,
                        lowStockMedicineCount,
                        outOfStockMedicineCount,
                        expiringWithin30DaysQuantity,
                        expiredStockQuantity,
                        openAlertCount,
                        activeAlertCount
                );


        List<AnalyticsReport.DistributionItem>
                categoryDistribution =
                buildSortedDistribution(
                        categoryCounts
                );


        List<AnalyticsReport.DistributionItem>
                stockStatusDistribution =
                new ArrayList<>();


        stockStatusDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "In Stock",
                        inStockMedicineCount
                )
        );


        stockStatusDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Low Stock",
                        lowStockMedicineCount
                )
        );


        stockStatusDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Out of Stock",
                        outOfStockMedicineCount
                )
        );


        List<AnalyticsReport.DistributionItem>
                expiryDistribution =
                new ArrayList<>();


        expiryDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "0-14 days",
                        expiryZeroToFourteen
                )
        );


        expiryDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "15-30 days",
                        expiryFifteenToThirty
                )
        );


        expiryDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "31-90 days",
                        expiryThirtyOneToNinety
                )
        );


        expiryDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "90+ days",
                        expiryOverNinety
                )
        );


        List<AnalyticsReport.DistributionItem>
                alertDistribution =
                new ArrayList<>();


        alertDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Low Stock",
                        lowStockAlertCount
                )
        );


        alertDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Near Expiry",
                        nearExpiryAlertCount
                )
        );


        alertDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Expired",
                        expiredAlertCount
                )
        );


        MovementCalculation movementCalculation =
                calculateMovements(
                        transactions,
                        medicinesById,
                        zoneId,
                        movementStartInclusive,
                        movementEndExclusive
                );


        return new AnalyticsReport(
                movementWindowDays,
                summary,
                categoryDistribution,
                stockStatusDistribution,
                expiryDistribution,
                alertDistribution,
                movementCalculation
                        .movementTypeDistribution,
                movementCalculation
                        .movementSummary,
                movementCalculation
                        .trend,
                movementCalculation
                        .topIssued
        );
    }


    private MovementCalculation
    calculateMovements(
            List<InventoryTransactionEntity> transactions,
            Map<String, MedicineEntity> medicinesById,
            ZoneId zoneId,
            long movementStartInclusive,
            long movementEndExclusive)
    {
        long receivedQuantity =
                0L;


        long issuedQuantity =
                0L;


        long adjustmentInQuantity =
                0L;


        long adjustmentOutQuantity =
                0L;


        long correctionInQuantity =
                0L;


        long correctionOutQuantity =
                0L;


        LocalDate startDate =
                Instant
                        .ofEpochMilli(
                                movementStartInclusive
                        )
                        .atZone(
                                zoneId
                        )
                        .toLocalDate();


        LocalDate endDate =
                Instant
                        .ofEpochMilli(
                                movementEndExclusive
                                        -
                                        1L
                        )
                        .atZone(
                                zoneId
                        )
                        .toLocalDate();


        Map<LocalDate, long[]>
                trendByDate =
                new LinkedHashMap<>();


        LocalDate cursor =
                startDate;


        while (!cursor.isAfter(
                endDate
        ))
        {
            trendByDate.put(
                    cursor,
                    new long[] {
                            0L,
                            0L
                    }
            );


            cursor =
                    cursor.plusDays(
                            1L
                    );
        }


        Map<String, Long>
                issuedByMedicine =
                new HashMap<>();


        for (InventoryTransactionEntity transaction :
                transactions)
        {
            if (transaction == null
                    ||
                    transaction.isProvisional())
            {
                continue;
            }


            long effectiveTime =
                    effectiveTransactionTime(
                            transaction
                    );


            if (effectiveTime
                    <
                    movementStartInclusive
                    ||
                    effectiveTime
                            >=
                            movementEndExclusive)
            {
                continue;
            }


            String type =
                    normalizeMovementType(
                            transaction
                                    .getTransactionType()
                    );


            if (type == null)
            {
                continue;
            }


            long quantity =
                    Math.abs(
                            (long)
                                    transaction
                                            .getQuantityChange()
                    );


            LocalDate movementDate =
                    Instant
                            .ofEpochMilli(
                                    effectiveTime
                            )
                            .atZone(
                                    zoneId
                            )
                            .toLocalDate();


            long[] dayValues =
                    trendByDate.get(
                            movementDate
                    );


            switch (type)
            {
                case "RECEIPT":

                    receivedQuantity +=
                            quantity;


                    if (dayValues != null)
                    {
                        dayValues[0] +=
                                quantity;
                    }

                    break;


                case "ISSUE":

                    issuedQuantity +=
                            quantity;


                    if (dayValues != null)
                    {
                        dayValues[1] +=
                                quantity;
                    }


                    issuedByMedicine.merge(
                            transaction
                                    .getMedicineId(),
                            quantity,
                            Long::sum
                    );

                    break;


                case "ADJUSTMENT_IN":

                    adjustmentInQuantity +=
                            quantity;

                    break;


                case "ADJUSTMENT_OUT":

                    adjustmentOutQuantity +=
                            quantity;

                    break;


                case "CORRECTION_IN":

                    correctionInQuantity +=
                            quantity;

                    break;


                case "CORRECTION_OUT":

                    correctionOutQuantity +=
                            quantity;

                    break;


                default:


                    if (type.startsWith(
                            "ADJUSTMENT"
                    ))
                    {
                        if (transaction
                                .getQuantityChange()
                                >=
                                0)
                        {
                            adjustmentInQuantity +=
                                    quantity;
                        }
                        else
                        {
                            adjustmentOutQuantity +=
                                    quantity;
                        }
                    }
                    else if (type.startsWith(
                            "CORRECTION"
                    ))
                    {
                        if (transaction
                                .getQuantityChange()
                                >=
                                0)
                        {
                            correctionInQuantity +=
                                    quantity;
                        }
                        else
                        {
                            correctionOutQuantity +=
                                    quantity;
                        }
                    }

                    break;
            }
        }


        AnalyticsReport.MovementSummary
                movementSummary =
                new AnalyticsReport.MovementSummary(
                        receivedQuantity,
                        issuedQuantity,
                        adjustmentInQuantity,
                        adjustmentOutQuantity,
                        correctionInQuantity,
                        correctionOutQuantity
                );


        List<AnalyticsReport.DistributionItem>
                movementTypeDistribution =
                new ArrayList<>();


        movementTypeDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Receipts",
                        receivedQuantity
                )
        );


        movementTypeDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Issues",
                        issuedQuantity
                )
        );


        movementTypeDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Adjustments",
                        adjustmentInQuantity
                                +
                                adjustmentOutQuantity
                )
        );


        movementTypeDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Corrections",
                        correctionInQuantity
                                +
                                correctionOutQuantity
                )
        );


        List<AnalyticsReport.MovementTrendPoint>
                movementTrend =
                new ArrayList<>();


        for (Map.Entry<LocalDate, long[]>
                entry :
                trendByDate.entrySet())
        {
            long dayStartMillis =
                    entry
                            .getKey()
                            .atStartOfDay(
                                    zoneId
                            )
                            .toInstant()
                            .toEpochMilli();


            long[] values =
                    entry.getValue();


            movementTrend.add(
                    new AnalyticsReport
                            .MovementTrendPoint(
                            dayStartMillis,
                            values[0],
                            values[1]
                    )
            );
        }


        List<AnalyticsReport.TopMedicineItem>
                topIssued =
                buildTopIssuedMedicines(
                        issuedByMedicine,
                        medicinesById
                );


        return new MovementCalculation(
                movementSummary,
                movementTypeDistribution,
                movementTrend,
                topIssued
        );
    }


    private List<AnalyticsReport.TopMedicineItem>
    buildTopIssuedMedicines(
            Map<String, Long> issuedByMedicine,
            Map<String, MedicineEntity> medicinesById)
    {
        List<Map.Entry<String, Long>>
                entries =
                new ArrayList<>(
                        issuedByMedicine
                                .entrySet()
                );


        entries.sort(
                (left, right) ->
                {
                    int quantityComparison =
                            Long.compare(
                                    right.getValue(),
                                    left.getValue()
                            );


                    if (quantityComparison != 0)
                    {
                        return quantityComparison;
                    }


                    MedicineEntity leftMedicine =
                            medicinesById.get(
                                    left.getKey()
                            );


                    MedicineEntity rightMedicine =
                            medicinesById.get(
                                    right.getKey()
                            );


                    String leftName =
                            leftMedicine == null
                                    ? left.getKey()
                                    : leftMedicine
                                    .getMedicineName();


                    String rightName =
                            rightMedicine == null
                                    ? right.getKey()
                                    : rightMedicine
                                    .getMedicineName();


                    return leftName
                            .compareToIgnoreCase(
                                    rightName
                            );
                }
        );


        List<AnalyticsReport.TopMedicineItem>
                result =
                new ArrayList<>();


        int count =
                Math.min(
                        TOP_ISSUED_LIMIT,
                        entries.size()
                );


        for (int index = 0;
             index < count;
             index++)
        {
            Map.Entry<String, Long>
                    entry =
                    entries.get(
                            index
                    );


            MedicineEntity medicine =
                    medicinesById.get(
                            entry.getKey()
                    );


            String medicineName =
                    medicine == null
                            ? entry.getKey()
                            : medicine
                            .getMedicineName();


            String medicineCode =
                    medicine == null
                            ? ""
                            : medicine
                            .getMedicineCode();


            result.add(
                    new AnalyticsReport
                            .TopMedicineItem(
                            entry.getKey(),
                            medicineName,
                            medicineCode,
                            entry.getValue()
                    )
            );
        }


        return result;
    }


    private List<AnalyticsReport.DistributionItem>
    buildSortedDistribution(
            Map<String, Long> values)
    {
        List<AnalyticsReport.DistributionItem>
                result =
                new ArrayList<>();


        for (Map.Entry<String, Long>
                entry :
                values.entrySet())
        {
            result.add(
                    new AnalyticsReport
                            .DistributionItem(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }


        result.sort(
                Comparator
                        .comparingLong(
                                AnalyticsReport
                                        .DistributionItem
                                        ::getValue
                        )
                        .reversed()
                        .thenComparing(
                                AnalyticsReport
                                        .DistributionItem
                                        ::getLabel,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );


        return result;
    }


    private long effectiveTransactionTime(
            InventoryTransactionEntity transaction)
    {
        if (transaction.getServerCreatedAt()
                >
                0L)
        {
            return transaction
                    .getServerCreatedAt();
        }


        return transaction
                .getClientCreatedAt();
    }

    private String normalizeMovementType(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        return normalized.isEmpty()
                ? null
                : normalized;
    }



    private String normalizeCategory(
            String value)
    {
        if (value == null)
        {
            return UNCATEGORISED;
        }


        String normalized =
                value.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        return normalized.isEmpty()
                ? UNCATEGORISED
                : normalized;
    }


    private static final class MovementCalculation
    {
        private final AnalyticsReport.MovementSummary
                movementSummary;

        private final List<AnalyticsReport.DistributionItem>
                movementTypeDistribution;

        private final List<AnalyticsReport.MovementTrendPoint>
                trend;

        private final List<AnalyticsReport.TopMedicineItem>
                topIssued;


        private MovementCalculation(
                AnalyticsReport.MovementSummary movementSummary,
                List<AnalyticsReport.DistributionItem> movementTypeDistribution,
                List<AnalyticsReport.MovementTrendPoint> trend,
                List<AnalyticsReport.TopMedicineItem> topIssued)
        {
            this.movementSummary =
                    movementSummary;

            this.movementTypeDistribution =
                    movementTypeDistribution;

            this.trend =
                    trend;

            this.topIssued =
                    topIssued;
        }
    }
}