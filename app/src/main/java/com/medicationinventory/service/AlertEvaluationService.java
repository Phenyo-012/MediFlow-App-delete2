package com.medicationinventory.service;

import com.medicationinventory.model.domain.AlertCondition;
import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.ReorderRecommendation;

import com.medicationinventory.model.enums.AlertSeverity;
import com.medicationinventory.model.enums.AlertType;
import com.medicationinventory.model.enums.BatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates low-stock, near-expiry, and expired-stock conditions for a medicine using the shared usable-stock and reorder calculations. Low stock is high severity when no usable stock remains and medium otherwise; expiry checks use the Africa/Johannesburg business date.
 */
public class AlertEvaluationService
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final ReorderRecommendationService
            reorderRecommendationService;


    public AlertEvaluationService(
            ReorderRecommendationService
                    reorderRecommendationService)
    {
        if (reorderRecommendationService == null)
        {
            throw new IllegalArgumentException(
                    "ReorderRecommendationService is required."
            );
        }


        this.reorderRecommendationService =
                reorderRecommendationService;
    }

    /**
     * Builds current alert conditions from one medicine and its batch snapshot. Null batches are treated as empty.
     * @param medicine medicine being evaluated
     * @param batches current batches
     * @param nearExpiryWarningDays inclusive expiry warning window; must be non-negative
     * @return conditions to reconcile with stored alert rows
     * @throws IllegalArgumentException when medicine is null or warning days are negative
     */
    public List<AlertCondition> evaluateAlerts(
            Medicine medicine,
            List<Batch> batches,
            int nearExpiryWarningDays)
    {
        if (medicine == null)
        {
            throw new IllegalArgumentException(
                    "Medicine is required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            throw new IllegalArgumentException(
                    "Near-expiry warning days cannot be negative."
            );
        }


        List<Batch> safeBatches =
                batches == null
                        ? Collections.emptyList()
                        : batches;


        List<AlertCondition> alerts =
                new ArrayList<>();


        evaluateLowStock(
                medicine,
                safeBatches,
                nearExpiryWarningDays,
                alerts
        );


        evaluateBatchExpiryAlerts(
                medicine,
                safeBatches,
                nearExpiryWarningDays,
                alerts
        );


        return alerts;
    }


    private void evaluateLowStock(
            Medicine medicine,
            List<Batch> batches,
            int nearExpiryWarningDays,
            List<AlertCondition> alerts)
    {
        ReorderRecommendation recommendation =
                reorderRecommendationService
                        .buildReorderRecommendation(
                                medicine,
                                batches,
                                nearExpiryWarningDays
                        );


        if (!recommendation.isReorderRequired())
        {
            return;
        }


        AlertSeverity severity =
                recommendation.getUsableStock() == 0
                        ? AlertSeverity.HIGH
                        : AlertSeverity.MEDIUM;


        String message =
                "Low stock: "
                        + recommendation.getUsableStock()
                        + " usable units available. "
                        + "Reorder level: "
                        + recommendation.getReorderLevel()
                        + ". Target stock level: "
                        + recommendation.getTargetStockLevel()
                        + ". Recommended quantity: "
                        + recommendation.getRecommendedQuantity()
                        + ".";


        alerts.add(
                new AlertCondition(
                        AlertType.LOW_STOCK,
                        medicine.getMedicineId(),
                        null,
                        severity,
                        message,
                        recommendation.getUsableStock(),
                        recommendation.getReorderLevel(),
                        recommendation.getTargetStockLevel(),
                        recommendation.getRecommendedQuantity(),
                        0,
                        0L
                )
        );
    }


    private void evaluateBatchExpiryAlerts(
            Medicine medicine,
            List<Batch> batches,
            int nearExpiryWarningDays,
            List<AlertCondition> alerts)
    {
        LocalDate today =
                LocalDate.now(
                        BUSINESS_ZONE
                );


        LocalDate warningEnd =
                today.plusDays(
                        nearExpiryWarningDays
                );


        for (Batch batch :
                batches)
        {
            if (batch == null)
            {
                continue;
            }


            if (!medicine.getMedicineId()
                    .equals(
                            batch.getMedicineId()
                    ))
            {
                continue;
            }


            if (batch.getQuantityAvailable()
                    <= 0)
            {
                continue;
            }


            LocalDate expiryDate =
                    toBusinessDate(
                            batch.getExpiryDate()
                    );


            boolean expired =
                    batch.getStatus()
                            == BatchStatus.EXPIRED
                            ||
                            expiryDate.isBefore(
                                    today
                            );



            if (expired)
            {
                alerts.add(
                        new AlertCondition(
                                AlertType.EXPIRED,
                                medicine.getMedicineId(),
                                batch.getBatchId(),
                                AlertSeverity.HIGH,
                                "Expired stock remains in batch "
                                        + batch.getBatchNumber()
                                        + ": "
                                        + batch.getQuantityAvailable()
                                        + " units.",
                                0,
                                0,
                                0,
                                0,
                                batch.getQuantityAvailable(),
                                batch.getExpiryDate()
                        )
                );


                continue;
            }


            if (!expiryDate.isAfter(
                    warningEnd
            ))
            {
                alerts.add(
                        new AlertCondition(
                                AlertType.NEAR_EXPIRY,
                                medicine.getMedicineId(),
                                batch.getBatchId(),
                                AlertSeverity.MEDIUM,
                                "Batch "
                                        + batch.getBatchNumber()
                                        + " is approaching expiry with "
                                        + batch.getQuantityAvailable()
                                        + " units available.",
                                0,
                                0,
                                0,
                                0,
                                batch.getQuantityAvailable(),
                                batch.getExpiryDate()
                        )
                );
            }
        }
    }


    private LocalDate toBusinessDate(
            long timestamp)
    {
        if (timestamp <= 0)
        {
            throw new IllegalArgumentException(
                    "Batch expiry date is invalid."
            );
        }


        return Instant
                .ofEpochMilli(
                        timestamp
                )
                .atZone(
                        BUSINESS_ZONE
                )
                .toLocalDate();
    }
}