package com.medicationinventory.service;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.AnalyticsReport;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.BatchStatus;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;




/**
 * Verifies analytics snapshots for inventory, open-alert distributions, and movement totals using the configured reporting inputs.
 */
public class AnalyticsReportServiceTest
{
    private final AnalyticsReportService
            service =
            new AnalyticsReportService();


    @Test
    public void buildReport_calculatesSnapshotAnalytics()
    {
        LocalDate today =
                LocalDate.of(
                        2026,
                        10,
                        7
                );


        long nowMillis =
                today
                        .atTime(
                                12,
                                0
                        )
                        .atZone(
                                ZoneId.systemDefault()
                        )
                        .toInstant()
                        .toEpochMilli();


        List<MedicineEntity> medicines =
                Arrays.asList(
                        medicine(
                                "med-1",
                                "MED001",
                                "Medicine A",
                                "Antibiotics",
                                50
                        ),

                        medicine(
                                "med-2",
                                "MED002",
                                "Medicine B",
                                "Antibiotics",
                                50
                        ),

                        medicine(
                                "med-3",
                                "MED003",
                                "Medicine C",
                                "Analgesics & Antipyretics",
                                50
                        ),

                        medicine(
                                "med-4",
                                "MED004",
                                "Medicine D",
                                "Uncategorised",
                                10
                        )
                );


        List<BatchEntity> batches =
                Arrays.asList(
                        batch(
                                "batch-2",
                                "med-2",
                                20,
                                today.plusDays(
                                        10
                                )
                        ),

                        batch(
                                "batch-3a",
                                "med-3",
                                100,
                                today.plusDays(
                                        20
                                )
                        ),

                        batch(
                                "batch-3b",
                                "med-3",
                                30,
                                today.minusDays(
                                        1
                                )
                        ),

                        batch(
                                "batch-4",
                                "med-4",
                                40,
                                today.plusDays(
                                        95
                                )
                        )
                );


        long movementStart =
                today
                        .minusDays(
                                29
                        )
                        .atStartOfDay(
                                ZoneId.systemDefault()
                        )
                        .toInstant()
                        .toEpochMilli();


        long movementEnd =
                today
                        .plusDays(
                                1
                        )
                        .atStartOfDay(
                                ZoneId.systemDefault()
                        )
                        .toInstant()
                        .toEpochMilli();


        AnalyticsReport report =
                service.buildReport(
                        medicines,
                        batches,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        30,
                        nowMillis,
                        movementStart,
                        movementEnd
                );


        assertNotNull(
                report
        );


        assertEquals(
                4,
                report
                        .getSummary()
                        .getActiveMedicineCount()
        );


        assertEquals(
                160L,
                report
                        .getSummary()
                        .getTotalUsableStock()
        );


        assertEquals(
                2,
                report
                        .getSummary()
                        .getInStockMedicineCount()
        );


        assertEquals(
                1,
                report
                        .getSummary()
                        .getLowStockMedicineCount()
        );


        assertEquals(
                1,
                report
                        .getSummary()
                        .getOutOfStockMedicineCount()
        );


        assertEquals(
                120L,
                report
                        .getSummary()
                        .getExpiringWithin30DaysQuantity()
        );


        assertEquals(
                30L,
                report
                        .getSummary()
                        .getExpiredStockQuantity()
        );


        assertEquals(
                2L,
                distributionValue(
                        report.getCategoryDistribution(),
                        "Antibiotics"
                )
        );


        assertEquals(
                20L,
                distributionValue(
                        report.getExpiryDistribution(),
                        "0-14 days"
                )
        );


        assertEquals(
                100L,
                distributionValue(
                        report.getExpiryDistribution(),
                        "15-30 days"
                )
        );


        assertEquals(
                0L,
                distributionValue(
                        report.getExpiryDistribution(),
                        "31-90 days"
                )
        );


        assertEquals(
                40L,
                distributionValue(
                        report.getExpiryDistribution(),
                        "90+ days"
                )
        );
    }


    @Test

