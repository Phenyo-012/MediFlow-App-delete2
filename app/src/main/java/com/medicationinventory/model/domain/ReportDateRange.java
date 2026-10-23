package com.medicationinventory.model.domain;



/**
 * Represents Report Date Range data used by inventory rules and application workflows.
 */
public class ReportDateRange
{
    private final long startInclusive;
    private final long endExclusive;



    public ReportDateRange(
            long startInclusive,
            long endExclusive)
    {
        if (startInclusive < 0)
        {
            throw new IllegalArgumentException(
                    "Report start time cannot be negative."
            );
        }

        if (endExclusive <= startInclusive)
        {
            throw new IllegalArgumentException(
                    "Report end time must be after the start time."
            );
        }


        this.startInclusive =
                startInclusive;

        this.endExclusive =
                endExclusive;
    }


    public long getStartInclusive()
    {
        return startInclusive;
    }


    public long getEndExclusive()
    {
        return endExclusive;
    }
}