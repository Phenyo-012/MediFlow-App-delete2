package com.medicationinventory.service;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.StockLevelReportItem;

import com.medicationinventory.model.enums.BatchStatus;
import com.medicationinventory.model.enums.StockLevelStatus;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Covers stock status boundaries, batch summaries, reorder-only results, inactive medicine exclusion, and invalid warning periods.
 */
public class StockLevelReportServiceTest
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private StockLevelReportService
            reportService;


    @Before
    public void setUp()
    {
        reportService =
                new StockLevelReportService(
                        new ReorderRecommendationService()
                );
    }


    @Test

    public void stockAboveReorderLevelIsNormal()
    {
        Medicine medicine =
                medicine(
                        "MED-1",
                        "Paracetamol",
                        "PAR500",
                        20,
                        100
                );


        List<Batch> batches =
                Collections.singletonList(
                        batch(
                                "BAT-1",
                                "MED-1",
                                50
                        )
                );


        StockLevelReportItem item =
                reportService.buildItem(
                        medicine,
                        batches,
                        90
                );


        assertEquals(
                50,
                item.getTotalAvailableQuantity()
        );


        assertEquals(
                StockLevelStatus.NORMAL,
                item.getStockStatus()
        );


        assertEquals(
                0,
                item.getRecommendedReorderQuantity()
        );
    }


    @Test

    public void stockAtReorderLevelIsLowStock()
    {
        Medicine medicine =
                medicine(
                        "MED-1",
                        "Paracetamol",
                        "PAR500",
                        20,
                        100
                );


        List<Batch> batches =
                Collections.singletonList(
                        batch(
                                "BAT-1",
                                "MED-1",
                                20
                        )
                );


        StockLevelReportItem item =
                reportService.buildItem(
                        medicine,
                        batches,
                        90
                );


        assertEquals(
                StockLevelStatus.LOW_STOCK,
                item.getStockStatus()
        );


        assertEquals(
                80,
                item.getRecommendedReorderQuantity()
        );
    }


    @Test

    public void zeroUsableStockIsOutOfStock()
    {
        Medicine medicine =
                medicine(
                        "MED-1",
                        "Paracetamol",
                        "PAR500",
                        20,
                        100
                );


        StockLevelReportItem item =
                reportService.buildItem(
                        medicine,
                        Collections.emptyList(),
                        90
                );


        assertEquals(
                0,
                item.getTotalAvailableQuantity()
        );


        assertEquals(
                StockLevelStatus.OUT_OF_STOCK,
                item.getStockStatus()
        );


        assertEquals(
                100,
                item.getRecommendedReorderQuantity()
        );
    }


    @Test

    public void batchSummaryCountsMatchingBatches()
    {
        Medicine medicine =
                medicine(
                        "MED-1",
                        "Paracetamol",
                        "PAR500",
                        20,
                        100
                );


        List<Batch> batches =
                new ArrayList<>();


        batches.add(
                batch(
                        "BAT-1",
                        "MED-1",
                        20
                )
        );


        batches.add(
                batch(
                        "BAT-2",
                        "MED-1",
                        0
                )
        );


        /*
         * Must not affect MED-1's summary.
         */
        batches.add(
                batch(
                        "BAT-OTHER",
                        "MED-2",
                        50
                )
        );


        StockLevelReportItem item =
                reportService.buildItem(
                        medicine,
                        batches,
                        90
                );


        assertEquals(
                2,
                item.getBatchCount()
        );


        assertEquals(
                1,
                item.getBatchesWithStock()
        );
    }


    @Test

    public void reorderOnlyExcludesNormalMedicines()
    {
        Medicine normalMedicine =
                medicine(
                        "MED-1",
                        "Normal Medicine",
                        "NORMAL",
                        10,
                        100
                );


        Medicine lowMedicine =
                medicine(
                        "MED-2",
                        "Low Medicine",
                        "LOW",
                        20,
                        100
                );


        Map<String, List<Batch>>
                batchesByMedicine =
                new HashMap<>();


        batchesByMedicine.put(
                "MED-1",
                Collections.singletonList(
                        batch(
                                "BAT-1",
                                "MED-1",
                                50
                        )
                )
        );


        batchesByMedicine.put(
                "MED-2",
                Collections.singletonList(
                        batch(
                                "BAT-2",
                                "MED-2",
                                10
                        )
                )
        );


        List<StockLevelReportItem> result =
                reportService.buildReport(
                        List.of(
                                normalMedicine,
                                lowMedicine
                        ),
                        batchesByMedicine,
                        90,
                        true
                );


        assertEquals(
                1,
                result.size()
        );


        assertEquals(
                "MED-2",
                result
                        .get(0)
                        .getMedicineId()
        );
    }


    @Test

    public void inactiveMedicineIsNotIncluded()
    {
        Medicine inactive =
                new Medicine(
                        "MED-1",
                        "OLD",
                        "Inactive Medicine",
                        null,
                        null,
                        "Tablet",
                        "Tablets",
                        10,
                        100,
                        false,
                        0L,
                        0L
                );


        List<StockLevelReportItem> result =
                reportService.buildReport(
                        Collections.singletonList(
                                inactive
                        ),
                        Collections.emptyMap(),
                        90,
                        false
                );


        assertTrue(
                result.isEmpty()
        );
    }


    @Test(expected = IllegalArgumentException.class)

    public void negativeWarningDaysAreRejected()
    {
        reportService.buildReport(
                Collections.emptyList(),
                Collections.emptyMap(),
                -1,
                false
        );
    }



    private Medicine medicine(
            String medicineId,
            String name,
            String code,
            int reorderLevel,
            int targetStockLevel)
    {
        return new Medicine(
                medicineId,
                code,
                name,
                null,
                null,
                "Tablet",
                "Tablets",
                reorderLevel,
                targetStockLevel,
                true,
                0L,
                0L
        );
    }



    private Batch batch(
            String batchId,
            String medicineId,
            int quantity)
    {
        BatchStatus status =
                quantity > 0
                        ? BatchStatus.ACTIVE
                        : BatchStatus.DEPLETED;


        return new Batch(
                batchId,
                medicineId,
                "BN-" + batchId,
                dateMillis(
                        LocalDate.of(
                                2026,
                                1,
                                1
                        )
                ),
                dateMillis(
                        LocalDate.of(
                                2099,
                                12,
                                31
                        )
                ),
                quantity,
                Math.max(
                        quantity,
                        1
                ),
                status,
                0L
        );
    }


    private long dateMillis(
            LocalDate date)
    {
        return date
                .atStartOfDay(
                        BUSINESS_ZONE
                )
                .toInstant()
                .toEpochMilli();
    }
}