package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.TypeConverters;

import com.medicationinventory.data.local.converter.InventoryOperationConverter;
import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.model.SyncStatusRecord;
import com.medicationinventory.data.local.model.SyncStatusSummary;
import com.medicationinventory.model.enums.InventoryOperationStatus;

import java.util.List;

/**
 * Stores operation identity and processing state and projects per-user synchronization summaries. Operation ownership in these queries scopes UI data; worker authorization is separately rechecked against Firebase identity.
 */
@Dao
@TypeConverters(InventoryOperationConverter.class)
public interface InventoryOperationDao
{
    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertLocalOperation(InventoryOperationEntity operation);

    @Query("SELECT * FROM inventory_operations " + "WHERE operationId = :operationId " + "LIMIT 1")

    InventoryOperationEntity getById(String operationId);

    @Query("SELECT * FROM inventory_operations " + "WHERE operationId = :operationId " + "LIMIT 1")

    LiveData<InventoryOperationEntity> observeById(String operationId);

    @Query("SELECT * FROM inventory_operations " + "WHERE status = :status " +
            "ORDER BY clientCreatedAt ASC")

    List<InventoryOperationEntity> getByStatus(InventoryOperationStatus status);

    @Query("UPDATE inventory_operations " + "SET status = :status, " + "resultCode = :resultCode, "
            + "resultMessage = :resultMessage " + "WHERE operationId = :operationId")

    void updateStatus(String operationId, InventoryOperationStatus status, String resultCode,
                      String resultMessage);

    @Query("SELECT " + "o.operationId AS operationId, " + "o.operationType AS operationType, " +
            "o.medicineId AS medicineId, " + "m.medicineName AS medicineName, " +
            "m.medicineCode AS medicineCode, " +
            "o.requestedQuantity AS requestedQuantity, " + "o.clientCreatedAt AS clientCreatedAt, "
            + "o.status AS status, " + "o.resultCode AS resultCode, " +
            "o.resultMessage AS resultMessage, " + "COALESCE(q.attemptCount, 0) AS attemptCount, " +
            "COALESCE(q.lastAttemptAt, 0) AS lastAttemptAt, " +
            "COALESCE(q.nextAttemptAt, 0) AS nextAttemptAt, " +
            "q.lastErrorCode AS lastErrorCode " + "FROM inventory_operations o " +
            "INNER JOIN medicines m " + "ON m.medicineId = o.medicineId " +
            "LEFT JOIN sync_queue q " + "ON q.operationId = o.operationId " +
            "WHERE o.performedBy = :performedBy " +
            "ORDER BY o.clientCreatedAt DESC, " + "o.operationId DESC " + "LIMIT :limit")
    LiveData<List<SyncStatusRecord>> observeSyncStatusRecords(String performedBy, int limit);

    @Query("SELECT " + "SUM(CASE " + "WHEN o.status IN ('LOCAL_PENDING', 'SUBMITTED') " +
            "THEN 1 ELSE 0 END) AS pendingCount, " + "SUM(CASE " + "WHEN o.status = 'CONFIRMED' " +
            "THEN 1 ELSE 0 END) AS syncedCount, " +
            "SUM(CASE " + "WHEN o.status IN ('CONFLICT', 'REJECTED') " +
            "THEN 1 ELSE 0 END) AS requiresAttentionCount, " +
            "SUM(CASE " + "WHEN o.status IN ('LOCAL_PENDING', 'SUBMITTED') " +
            "AND q.attemptCount > 0 " + "THEN 1 ELSE 0 END) AS retryingCount " +
            "FROM inventory_operations o " +
            "LEFT JOIN sync_queue q " + "ON q.operationId = o.operationId " +
            "WHERE o.performedBy = :performedBy")
    LiveData<SyncStatusSummary> observeSyncStatusSummary(String performedBy);


    @Query("SELECT " + "o.operationId AS operationId, " + "o.operationType AS operationType, " +
            "o.medicineId AS medicineId, " + "m.medicineName AS medicineName, " +
            "m.medicineCode AS medicineCode, " + "o.requestedQuantity AS requestedQuantity, " +
            "o.clientCreatedAt AS clientCreatedAt, " + "o.status AS status, " +
            "o.resultCode AS resultCode, " + "o.resultMessage AS resultMessage, " +
            "COALESCE(q.attemptCount, 0) AS attemptCount, " +
            "COALESCE(q.lastAttemptAt, 0) AS lastAttemptAt, " +
            "COALESCE(q.nextAttemptAt, 0) AS nextAttemptAt, " + "q.lastErrorCode AS lastErrorCode "
            + "FROM inventory_operations o " + "INNER JOIN medicines m " +
            "ON m.medicineId = o.medicineId " + "LEFT JOIN sync_queue q " +
            "ON q.operationId = o.operationId " +
            "WHERE o.operationId = :operationId " + "AND o.performedBy = :performedBy " + "LIMIT 1")
    LiveData<SyncStatusRecord>
    observeSyncStatusRecordById(String operationId, String performedBy);

    @Query("SELECT COUNT(*) " + "FROM inventory_operations " +
            "WHERE performedBy = :performedBy " +
            "AND status IN ('LOCAL_PENDING', 'SUBMITTED')")

    LiveData<Integer> observePendingSyncCount(String performedBy);


    @Query("SELECT COUNT(*) " + "FROM inventory_operations " + "WHERE performedBy = :performedBy " +
            "AND status IN ('CONFLICT', 'REJECTED')")

    LiveData<Integer> observeSyncAttentionCount(String performedBy);
}