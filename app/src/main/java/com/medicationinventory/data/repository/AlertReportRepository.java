package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.AlertDao;
import com.medicationinventory.data.local.model.AlertReportRecord;
import com.medicationinventory.data.local.model.AlertReportBatchOption;
import com.medicationinventory.data.local.model.AlertReportMedicineOption;

import com.medicationinventory.model.domain.ReportDateRange;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;

import java.util.List;



/**
 * Queries the local alert DAO for report rows and filter options used by alert reports.
 */
public class AlertReportRepository
{
    private final AlertDao
            alertDao;


    public AlertReportRepository(
            AlertDao alertDao)
    {
        if (alertDao == null)
        {
            throw new IllegalArgumentException(
                    "Alert DAO is required."
            );
        }


        this.alertDao =
                alertDao;
    }


    public LiveData<List<AlertReportRecord>>
    observeReport(
            ReportDateRange dateRange,
            String medicineId,
            String batchId,
            AlertType alertType,
            AlertStatus status)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        String normalizedMedicineId =
                normalizeOptional(
                        medicineId
                );


        String normalizedBatchId =
                normalizeOptional(
                        batchId
                );


        String alertTypeValue =
                alertType == null
                        ? null
                        : alertType.name();


        String statusValue =
                status == null
                        ? null
                        : status.name();


        return alertDao
                .observeAlertReport(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive(),
                        normalizedMedicineId,
                        normalizedBatchId,
                        alertTypeValue,
                        statusValue
                );
    }


    private String normalizeOptional(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim();


        return normalized.isEmpty()
                ? null
                : normalized;
    }

    public LiveData<List<AlertReportMedicineOption>>
    observeMedicineOptions(
            ReportDateRange dateRange)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        return alertDao
                .observeAlertReportMedicineOptions(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive()
                );
    }


    public LiveData<List<AlertReportBatchOption>> observeBatchOptions(ReportDateRange dateRange,
                                                                      String medicineId)
    {
        if (dateRange == null)
        {
            throw new IllegalArgumentException(
                    "Report date range is required."
            );
        }


        return alertDao
                .observeAlertReportBatchOptions(
                        dateRange.getStartInclusive(),
                        dateRange.getEndExclusive(),
                        normalizeOptional(
                                medicineId
                        )
                );
    }
}