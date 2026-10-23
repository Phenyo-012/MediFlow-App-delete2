package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.TypeConverters;
import androidx.room.Upsert;

import com.medicationinventory.data.local.entity.AlertEntity;
import com.medicationinventory.data.local.model.AlertListRecord;
import com.medicationinventory.data.local.converter.AlertConverter;
import com.medicationinventory.data.local.model.AlertReportRecord;
import com.medicationinventory.data.local.model.AlertReportBatchOption;
import com.medicationinventory.data.local.model.AlertReportMedicineOption;

import com.medicationinventory.model.enums.AlertStatus;
import com.medicationinventory.model.enums.AlertType;

import java.util.List;


/**
 * Provides alert persistence and queries for active alerts, notification candidates, acknowledgement, and report projections. Alert evaluation decides desired conditions; repository reconciliation uses this DAO to update existing rows without creating duplicate open alerts.
 */
@Dao
@TypeConverters(AlertConverter.class)
public interface AlertDao
{

    /*
     * WRITE
     */
    @Upsert

    void upsert(AlertEntity alert);

    @Upsert

    void upsert(List<AlertEntity> alerts);

    @Query("DELETE FROM alerts WHERE batchId = :batchId")

    int deleteForRejectedProvisionalBatch(String batchId);


    /*
     * SINGLE ALERT
     */
    @Query("SELECT * FROM alerts " + "WHERE alertId = :alertId " + "LIMIT 1")

    AlertEntity getById(String alertId);

    @Query("SELECT * FROM alerts " + "WHERE alertId = :alertId " +
            "LIMIT 1")

    LiveData<AlertEntity> observeById(String alertId);



    @Query("SELECT * FROM alerts " + "WHERE status != 'RESOLVED' " +
            "ORDER BY updatedAt DESC, createdAt DESC")

    LiveData<List<AlertEntity>> observeOpenAlerts();


    @Query("SELECT * FROM alerts " +
            "WHERE status = 'ACTIVE' " +
            "ORDER BY createdAt ASC, alertId ASC")

    List<AlertEntity> getActiveForNotificationDispatch();

    @Query("SELECT * FROM alerts " + "WHERE status = :status " +
            "ORDER BY updatedAt DESC, createdAt DESC")

    LiveData<List<AlertEntity>> observeByStatus(AlertStatus status);



    @Query("SELECT * FROM alerts " + "WHERE alertType = :alertType " +
            "ORDER BY updatedAt DESC, createdAt DESC")

    LiveData<List<AlertEntity>> observeByType(AlertType alertType);


    @Query("SELECT * FROM alerts " + "WHERE medicineId = :medicineId " +
            "ORDER BY updatedAt DESC, createdAt DESC")

    LiveData<List<AlertEntity>> observeForMedicine(String medicineId);


    @Query("SELECT * FROM alerts " + "WHERE alertType = :alertType " +
            "AND medicineId = :medicineId " + "AND (" + "batchId = :batchId " +
            "OR (batchId IS NULL AND :batchId IS NULL)" + ") " +
            "AND status != 'RESOLVED' " + "LIMIT 1")

    AlertEntity getOpenAlertForCondition(AlertType alertType, String medicineId, String batchId);


    @Query("UPDATE alerts " + "SET status = 'ACKNOWLEDGED', " + "updatedAt = :updatedAt " +
            "WHERE alertId = :alertId " + "AND status = 'ACTIVE'")

    int acknowledge(String alertId, long updatedAt);


    @Query("UPDATE alerts " + "SET status = 'RESOLVED', " + "updatedAt = :updatedAt " +
            "WHERE alertId = :alertId " + "AND status != 'RESOLVED'")

    int resolve(String alertId, long updatedAt);


    @Query("SELECT * FROM alerts " + "WHERE medicineId = :medicineId " + "AND status != 'RESOLVED' "
            + "ORDER BY createdAt ASC")

    List<AlertEntity> getOpenAlertsForMedicine(String medicineId);

