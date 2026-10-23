package com.medicationinventory.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.enums.BatchStatus;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks usable and near-expiry stock rules, reorder thresholds and quantities, recommendation consistency, and invalid inputs.
 */
public class ReorderRecommendationServiceTest
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private ReorderRecommendationService service;

    private Medicine medicine;


    @Before
    public void setUp()
    {
        service =
                new ReorderRecommendationService();


        /*
         * reorderLevel = 50
         * targetStockLevel = 100
         */
        medicine =
                createMedicine(
                        50,
                        100
                );
    }




    @Test
    public void calculateUsableStock_sumsPositiveNonExpiredBatches()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                30,
                                30,
                                BatchStatus.ACTIVE
                        ),
                        createBatch(
                                "B002",
                                20,
                                60,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateUsableStock(
                        batches
                );


        assertEquals(
                50,
                result
        );
    }


    @Test
    public void calculateUsableStock_excludesPastExpiryEvenIfStatusActive()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                40,
                                -1,
                                BatchStatus.ACTIVE
                        ),
                        createBatch(
                                "B002",
                                25,
                                20,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateUsableStock(
                        batches
                );


        assertEquals(
                25,
                result
        );
    }


    @Test
    public void calculateUsableStock_excludesExpiredStatusEvenWithFutureDate()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                40,
                                30,
                                BatchStatus.EXPIRED
                        ),
                        createBatch(
                                "B002",
                                15,
                                30,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateUsableStock(
                        batches
                );


        assertEquals(
                15,
                result
        );
    }


    @Test
    public void calculateUsableStock_excludesZeroQuantity()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                0,
                                30,
                                BatchStatus.ACTIVE
                        ),
                        createBatch(
                                "B002",
                                12,
                                30,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateUsableStock(
                        batches
                );


        assertEquals(
                12,
                result
        );
    }


    @Test
    public void calculateUsableStock_emptyListReturnsZero()
    {
        assertEquals(
                0,
                service.calculateUsableStock(
                        Collections.emptyList()
                )
        );
    }


    @Test
    public void calculateUsableStock_nullListReturnsZero()
    {
        assertEquals(
                0,
                service.calculateUsableStock(
                        null
                )
        );
    }


    @Test
    public void isReorderRequired_belowReorderLevelReturnsTrue()
    {
        assertTrue(
                service.isReorderRequired(
                        medicine,
                        49
                )
        );
    }


    @Test
    public void isReorderRequired_equalToReorderLevelReturnsTrue()
    {

        assertTrue(
                service.isReorderRequired(
                        medicine,
                        50
                )
        );
    }


    @Test
    public void isReorderRequired_aboveReorderLevelReturnsFalse()
    {
        assertFalse(
                service.isReorderRequired(
                        medicine,
                        51
                )
        );
    }


    @Test
    public void isReorderRequired_zeroStockReturnsTrue()
    {
        assertTrue(
                service.isReorderRequired(
                        medicine,
                        0
                )
        );
    }


    /*
     * RECOMMENDED QUANTITY
     */

    @Test
    public void calculateRecommendedQuantity_whenReorderRequiredReturnsTargetGap()
    {
        int result =
                service.calculateRecommendedQuantity(
                        medicine,
                        40
                );


        assertEquals(
                60,
                result
        );
    }


    @Test

    public void calculateRecommendedQuantity_atReorderLevelReturnsTargetGap()
    {
        int result =
                service.calculateRecommendedQuantity(
                        medicine,
                        50
                );


        assertEquals(
                50,
                result
        );
    }


    @Test

    public void calculateRecommendedQuantity_aboveReorderLevelReturnsZero()
    {
        int result =
                service.calculateRecommendedQuantity(
                        medicine,
                        80
                );


        assertEquals(
                0,
                result
        );
    }


    @Test

    public void calculateRecommendedQuantity_zeroStockReturnsTargetStock()
    {
        int result =
                service.calculateRecommendedQuantity(
                        medicine,
                        0
                );


        assertEquals(
                100,
                result
        );
    }


    /*
     * NEAR EXPIRY
     */

    @Test

    public void calculateNearExpiryStock_countsUsableStockWithinWarningPeriod()
    {
        List<Batch> batches =
                Arrays.asList(
                        /*
                         * Inside 30-day warning period.
                         */
                        createBatch(
                                "B001",
                                20,
                                10,
                                BatchStatus.ACTIVE
                        ),

                        /*
                         * Exactly on warning boundary.
                         */
                        createBatch(
                                "B002",
                                15,
                                30,
                                BatchStatus.ACTIVE
                        ),

                        /*
                         * Outside warning period.
                         */
                        createBatch(
                                "B003",
                                50,
                                31,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateNearExpiryStock(
                        batches,
                        30
                );


        assertEquals(
                35,
                result
        );
    }


    @Test

    public void calculateNearExpiryStock_expiredBatchIsNotCounted()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                25,
                                -1,
                                BatchStatus.EXPIRED
                        ),
                        createBatch(
                                "B002",
                                10,
                                5,
                                BatchStatus.ACTIVE
                        )
                );


        int result =
                service.calculateNearExpiryStock(
                        batches,
                        30
                );


        assertEquals(
                10,
                result
        );
    }


    @Test

    public void calculateNearExpiryStock_stillContributesToUsableStock()
    {
        Batch nearExpiryBatch =
                createBatch(
                        "B001",
                        25,
                        5,
                        BatchStatus.ACTIVE
                );


        List<Batch> batches =
                Collections.singletonList(
                        nearExpiryBatch
                );


        assertEquals(
                25,
                service.calculateUsableStock(
                        batches
                )
        );


        assertEquals(
                25,
                service.calculateNearExpiryStock(
                        batches,
                        30
                )
        );
    }



    @Test

    public void buildReorderRecommendation_buildsConsistentResult()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                20,
                                5,
                                BatchStatus.ACTIVE
                        ),
                        createBatch(
                                "B002",
                                20,
                                60,
                                BatchStatus.ACTIVE
                        ),
                        createBatch(
                                "B003",
                                100,
                                -5,
                                BatchStatus.EXPIRED
                        )
                );


        ReorderRecommendation recommendation =
                service.buildReorderRecommendation(
                        medicine,
                        batches,
                        30
                );


        assertEquals(
                "medicine-1",
                recommendation.getMedicineId()
        );


        assertEquals(
                40,
                recommendation.getUsableStock()
        );


        assertEquals(
                20,
                recommendation.getNearExpiryStock()
        );


        assertEquals(
                50,
                recommendation.getReorderLevel()
        );


        assertEquals(
                100,
                recommendation.getTargetStockLevel()
        );


        assertTrue(
                recommendation.isReorderRequired()
        );


        assertEquals(
                60,
                recommendation.getRecommendedQuantity()
        );
    }


    @Test
    public void buildReorderRecommendation_aboveThresholdHasZeroRecommendation()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                70,
                                60,
                                BatchStatus.ACTIVE
                        )
                );


        ReorderRecommendation recommendation =
                service.buildReorderRecommendation(
                        medicine,
                        batches,
                        30
                );


        assertEquals(
                70,
                recommendation.getUsableStock()
        );


        assertFalse(
                recommendation.isReorderRequired()
        );


        assertEquals(
                0,
                recommendation.getRecommendedQuantity()
        );
    }


    /*
     * INVALID CONFIGURATIONF
     */

    @Test(
            expected =
                    IllegalArgumentException.class
    )

    public void invalidMedicine_negativeReorderLevelRejected()
    {
        Medicine invalidMedicine =
                createMedicine(
                        -1,
                        100
                );


        service.isReorderRequired(
                invalidMedicine,
                20
        );
    }


    @Test(
            expected =
                    IllegalArgumentException.class
    )

    public void invalidMedicine_targetBelowReorderLevelRejected()
    {
        Medicine invalidMedicine =
                createMedicine(
                        50,
                        40
                );


        service.calculateRecommendedQuantity(
                invalidMedicine,
                20
        );
    }


    @Test(
            expected =
                    IllegalArgumentException.class
    )

    public void calculateNearExpiryStock_negativeWarningDaysRejected()
    {
        service.calculateNearExpiryStock(
                new ArrayList<>(),
                -1
        );
    }



    private Medicine createMedicine(
            int reorderLevel,
            int targetStockLevel)
    {
        return new Medicine(
                "medicine-1",
                "MED-001",
                "Paracetamol",
                "Paracetamol",
                "500 mg",
                "Tablet",
                "tablet",
                reorderLevel,
                targetStockLevel,
                true,
                System.currentTimeMillis(),
                1L
        );
    }



    private Batch createBatch(
            String batchId,
            int quantity,
            int daysUntilExpiry,
            BatchStatus status)
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        long receivedDate =
                today
                        .minusDays(
                                30
                        )
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        long expiryDate =
                today
                        .plusDays(
                                daysUntilExpiry
                        )
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        return new Batch(
                batchId,
                "medicine-1",
                batchId,
                receivedDate,
                expiryDate,
                quantity,
                quantity,
                status,
                System.currentTimeMillis()
        );
    }
}