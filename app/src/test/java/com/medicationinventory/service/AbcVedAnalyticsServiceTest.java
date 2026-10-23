package com.medicationinventory.service;


import static org.junit.Assert.assertEquals;

import com.medicationinventory.model.domain.AbcVedAnalytics;
import com.medicationinventory.model.domain.AnalyticsReport;
import com.medicationinventory.model.domain.MedicineClassification;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;



/**
 * Checks ABC/VED distribution and matrix calculations and confirms invalid classification pairs are excluded.
 */
public class AbcVedAnalyticsServiceTest
{
    private final AbcVedAnalyticsService service =
            new AbcVedAnalyticsService();


    @Test
    public void build_calculatesAbcVedDistributionsAndMatrix()
    {
        List<MedicineClassification> classifications =
                Arrays.asList(
                        classification(
                                "m1",
                                "A",
                                "V"
                        ),

                        classification(
                                "m2",
                                "A",
                                "E"
                        ),

                        classification(
                                "m3",
                                "B",
                                "E"
                        ),

                        classification(
                                "m4",
                                "C",
                                "D"
                        )
                );


        AbcVedAnalytics analytics =
                service.build(
                        classifications
                );


        assertEquals(
                4,
                analytics
                        .getClassifiedMedicineCount()
        );


        assertEquals(
                2L,
                value(
                        analytics
                                .getAbcDistribution(),
                        "Class A"
                )
        );


        assertEquals(
                1L,
                value(
                        analytics
                                .getAbcDistribution(),
                        "Class B"
                )
        );


        assertEquals(
                1L,
                value(
                        analytics
                                .getAbcDistribution(),
                        "Class C"
                )
        );


        assertEquals(
                1L,
                value(
                        analytics
                                .getVedDistribution(),
                        "Vital (V)"
                )
        );


        assertEquals(
                2L,
                value(
                        analytics
                                .getVedDistribution(),
                        "Essential (E)"
                )
        );


        assertEquals(
                1L,
                value(
                        analytics
                                .getVedDistribution(),
                        "Desirable (D)"
                )
        );


        assertEquals(
                1L,
                analytics.getCombinedCount(
                        "AV"
                )
        );


        assertEquals(
                1L,
                analytics.getCombinedCount(
                        "AE"
                )
        );


        assertEquals(
                1L,
                analytics.getCombinedCount(
                        "BE"
                )
        );


        assertEquals(
                1L,
                analytics.getCombinedCount(
                        "CD"
                )
        );


        assertEquals(
                0L,
                analytics.getCombinedCount(
                        "AD"
                )
        );
    }


    @Test

    public void build_ignoresInvalidClassificationPairs()
    {
        List<MedicineClassification> classifications =
                Arrays.asList(
                        classification(
                                "m1",
                                "A",
                                "V"
                        ),

                        classification(
                                "m2",
                                "X",
                                "E"
                        ),

                        classification(
                                "m3",
                                "B",
                                "Z"
                        )
                );


        AbcVedAnalytics analytics =
                service.build(
                        classifications
                );


        assertEquals(
                1,
                analytics
                        .getClassifiedMedicineCount()
        );


        assertEquals(
                2,
                analytics
                        .getInvalidClassificationCount()
        );
    }



    private MedicineClassification classification(
            String medicineId,
            String abc,
            String ved)
    {
        return new MedicineClassification(
                medicineId,
                abc,
                ved,
                1L
        );
    }



    private long value(
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
}