package com.medicationinventory.service;

import com.medicationinventory.model.domain.ReportDateRange;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;



/**
 * Verifies inclusive custom date ranges and rejects reversed ranges or missing endpoints.
 */
public class ReportDateRangeServiceTest
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final ReportDateRangeService service =
            new ReportDateRangeService();


    @Test

    public void customSingleDayIncludesWholeDay()
    {
        LocalDate date =
                LocalDate.of(
                        2026,
                        10,
                        1
                );


        ReportDateRange range =
                service.fromDates(
                        date,
                        date
                );


        LocalDate start =
                Instant
                        .ofEpochMilli(
                                range.getStartInclusive()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .toLocalDate();


        LocalDate endExclusive =
                Instant
                        .ofEpochMilli(
                                range.getEndExclusive()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .toLocalDate();


        assertEquals(
                date,
                start
        );


        assertEquals(
                date.plusDays(
                        1
                ),
                endExclusive
        );
    }


    @Test

    public void customRangeIncludesSelectedEndDate()
    {
        LocalDate start =
                LocalDate.of(
                        2026,
                        9,
                        1
                );

        LocalDate end =
                LocalDate.of(
                        2026,
                        9,
                        30
                );


        ReportDateRange range =
                service.fromDates(
                        start,
                        end
                );


        long expectedStart =
                start
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        long expectedEndExclusive =
                end
                        .plusDays(
                                1
                        )
                        .atStartOfDay(
                                BUSINESS_ZONE
                        )
                        .toInstant()
                        .toEpochMilli();


        assertEquals(
                expectedStart,
                range.getStartInclusive()
        );


        assertEquals(
                expectedEndExclusive,
                range.getEndExclusive()
        );
    }


    @Test

    public void startAfterEndIsRejected()
    {
        try
        {
            service.fromDates(
                    LocalDate.of(
                            2026,
                            10,
                            5
                    ),
                    LocalDate.of(
                            2026,
                            10,
                            1
                    )
            );


            fail(
                    "Expected IllegalArgumentException."
            );
        }
        catch (IllegalArgumentException exception)
        {
            assertTrue(
                    exception
                            .getMessage()
                            .contains(
                                    "cannot be after"
                            )
            );
        }
    }


    @Test

    public void nullStartDateIsRejected()
    {
        try
        {
            service.fromDates(
                    null,
                    LocalDate.of(
                            2026,
                            10,
                            1
                    )
            );


            fail(
                    "Expected IllegalArgumentException."
            );
        }
        catch (IllegalArgumentException expected)
        {
            // Expected.
        }
    }


    @Test

    public void nullEndDateIsRejected()
    {
        try
        {
            service.fromDates(
                    LocalDate.of(
                            2026,
                            10,
                            1
                    ),
                    null
            );


            fail(
                    "Expected IllegalArgumentException."
            );
        }
        catch (IllegalArgumentException expected)
        {
            // Expected.
        }
    }
}