    public void buildReport_calculatesOpenAlertDistribution()
    {
        LocalDate today =
                LocalDate.of(
                        2026,
                        10,
                        7
                );


        long nowMillis =
                atNoon(
                        today
                );


        MedicineEntity medicine =
                medicine(
                        "med-1",
                        "MED001",
                        "Medicine A",
                        "Antibiotics",
                        50
                );


        List<AlertEntity> alerts =
                Arrays.asList(
                        new AlertEntity(
                                "alert-1",
                                AlertType.LOW_STOCK,
                                "med-1",
                                null,
                                AlertSeverity.HIGH,
                                AlertStatus.ACTIVE,
                                "Low stock",
                                nowMillis,
                                nowMillis
                        ),

                        new AlertEntity(
                                "alert-2",
                                AlertType.NEAR_EXPIRY,
                                "med-1",
                                "batch-1",
                                AlertSeverity.MEDIUM,
                                AlertStatus.ACKNOWLEDGED,
                                "Near expiry",
                                nowMillis,
                                nowMillis
                        ),

                        new AlertEntity(
                                "alert-3",
                                AlertType.EXPIRED,
                                "med-1",
                                "batch-2",
                                AlertSeverity.HIGH,
                                AlertStatus.ACTIVE,
                                "Expired stock",
                                nowMillis,
                                nowMillis
                        )
                );


        long movementStart =
                dayStart(
                        today.minusDays(
                                29
                        )
                );


        long movementEnd =
                dayStart(
                        today.plusDays(
                                1
                        )
                );


        AnalyticsReport report =
                service.buildReport(
                        Collections.singletonList(
                                medicine
                        ),
                        Collections.emptyList(),
                        alerts,
                        Collections.emptyList(),
                        30,
                        nowMillis,
                        movementStart,
                        movementEnd
                );


        assertEquals(
                3,
                report
                        .getSummary()
                        .getOpenAlertCount()
        );


        assertEquals(
                2,
                report
                        .getSummary()
                        .getActiveAlertCount()
        );


        assertEquals(
                1L,
                distributionValue(
                        report.getAlertDistribution(),
                        "Low Stock"
                )
        );


        assertEquals(
                1L,
                distributionValue(
                        report.getAlertDistribution(),
                        "Near Expiry"
                )
        );


        assertEquals(
                1L,
                distributionValue(
                        report.getAlertDistribution(),
                        "Expired"
                )
        );
    }


    @Test

