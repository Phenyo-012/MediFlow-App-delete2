package com.medicationinventory.model.domain;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;




/**
 * Snapshot used by analytics screens to display the counts already assigned to
 * valid and invalid medicine classifications. It carries separate ABC and VED
 * distributions plus combined pair counts; classification decisions themselves
 * are made elsewhere. Collection inputs are defensively copied and exposed as
 * unmodifiable values so a displayed report cannot change after construction.
 */
public final class AbcVedAnalytics
{
    private final int classifiedMedicineCount;

    private final int invalidClassificationCount;

    private final List<AnalyticsReport.DistributionItem>
            abcDistribution;

    private final List<AnalyticsReport.DistributionItem>
            vedDistribution;

    private final Map<String, Long>
            combinedCounts;



    public AbcVedAnalytics(
            int classifiedMedicineCount,
            int invalidClassificationCount,
            List<AnalyticsReport.DistributionItem> abcDistribution,
            List<AnalyticsReport.DistributionItem> vedDistribution,
            Map<String, Long> combinedCounts)
    {
        this.classifiedMedicineCount =
                classifiedMedicineCount;

        this.invalidClassificationCount =
                invalidClassificationCount;

        this.abcDistribution =
                immutableList(
                        abcDistribution
                );

        this.vedDistribution =
                immutableList(
                        vedDistribution
                );

        this.combinedCounts =
                combinedCounts == null
                        ? Collections.emptyMap()
                        : Collections.unmodifiableMap(
                        new HashMap<>(
                                combinedCounts
                        )
                );
    }


    private static <T> List<T> immutableList(
            List<T> source)
    {
        if (source == null
                ||
                source.isEmpty())
        {
            return Collections.emptyList();
        }


        return Collections.unmodifiableList(
                new ArrayList<>(
                        source
                )
        );
    }


    public int getClassifiedMedicineCount()
    {
        return classifiedMedicineCount;
    }


    public int getInvalidClassificationCount()
    {
        return invalidClassificationCount;
    }


    public List<AnalyticsReport.DistributionItem>
    getAbcDistribution()
    {
        return abcDistribution;
    }


    public List<AnalyticsReport.DistributionItem>
    getVedDistribution()
    {
        return vedDistribution;
    }


    public long getCombinedCount(
            String classification)
    {
        if (classification == null)
        {
            return 0L;
        }


        Long value =
                combinedCounts.get(
                        classification
                                .trim()
                                .toUpperCase(Locale.ROOT)
                );


        return value == null
                ? 0L
                : value;
    }
}