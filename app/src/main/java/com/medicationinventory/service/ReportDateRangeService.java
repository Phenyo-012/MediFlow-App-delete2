package com.medicationinventory.service;

import com.medicationinventory.model.domain.ReportDateRange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Creates inclusive report ranges using the Africa/Johannesburg calendar. Keeping date selection
 * here gives report screens a shared definition of “today”, rolling periods, and calendar months,
 * while the returned range stores epoch-millisecond boundaries for Room queries. Calendar-based
 * periods are calculated in the business zone before conversion, avoiding UTC midnight shifts.
 */
public class ReportDateRangeService
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    public ReportDateRange today()
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        return fromDates(
                today,
                today
        );
    }


    public ReportDateRange yesterday()
    {
        LocalDate yesterday =
                LocalDate
                        .now(
                                BUSINESS_ZONE
                        )
                        .minusDays(
                                1
                        );


        return fromDates(
                yesterday,
                yesterday
        );
    }


    public ReportDateRange last7Days()
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        return fromDates(
                today.minusDays(
                        6
                ),
                today
        );
    }


    public ReportDateRange last30Days()
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        return fromDates(
                today.minusDays(
                        29
                ),
                today
        );
    }


    public ReportDateRange thisMonth()
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        return fromDates(
                today.withDayOfMonth(
                        1
                ),
                today
        );
    }


    public ReportDateRange previousMonth()
    {
        YearMonth previousMonth =
                YearMonth
                        .now(
                                BUSINESS_ZONE
                        )
                        .minusMonths(
                                1
                        );


        return fromDates(
                previousMonth.atDay(
                        1
                ),
                previousMonth.atEndOfMonth()
        );
    }


    /**
     * Converts a user-selected inclusive date span to the millisecond range consumed by report
     * repositories. Both boundaries use local start-of-day in the business zone; callers must
     * provide ordered, non-null dates.
     *
     * @param startDate first included calendar date
     * @param endDate last included calendar date
     * @return range spanning both selected dates
     */
    public ReportDateRange fromDates(
            LocalDate startDate,
            LocalDate endDate)
    {
        if (startDate == null
                ||
                endDate == null)
        {
            throw new IllegalArgumentException(
                    "Report start and end dates are required."
            );
        }


        if (startDate.isAfter(
                endDate
        ))
        {
            throw new IllegalArgumentException(
                    "Report start date cannot be after the end date."
            );
        }


        long startInclusive =
                startDate
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        long endExclusive =
                endDate
                        .plusDays(
                                1
                        )
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        return new ReportDateRange(
                startInclusive,
                endExclusive
        );
    }
}