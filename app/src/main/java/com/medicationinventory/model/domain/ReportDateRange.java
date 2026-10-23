package com.medicationinventory.model.domain;



/**
 * Validated half-open time window for report queries. The start is included and
 * the end is excluded, which lets adjacent date ranges meet without counting a
 * boundary transaction twice. Both values are epoch milliseconds.
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