    @Query("SELECT " + "a.alertId AS alertId, " + "a.alertType AS alertType, " +
            "a.medicineId AS medicineId, " + "m.medicineName AS medicineName, " +
            "m.medicineCode AS medicineCode, " + "a.batchId AS batchId, " +
            "b.batchNumber AS batchNumber, " + "a.severity AS severity, " + "a.status AS status, "
            + "a.message AS message, " + "a.createdAt AS createdAt, " + "a.updatedAt AS updatedAt "
            + "FROM alerts a " + "INNER JOIN medicines m " + "ON m.medicineId = a.medicineId " +
            "LEFT JOIN batches b " + "ON b.batchId = a.batchId " + "WHERE a.status != 'RESOLVED' "
            + "ORDER BY " + "CASE a.severity " + "WHEN 'HIGH' THEN 0 " + "ELSE 1 END, "
            + "a.updatedAt DESC")

    LiveData<List<AlertListRecord>> observeOpenAlertList();

    @Query(
            "SELECT " +
                    "a.alertId AS alertId, " +
                    "a.alertType AS alertType, " +
                    "a.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode, " +
                    "a.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +
                    "a.severity AS severity, " +
                    "a.status AS status, " +
                    "a.message AS message, " +
                    "a.createdAt AS createdAt, " +
                    "a.updatedAt AS updatedAt " +

                    "FROM alerts a " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = a.medicineId " +

                    "LEFT JOIN batches b " +
                    "ON b.batchId = a.batchId " +

                    "WHERE a.createdAt >= :startInclusive " +
                    "AND a.createdAt < :endExclusive " +

                    "AND (:medicineId IS NULL " +
                    "OR a.medicineId = :medicineId) " +

                    "AND (:batchId IS NULL " +
                    "OR a.batchId = :batchId) " +

                    "AND (:alertType IS NULL " +
                    "OR a.alertType = :alertType) " +

                    "AND (:status IS NULL " +
                    "OR a.status = :status) " +

                    "ORDER BY a.createdAt DESC, " +
                    "a.alertId DESC"
    )
    LiveData<List<AlertReportRecord>>
    observeAlertReport(long startInclusive, long endExclusive, String medicineId, String batchId,
                       String alertType, String status);

    @Query(
            "SELECT DISTINCT " +
                    "m.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode " +

                    "FROM alerts a " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = a.medicineId " +

                    "WHERE a.createdAt >= :startInclusive " +
                    "AND a.createdAt < :endExclusive " +

                    "ORDER BY m.medicineName COLLATE NOCASE ASC, " +
                    "m.medicineCode COLLATE NOCASE ASC"
    )
    LiveData<List<AlertReportMedicineOption>>
    observeAlertReportMedicineOptions(
            long startInclusive,
            long endExclusive);


    @Query(
            "SELECT DISTINCT " +
                    "b.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +
                    "b.medicineId AS medicineId " +

                    "FROM alerts a " +

                    "INNER JOIN batches b " +
                    "ON b.batchId = a.batchId " +

                    "WHERE a.createdAt >= :startInclusive " +
                    "AND a.createdAt < :endExclusive " +

                    "AND a.batchId IS NOT NULL " +

                    "AND (:medicineId IS NULL " +
                    "OR a.medicineId = :medicineId) " +

                    "ORDER BY b.batchNumber COLLATE NOCASE ASC, " +
                    "b.batchId ASC"
    )
    LiveData<List<AlertReportBatchOption>>
    observeAlertReportBatchOptions(
            long startInclusive,
            long endExclusive,
            String medicineId);

    @Query(
            "SELECT COUNT(*) " +
                    "FROM alerts " +
                    "WHERE status = 'ACTIVE'"
    )

    LiveData<Integer> observeActiveAlertCount();


    @Query(
            "SELECT COUNT(*) " +
                    "FROM alerts " +
                    "WHERE alertType = 'NEAR_EXPIRY' " +
                    "AND status != 'RESOLVED'"
    )

    LiveData<Integer> observeOpenNearExpiryCount();


    @Query(
            "SELECT COUNT(*) " +
                    "FROM alerts " +
                    "WHERE alertType = 'EXPIRED' " +
                    "AND status != 'RESOLVED'"
    )

    LiveData<Integer> observeOpenExpiredCount();
}
