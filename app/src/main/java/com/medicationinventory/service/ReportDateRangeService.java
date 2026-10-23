package com.medicationinventory.service;

import com.medicationinventory.model.domain.ReportDateRange;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Applies Report Date Range Service business rules used by inventory operations and reports.
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