package com.medicationinventory.data.local.model;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;


/**
 * A joined alert row for the live alert list, including medicine and batch labels for display.
 */
public class AlertListRecord
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