    public void buildReport_calculatesMovementAnalytics()
    {
        LocalDate today =
                LocalDate.of(
                        2026,
                        10,
                        7
                );


        long nowMillis =
                atNoon(
                        today
                );


        List<MedicineEntity> medicines =
                Arrays.asList(
                        medicine(
                                "med-1",
                                "MED001",
                                "Medicine A",
                                "Antibiotics",
                                50
                        ),

                        medicine(
                                "med-2",
                                "MED002",
                                "Medicine B",
                                "Analgesics & Antipyretics",
                                50
                        )
                );


        List<InventoryTransactionEntity>
                transactions =
                new ArrayList<>();


        transactions.add(
                transaction(
                        "tx-1",
                        "op-1",
                        "med-1",
                        "batch-1",
                        "RECEIPT",
                        200,
                        today.minusDays(
                                2
                        )
                )
        );


        transactions.add(
                transaction(
                        "tx-2",
                        "op-2",
                        "med-1",
                        "batch-1",
                        "ISSUE",
                        -50,
                        today.minusDays(
                                1
                        )
                )
        );


        transactions.add(
                transaction(
                        "tx-3",
                        "op-3",
                        "med-2",
                        "batch-2",
                        "ISSUE",
                        -30,
                        today
                )
        );


        transactions.add(
                transaction(
                        "tx-4",
                        "op-4",
                        "med-1",
                        "batch-1",
                        "ADJUSTMENT_IN",
                        10,
                        today
                )
        );


        transactions.add(
                transaction(
                        "tx-5",
                        "op-5",
                        "med-1",
                        "batch-1",
                        "ADJUSTMENT_OUT",
                        -4,
                        today
                )
        );


        transactions.add(
                transaction(
                        "tx-6",
                        "op-6",
                        "med-1",
                        "batch-1",
                        "CORRECTION_OUT",
                        -5,
                        today
                )
        );


        long movementStart =
                dayStart(
                        today.minusDays(
                                29
                        )
                );


        long movementEnd =
                dayStart(
                        today.plusDays(
                                1
                        )
                );


        AnalyticsReport report =
                service.buildReport(
                        medicines,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        transactions,
                        30,
                        nowMillis,
                        movementStart,
                        movementEnd
                );


        AnalyticsReport.MovementSummary summary =
                report.getMovementSummary();


        assertEquals(
                200L,
                summary.getReceivedQuantity()
        );


        assertEquals(
                80L,
                summary.getIssuedQuantity()
        );


        assertEquals(
                10L,
                summary.getAdjustmentInQuantity()
        );


        assertEquals(
                4L,
                summary.getAdjustmentOutQuantity()
        );


        assertEquals(
                5L,
                summary.getCorrectionOutQuantity()
        );


        assertEquals(
                14L,
                distributionValue(
                        report.getMovementTypeDistribution(),
                        "Adjustments"
                )
        );


        assertEquals(
                5L,
                distributionValue(
                        report.getMovementTypeDistribution(),
                        "Corrections"
                )
        );


        assertEquals(
                30,
                report
                        .getMovementTrend()
                        .size()
        );


        assertEquals(
                2,
                report
                        .getTopIssuedMedicines()
                        .size()
        );


        assertEquals(
                "med-1",
                report
                        .getTopIssuedMedicines()
                        .get(0)
                        .getMedicineId()
        );


        assertEquals(
                50L,
                report
                        .getTopIssuedMedicines()
                        .get(0)
                        .getQuantityIssued()
        );
    }



    private MedicineEntity medicine(
            String medicineId,
            String code,
            String name,
            String category,
            int reorderLevel)
    {
        return new MedicineEntity(
                medicineId,
                code,
                name,
                name,
                category,
                "500 mg",
                "Tablet",
                "Tablets",
                reorderLevel,
                100,
                true,
                0L,
                0L
        );
    }



    private BatchEntity batch(
            String batchId,
            String medicineId,
            int quantity,
            LocalDate expiryDate)
    {
        return new BatchEntity(
                batchId,
                medicineId,
                "LOT-" + batchId,
                dayStart(
                        expiryDate.minusDays(
                                30
                        )
                ),
                dayStart(
                        expiryDate
                ),
                quantity,
                quantity,
                BatchStatus.ACTIVE,
                0L
        );
    }



    private InventoryTransactionEntity transaction(
            String transactionId,
            String operationId,
            String medicineId,
            String batchId,
            String transactionType,
            int quantityChange,
            LocalDate date)
    {
        long createdAt =
                atNoon(
                        date
                );


        return new InventoryTransactionEntity(
                transactionId,
                operationId,
                medicineId,
                batchId,
                transactionType,
                quantityChange,
                null,
                "user-1",
                createdAt,
                createdAt,
                false
        );
    }



    private long distributionValue(
            List<AnalyticsReport.DistributionItem> items,
            String label)
    {
        for (AnalyticsReport.DistributionItem item :
                items)
        {
            if (label.equals(
                    item.getLabel()
            ))
            {
                return item.getValue();
            }
        }


        throw new AssertionError(
                "Distribution item not found: "
                        +
                        label
        );
    }



    private long atNoon(
            LocalDate date)
    {
        return date
                .atTime(
                        12,
                        0
                )
                .atZone(
                        ZoneId.systemDefault()
                )
                .toInstant()
                .toEpochMilli();
    }



    private long dayStart(
            LocalDate date)
    {
        return date
                .atStartOfDay(
                        ZoneId.systemDefault()
                )
                .toInstant()
                .toEpochMilli();
    }
}