package com.medicationinventory.data.local.model;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;



/**
 * A report row combining alert state and timestamps with the related medicine and batch labels.
 */
public class AlertReportRecord
{
    public String alertId;

    public AlertType alertType;

    public String medicineId;

    public String medicineName;

    public String medicineCode;

    public String batchId;

    public String batchNumber;

    public AlertSeverity severity;

    public AlertStatus status;

    public String message;

    public long createdAt;

    public long updatedAt;
}