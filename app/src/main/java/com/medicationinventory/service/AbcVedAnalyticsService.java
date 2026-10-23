package com.medicationinventory.service;


import com.medicationinventory.model.domain.AbcVedAnalytics;
import com.medicationinventory.model.domain.AnalyticsReport;
import com.medicationinventory.model.domain.MedicineClassification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;




/**
 * Builds the ABC, VED, and combined nine-cell distributions used by analytics reports. This class
 * counts classifications that have already been assigned; it does not derive ABC classes from
 * usage or VED classes from clinical data. Null records and pairs outside A/B/C plus V/E/D are
 * omitted from the distributions and included in the invalid-record count.
 */
public class AbcVedAnalyticsService
{

    /**
     * Aggregates valid classification pairs after trimming and case normalization. Each accepted
     * record increments exactly one ABC total, one VED total, and one matrix cell, keeping the
     * three views of the same input consistent.
     *
     * @param classifications local classification records; {@code null} is treated as empty input
     * @return report distributions, an initialized nine-cell matrix, and valid/invalid counts
     */
    public AbcVedAnalytics build(
            List<MedicineClassification> classifications)
    {
        long classA =
                0L;

        long classB =
                0L;

        long classC =
                0L;


        long vital =
                0L;

        long essential =
                0L;

        long desirable =
                0L;


        int validCount =
                0;

        int invalidCount =
                0;


        Map<String, Long> combined =
                createEmptyMatrix();


        if (classifications != null)
        {
            for (MedicineClassification classification :
                    classifications)
            {
                if (classification == null)
                {
                    continue;
                }


                String abc =
                        normalizeAbc(
                                classification
                                        .getAbcClassification()
                        );


                String ved =
                        normalizeVed(
                                classification
                                        .getVedClassification()
                        );


                /*
                 * Only a valid ABC + VED pair belongs in
                 * the analytics distributions and matrix.
                 */
                if (abc == null
                        ||
                        ved == null)
                {
                    invalidCount++;

                    continue;
                }


                validCount++;


                switch (abc)
                {
                    case "A":

                        classA++;

                        break;


                    case "B":

                        classB++;

                        break;


                    case "C":

                        classC++;

                        break;
                }


                switch (ved)
                {
                    case "V":

                        vital++;

                        break;


                    case "E":

                        essential++;

                        break;


                    case "D":

                        desirable++;

                        break;
                }


                String combinedKey =
                        abc
                                +
                                ved;


                combined.put(
                        combinedKey,
                        combined.getOrDefault(
                                combinedKey,
                                0L
                        )
                                +
                                1L
                );
            }
        }


        List<AnalyticsReport.DistributionItem>
                abcDistribution =
                new ArrayList<>();


        abcDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Class A",
                        classA
                )
        );


        abcDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Class B",
                        classB
                )
        );


        abcDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Class C",
                        classC
                )
        );


        List<AnalyticsReport.DistributionItem>
                vedDistribution =
                new ArrayList<>();


        vedDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Vital (V)",
                        vital
                )
        );


        vedDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Essential (E)",
                        essential
                )
        );


        vedDistribution.add(
                new AnalyticsReport.DistributionItem(
                        "Desirable (D)",
                        desirable
                )
        );


        return new AbcVedAnalytics(
                validCount,
                invalidCount,
                abcDistribution,
                vedDistribution,
                combined
        );
    }


    private Map<String, Long>
    createEmptyMatrix()
    {
        Map<String, Long> values =
                new HashMap<>();


        values.put(
                "AV",
                0L
        );

        values.put(
                "AE",
                0L
        );

        values.put(
                "AD",
                0L
        );


        values.put(
                "BV",
                0L
        );

        values.put(
                "BE",
                0L
        );

        values.put(
                "BD",
                0L
        );


        values.put(
                "CV",
                0L
        );

        values.put(
                "CE",
                0L
        );

        values.put(
                "CD",
                0L
        );


        return values;
    }


    private String normalizeAbc(
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


        if ("A".equals(
                normalized
        )
                ||
                "B".equals(
                        normalized
                )
                ||
                "C".equals(
                        normalized
                ))
        {
            return normalized;
        }


        return null;
    }



    private String normalizeVed(
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


        if ("V".equals(
                normalized
        )
                ||
                "E".equals(
                        normalized
                )
                ||
                "D".equals(
                        normalized
                ))
        {
            return normalized;
        }


        return null;
    }
}