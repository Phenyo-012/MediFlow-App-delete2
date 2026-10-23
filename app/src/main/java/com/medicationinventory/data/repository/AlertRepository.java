package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;

import com.medicationinventory.data.local.dao.AlertDao;
import com.medicationinventory.data.local.database.AppDatabase;
import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.model.AlertListRecord;

import com.medicationinventory.model.domain.AlertCondition;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;

import com.medicationinventory.util.AppExecutors;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;



/**
 * Stores and observes alerts in Room, including reconciliation of evaluated alert conditions.
 */
public class AlertRepository
{
    private final AppDatabase database;

    private final AlertDao alertDao;

    private final AppExecutors appExecutors;


    public AlertRepository(
            AppDatabase database,
            AppExecutors appExecutors)
    {
        if (database == null)
        {
            throw new IllegalArgumentException(
                    "AppDatabase is required."
            );
        }


        if (appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "AppExecutors is required."
            );
        }


        this.database =
                database;

        this.alertDao =
                database.alertDao();

        this.appExecutors =
                appExecutors;
    }



    public LiveData<List<AlertEntity>>
    observeOpenAlerts()
    {
        return alertDao
                .observeOpenAlerts();
    }


    public LiveData<List<AlertEntity>>
    observeForMedicine(
            String medicineId)
    {
        validateMedicineId(
                medicineId
        );


        return alertDao
                .observeForMedicine(
                        medicineId
                );
    }



    private void reconcileInsideTransaction(
            String medicineId,
            List<AlertCondition> currentConditions)
    {
        List<AlertEntity> existingOpenAlerts =
                alertDao
                        .getOpenAlertsForMedicine(
                                medicineId
                        );


        Map<String, AlertEntity> existingByCondition =
                new HashMap<>();


        if (existingOpenAlerts != null)
        {
            for (AlertEntity existing :
                    existingOpenAlerts)
            {
                if (existing == null)
                {
                    continue;
                }


                String key =
                        conditionKey(
                                existing.getAlertType(),
                                existing.getMedicineId(),
                                existing.getBatchId()
                        );



                if (existingByCondition.put(
                        key,
                        existing
                ) != null)
                {
                    throw new IllegalStateException(
                            "Duplicate open alert condition found: "
                                    + key
                    );
                }
            }
        }


        Set<String> currentConditionKeys =
                new HashSet<>();


        long now =
                System.currentTimeMillis();


        for (AlertCondition condition :
                currentConditions)
        {
            validateCondition(
                    medicineId,
                    condition
            );


            String key =
                    conditionKey(
                            condition.getAlertType(),
                            condition.getMedicineId(),
                            condition.getBatchId()
                    );


            if (!currentConditionKeys.add(
                    key
            ))
            {
                throw new IllegalStateException(
                        "Duplicate evaluated alert condition: "
                                + key
                );
            }


            AlertEntity existing =
                    existingByCondition
                            .remove(
                                    key
                            );


            if (existing == null)
            {
                createNewAlert(
                        condition,
                        now
                );
            }
            else
            {
                refreshExistingAlert(
                        existing,
                        condition,
                        now
                );
            }
        }


        for (AlertEntity staleAlert :
                existingByCondition.values())
        {
            if (staleAlert == null)
            {
                continue;
            }


            staleAlert.setStatus(
                    AlertStatus.RESOLVED
            );


            staleAlert.setUpdatedAt(
                    now
            );


            alertDao.upsert(
                    staleAlert
            );
        }
    }


    private void createNewAlert(
            AlertCondition condition,
            long now)
    {
        AlertEntity entity =
                new AlertEntity(
                        UUID.randomUUID()
                                .toString(),

                        condition.getAlertType(),

                        condition.getMedicineId(),

                        condition.getBatchId(),

                        condition.getSeverity(),

                        AlertStatus.ACTIVE,

                        condition.getMessage(),

                        now,

                        now
                );


        alertDao.upsert(
                entity
        );
    }


    private void refreshExistingAlert(
            AlertEntity existing,
            AlertCondition condition,
            long now)
    {
        boolean changed =
                existing.getSeverity()
                        != condition.getSeverity()
                        ||
                        !Objects.equals(
                                existing.getMessage(),
                                condition.getMessage()
                        );


        if (!changed)
        {
            return;
        }


        existing.setSeverity(
                condition.getSeverity()
        );


        existing.setMessage(
                condition.getMessage()
        );


        existing.setUpdatedAt(
                now
        );


        alertDao.upsert(
                existing
        );
    }


    private void validateMedicineId(
            String medicineId)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Medicine ID is required."
            );
        }
    }


    private void validateCondition(
            String expectedMedicineId,
            AlertCondition condition)
    {
        if (condition == null)
        {
            throw new IllegalArgumentException(
                    "Alert condition cannot be null."
            );
        }


        if (!expectedMedicineId.equals(
                condition.getMedicineId()
        ))
        {
            throw new IllegalArgumentException(
                    "Alert condition belongs to a different medicine."
            );
        }


        if (condition.getAlertType() == null)
        {
            throw new IllegalArgumentException(
                    "Alert type is required."
            );
        }


        if (condition.getSeverity() == null)
        {
            throw new IllegalArgumentException(
                    "Alert severity is required."
            );
        }


        if (condition.getMessage() == null
                ||
                condition.getMessage()
                        .trim()
                        .isEmpty())
        {
            throw new IllegalArgumentException(
                    "Alert message is required."
            );
        }


        if (condition.getAlertType()
                == AlertType.LOW_STOCK)
        {
            if (condition.getBatchId()
                    != null)
            {
                throw new IllegalArgumentException(
                        "LOW_STOCK must not reference a batch."
                );
            }
        }
        else
        {
            if (condition.getBatchId()
                    == null
                    ||
                    condition.getBatchId()
                            .trim()
                            .isEmpty())
            {
                throw new IllegalArgumentException(
                        "Batch alert requires a batch ID."
                );
            }
        }
    }


    private String conditionKey(
            AlertType alertType,
            String medicineId,
            String batchId)
    {
        if (alertType == null)
        {
            throw new IllegalArgumentException(
                    "Alert type is required."
            );
        }


        validateMedicineId(
                medicineId
        );


        return alertType.name()
                + "|"
                + medicineId
                + "|"
                + (
                batchId == null
                        ? ""
                        : batchId
        );
    }

    public void reconcileMedicineAlertsBlocking(
            String medicineId,
            List<AlertCondition> currentConditions)
    {
        validateMedicineId(
                medicineId
        );


        List<AlertCondition> safeConditions =
                currentConditions == null
                        ? Collections.emptyList()
                        : currentConditions;



        database.runInTransaction(
                () ->
                        reconcileInsideTransaction(
                                medicineId,
                                safeConditions
                        )
        );
    }


    public LiveData<List<AlertListRecord>>
    observeOpenAlertList()
    {
        return alertDao
                .observeOpenAlertList();
    }

    public void acknowledgeAlert(
            String alertId,
            OperationCallback callback)
    {
        if (alertId == null
                ||
                alertId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Alert ID is required."
            );
        }


        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Callback is required."
            );
        }


        appExecutors
                .databaseIO()
                .execute(() ->
                {
                    try
                    {
                        AlertEntity existing =
                                alertDao
                                        .getById(
                                                alertId
                                        );


                        if (existing == null)
                        {
                            throw new IllegalStateException(
                                    "Alert could not be found."
                            );
                        }


                        if (existing.getStatus()
                                == AlertStatus.RESOLVED)
                        {
                            throw new IllegalStateException(
                                    "Resolved alerts cannot be acknowledged."
                            );
                        }

                        if (existing.getStatus()
                                == AlertStatus.ACKNOWLEDGED)
                        {
                            appExecutors
                                    .mainThread()
                                    .execute(
                                            callback::onSuccess
                                    );

                            return;
                        }


                        int updated =
                                alertDao
                                        .acknowledge(
                                                alertId,
                                                System.currentTimeMillis()
                                        );


                        if (updated != 1)
                        {
                            throw new IllegalStateException(
                                    "Alert acknowledgement could not be completed."
                            );
                        }


                        appExecutors
                                .mainThread()
                                .execute(
                                        callback::onSuccess
                                );
                    }
                    catch (Exception exception)
                    {
                        appExecutors
                                .mainThread()
                                .execute(() ->
                                        callback.onError(
                                                exception
                                        )
                                );
                    }
                });
    }

    public LiveData<Integer>
    observeActiveAlertCount()
    {
        return alertDao
                .observeActiveAlertCount();
    }

    public LiveData<Integer>
    observeOpenNearExpiryCount()
    {
        return alertDao
                .observeOpenNearExpiryCount();
    }

    public LiveData<Integer>
    observeOpenExpiredCount()
    {
        return alertDao
                .observeOpenExpiredCount();
    }

    public interface OperationCallback
    {
        void onSuccess();


        void onError(
                Exception exception
        );
    }
}