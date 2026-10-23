package com.medicationinventory.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.medicationinventory.model.domain.AlertCondition;
import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.BatchStatus;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;



/**
 * Covers low-stock, near-expiry, and expired alert thresholds, overlapping conditions, excluded stock, and invalid input rejection.
 */
public class AlertEvaluationServiceTest
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private AlertEvaluationService service;

    private Medicine medicine;


    @Before
    public void setUp()
    {
        ReorderRecommendationService
                reorderRecommendationService =
                new ReorderRecommendationService();


        service =
                new AlertEvaluationService(
                        reorderRecommendationService
                );


        medicine =
                createMedicine(
                        50,
                        100
                );
    }


    /*
     * LOW STOCK
     */

    @Test

    public void lowStock_belowReorderLevelCreatesMediumAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                40,
                                60,
                                BatchStatus.ACTIVE
                        )
                );


        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        batches,
                        30
                );


        AlertCondition lowStock =
                findAlert(
                        alerts,
                        AlertType.LOW_STOCK
                );


        assertNotNull(
                lowStock
        );


        assertEquals(
                AlertSeverity.MEDIUM,
                lowStock.getSeverity()
        );


        assertEquals(
                40,
                lowStock.getUsableStock()
        );


        assertEquals(
                50,
                lowStock.getReorderLevel()
        );


        assertEquals(
                100,
                lowStock.getTargetStockLevel()
        );


        assertEquals(
                60,
                lowStock.getRecommendedQuantity()
        );
    }


    @Test

    public void lowStock_equalToReorderLevelStillCreatesAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                50,
                                60,
                                BatchStatus.ACTIVE
                        )
                );


        AlertCondition lowStock =
                findAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.LOW_STOCK
                );


        assertNotNull(
                lowStock
        );


        assertEquals(
                50,
                lowStock.getRecommendedQuantity()
        );
    }


    @Test

    public void lowStock_zeroUsableStockCreatesHighAlert()
    {
        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        Collections.emptyList(),
                        30
                );


        AlertCondition lowStock =
                findAlert(
                        alerts,
                        AlertType.LOW_STOCK
                );


        assertNotNull(
                lowStock
        );


        assertEquals(
                AlertSeverity.HIGH,
                lowStock.getSeverity()
        );


        assertEquals(
                0,
                lowStock.getUsableStock()
        );


        assertEquals(
                100,
                lowStock.getRecommendedQuantity()
        );
    }


    @Test

    public void lowStock_aboveReorderLevelDoesNotCreateAlert()
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


        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        batches,
                        30
                );


        assertFalse(
                containsAlert(
                        alerts,
                        AlertType.LOW_STOCK
                )
        );
    }


    /*
     * NEAR EXPIRY
     */

    @Test

    public void nearExpiry_batchInsideWarningPeriodCreatesMediumAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                80,
                                10,
                                BatchStatus.ACTIVE
                        )
                );


        AlertCondition nearExpiry =
                findAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.NEAR_EXPIRY
                );


        assertNotNull(
                nearExpiry
        );


        assertEquals(
                AlertSeverity.MEDIUM,
                nearExpiry.getSeverity()
        );


        assertEquals(
                "B001",
                nearExpiry.getBatchId()
        );


        assertEquals(
                80,
                nearExpiry.getBatchQuantity()
        );
    }


    @Test

    public void nearExpiry_exactlyOnWarningBoundaryCreatesAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                80,
                                30,
                                BatchStatus.ACTIVE
                        )
                );


        assertTrue(
                containsAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.NEAR_EXPIRY
                )
        );
    }


    @Test

    public void nearExpiry_afterWarningBoundaryDoesNotCreateAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                80,
                                31,
                                BatchStatus.ACTIVE
                        )
                );


        assertFalse(
                containsAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.NEAR_EXPIRY
                )
        );
    }


    @Test

    public void nearExpiry_zeroQuantityDoesNotCreateAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                0,
                                10,
                                BatchStatus.DEPLETED
                        )
                );


        assertFalse(
                containsAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.NEAR_EXPIRY
                )
        );
    }



    @Test

    public void expired_pastExpiryDateCreatesHighAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                25,
                                -1,
                                BatchStatus.ACTIVE
                        )
                );


        AlertCondition expired =
                findAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.EXPIRED
                );


        assertNotNull(
                expired
        );


        assertEquals(
                AlertSeverity.HIGH,
                expired.getSeverity()
        );


        assertEquals(
                "B001",
                expired.getBatchId()
        );


        assertEquals(
                25,
                expired.getBatchQuantity()
        );
    }


    @Test

    public void expired_expiredStatusCreatesHighAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                25,
                                10,
                                BatchStatus.EXPIRED
                        )
                );


        AlertCondition expired =
                findAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.EXPIRED
                );


        assertNotNull(
                expired
        );


        assertEquals(
                AlertSeverity.HIGH,
                expired.getSeverity()
        );
    }


    @Test

    public void expired_zeroQuantityDoesNotCreateAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                0,
                                -1,
                                BatchStatus.EXPIRED
                        )
                );


        assertFalse(
                containsAlert(
                        service.evaluateAlerts(
                                medicine,
                                batches,
                                30
                        ),
                        AlertType.EXPIRED
                )
        );
    }




    @Test

    public void expiredBatch_doesNotAlsoCreateNearExpiryAlert()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                25,
                                -1,
                                BatchStatus.EXPIRED
                        )
                );


        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        batches,
                        30
                );


        assertTrue(
                containsAlert(
                        alerts,
                        AlertType.EXPIRED
                )
        );


        assertFalse(
                containsAlert(
                        alerts,
                        AlertType.NEAR_EXPIRY
                )
        );
    }



    @Test

    public void lowStockAndNearExpiry_canExistTogether()
    {
        List<Batch> batches =
                Collections.singletonList(
                        createBatch(
                                "B001",
                                40,
                                10,
                                BatchStatus.ACTIVE
                        )
                );


        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        batches,
                        30
                );


        assertTrue(
                containsAlert(
                        alerts,
                        AlertType.LOW_STOCK
                )
        );


        assertTrue(
                containsAlert(
                        alerts,
                        AlertType.NEAR_EXPIRY
                )
        );
    }


    @Test

    public void expiredStock_isExcludedFromLowStockCalculation()
    {
        List<Batch> batches =
                Arrays.asList(
                        createBatch(
                                "B001",
                                100,
                                -1,
                                BatchStatus.EXPIRED
                        ),
                        createBatch(
                                "B002",
                                20,
                                60,
                                BatchStatus.ACTIVE
                        )
                );


        List<AlertCondition> alerts =
                service.evaluateAlerts(
                        medicine,
                        batches,
                        30
                );


        AlertCondition lowStock =
                findAlert(
                        alerts,
                        AlertType.LOW_STOCK
                );


        assertNotNull(
                lowStock
        );



        assertEquals(
                20,
                lowStock.getUsableStock()
        );


        assertEquals(
                80,
                lowStock.getRecommendedQuantity()
        );


        assertTrue(
                containsAlert(
                        alerts,
                        AlertType.EXPIRED
                )
        );
    }



    @Test(
            expected =
                    IllegalArgumentException.class
    )

    public void evaluateAlerts_nullMedicineRejected()
    {
        service.evaluateAlerts(
                null,
                Collections.emptyList(),
                30
        );
    }


    @Test(
            expected =
                    IllegalArgumentException.class
    )

    public void evaluateAlerts_negativeWarningDaysRejected()
    {
        service.evaluateAlerts(
                medicine,
                Collections.emptyList(),
                -1
        );
    }



    private AlertCondition findAlert(
            List<AlertCondition> alerts,
            AlertType type)
    {
        if (alerts == null)
        {
            return null;
        }


        for (AlertCondition alert :
                alerts)
        {
            if (alert != null
                    &&
                    alert.getAlertType() == type)
            {
                return alert;
            }
        }


        return null;
    }



    private boolean containsAlert(
            List<AlertCondition> alerts,
            AlertType type)
    {
        return findAlert(
                alerts,
                type
        ) != null;
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