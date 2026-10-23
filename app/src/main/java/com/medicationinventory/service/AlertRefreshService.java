package com.medicationinventory.service;

import com.medicationinventory.data.repository.AlertRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;

import com.medicationinventory.model.domain.AlertCondition;
import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.InventorySettings;

import java.util.Collections;
import java.util.List;


/**
 * Loads the configured near-expiry window, evaluates active medicine batches, and reconciles the resulting alerts.
 */
public class AlertRefreshService
{
    private final AlertEvaluationService alertEvaluationService;

    private final AlertRepository alertRepository;

    private final InventorySettingsRepository inventorySettingsRepository;



    public AlertRefreshService(
            AlertEvaluationService alertEvaluationService,
            AlertRepository alertRepository,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (alertEvaluationService == null
                ||
                alertRepository == null
                ||
                inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Alert refresh dependencies are required."
            );
        }


        this.alertEvaluationService =
                alertEvaluationService;


        this.alertRepository =
                alertRepository;


        this.inventorySettingsRepository =
                inventorySettingsRepository;
    }



    /**
     * Recomputes one medicine alert set from current settings and reconciles it with stored alerts.
     * Inactive medicines use an empty set so stale alerts can be closed.
     * @param medicine medicine to refresh; required
     * @param batches latest batches, or null when none are available
     * @throws IllegalArgumentException when medicine is null
     */
    public void refreshMedicineAlertsBlocking(
            Medicine medicine,
            List<Batch> batches)
    {
        if (medicine == null)
        {
            throw new IllegalArgumentException(
                    "Medicine is required."
            );
        }


        List<AlertCondition> conditions;

        InventorySettings settings =
                inventorySettingsRepository
                        .getCurrentSettingsBlocking();


        int nearExpiryWarningDays =
                settings.getNearExpiryWarningDays();

        if (!medicine.isActive())
        {
            conditions =
                    Collections.emptyList();
        }
        else
        {
            List<Batch> safeBatches =
                    batches == null
                            ? Collections.emptyList()
                            : batches;


            conditions =
                    alertEvaluationService
                            .evaluateAlerts(
                                    medicine,
                                    safeBatches,
                                    nearExpiryWarningDays
                            );
        }


        alertRepository
                .reconcileMedicineAlertsBlocking(
                        medicine.getMedicineId(),
                        conditions
                );
    }
}