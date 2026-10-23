package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.model.MovementHistoryRecord;
import com.medicationinventory.data.local.model.MovementBatchOption;
import com.medicationinventory.data.local.model.MovementReportMedicineOption;
import com.medicationinventory.data.local.model.MovementReportBatchOption;

import java.util.List;


/**
 * Stores provisional and confirmed inventory movements and provides joined history/report queries. Provisional rows support offline visibility; reconciliation replaces or confirms them from trusted results.
 */
@Dao
public interface TransactionDao
{
    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertProvisional(InventoryTransactionEntity transaction);

    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertProvisional(List<InventoryTransactionEntity> transactions);

    @Query("SELECT * FROM inventory_transactions " + "WHERE operationId = :operationId " +
            "ORDER BY clientCreatedAt ASC")

    List<InventoryTransactionEntity> getForOperation(String operationId);

    @Query("SELECT * FROM inventory_transactions " + "WHERE medicineId = :medicineId " +
            "ORDER BY clientCreatedAt DESC")
    LiveData<List<InventoryTransactionEntity>>
    observeForMedicine(String medicineId);

    @Query("SELECT * FROM inventory_transactions " + "WHERE batchId = :batchId " +
            "ORDER BY clientCreatedAt DESC")
    LiveData<List<InventoryTransactionEntity>>
    observeForBatch(String batchId);

    @Query("DELETE FROM inventory_transactions " + "WHERE operationId = :operationId " +
            "AND provisional = 1")

    void deleteProvisionalForOperation(String operationId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    void upsertConfirmed(InventoryTransactionEntity transaction);

    @Query(
            "SELECT " +

                    "t.transactionId AS transactionId, " +
                    "t.operationId AS operationId, " +

                    "t.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode, " +

                    "t.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +

                    "t.transactionType AS transactionType, " +
                    "t.quantityChange AS quantityChange, " +
                    "t.reason AS reason, " +
                    "t.performedBy AS performedBy, " +

                    "t.clientCreatedAt AS clientCreatedAt, " +
                    "t.serverCreatedAt AS serverCreatedAt, " +
                    "t.provisional AS provisional, " +

                    "o.status AS operationStatus, " +
                    "o.overrideReason AS overrideReason, " +
                    "o.resultCode AS resultCode, " +
                    "o.resultMessage AS resultMessage " +

                    "FROM inventory_transactions t " +

                    "INNER JOIN inventory_operations o " +
                    "ON o.operationId = t.operationId " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = t.medicineId " +

                    "INNER JOIN batches b " +
                    "ON b.batchId = t.batchId " +

                    "WHERE t.medicineId = :medicineId " +

                    "AND (:transactionType IS NULL " +
                    "OR t.transactionType = :transactionType) " +

                    "AND (:batchId IS NULL " +
                    "OR t.batchId = :batchId) " +

                    "AND (:startInclusive <= 0 " +
                    "OR (CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) >= :startInclusive) " +

                    "AND (:endExclusive <= 0 " +
                    "OR (CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) < :endExclusive) " +

                    "ORDER BY " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) DESC, " +

                    "t.clientCreatedAt DESC, " +
                    "t.transactionId DESC " +

                    "LIMIT :limit"
    )
    LiveData<List<MovementHistoryRecord>>
    observeFilteredHistoryForMedicine(String medicineId, String transactionType, String batchId,
                                      long startInclusive, long endExclusive, int limit);

    @Query("SELECT DISTINCT " + "b.batchId AS batchId, " + "b.batchNumber AS batchNumber " +
            "FROM inventory_transactions t " + "INNER JOIN batches b " +
            "ON b.batchId = t.batchId " + "WHERE t.medicineId = :medicineId " +
            "ORDER BY b.batchNumber COLLATE NOCASE ASC")
    LiveData<List<MovementBatchOption>>
    observeHistoryBatchOptions(String medicineId);

    @Query(
            "SELECT " +

                    "t.transactionId AS transactionId, " +
                    "t.operationId AS operationId, " +

                    "t.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode, " +

                    "t.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +

                    "t.transactionType AS transactionType, " +
                    "t.quantityChange AS quantityChange, " +
                    "t.reason AS reason, " +
                    "t.performedBy AS performedBy, " +

                    "t.clientCreatedAt AS clientCreatedAt, " +
                    "t.serverCreatedAt AS serverCreatedAt, " +
                    "t.provisional AS provisional, " +

                    "o.status AS operationStatus, " +
                    "o.overrideReason AS overrideReason, " +
                    "o.resultCode AS resultCode, " +
                    "o.resultMessage AS resultMessage " +

                    "FROM inventory_transactions t " +

                    "INNER JOIN inventory_operations o " +
                    "ON o.operationId = t.operationId " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = t.medicineId " +

                    "INNER JOIN batches b " +
                    "ON b.batchId = t.batchId " +

                    "WHERE " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) >= :startInclusive " +

                    "AND " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) < :endExclusive " +

                    "AND (:medicineId IS NULL " +
                    "OR t.medicineId = :medicineId) " +

                    "AND (:batchId IS NULL " +
                    "OR t.batchId = :batchId) " +

                    "AND (:transactionType IS NULL " +
                    "OR t.transactionType = :transactionType) " +

                    "ORDER BY " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) DESC, " +

                    "t.clientCreatedAt DESC, " +
                    "t.transactionId DESC " +

                    "LIMIT :limit"
    )
    LiveData<List<MovementHistoryRecord>>
    observeMovementReport(
            long startInclusive,
            long endExclusive,
            String medicineId,
            String batchId,
            String transactionType,
            int limit);

    @Query(
            "SELECT DISTINCT " +
                    "m.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode " +

                    "FROM inventory_transactions t " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = t.medicineId " +

                    "WHERE " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) >= :startInclusive " +

                    "AND " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) < :endExclusive " +

                    "ORDER BY " +
                    "m.medicineName COLLATE NOCASE ASC, " +
                    "m.medicineCode COLLATE NOCASE ASC"
    )
    LiveData<List<MovementReportMedicineOption>>
    observeMovementReportMedicineOptions(
            long startInclusive,
            long endExclusive);

    @Query(
            "SELECT DISTINCT " +
                    "b.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +
                    "b.medicineId AS medicineId " +

                    "FROM inventory_transactions t " +

                    "INNER JOIN batches b " +
                    "ON b.batchId = t.batchId " +

                    "WHERE " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) >= :startInclusive " +

                    "AND " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) < :endExclusive " +

                    "AND (:medicineId IS NULL " +
                    "OR t.medicineId = :medicineId) " +

                    "ORDER BY " +
                    "b.batchNumber COLLATE NOCASE ASC, " +
                    "b.batchId ASC"
    )
    LiveData<List<MovementReportBatchOption>>
    observeMovementReportBatchOptions(
            long startInclusive,
            long endExclusive,
            String medicineId);

    @Query(
            "SELECT " +

                    "t.transactionId AS transactionId, " +
                    "t.operationId AS operationId, " +

                    "t.medicineId AS medicineId, " +
                    "m.medicineName AS medicineName, " +
                    "m.medicineCode AS medicineCode, " +

                    "t.batchId AS batchId, " +
                    "b.batchNumber AS batchNumber, " +

                    "t.transactionType AS transactionType, " +
                    "t.quantityChange AS quantityChange, " +
                    "t.reason AS reason, " +
                    "t.performedBy AS performedBy, " +

                    "t.clientCreatedAt AS clientCreatedAt, " +
                    "t.serverCreatedAt AS serverCreatedAt, " +
                    "t.provisional AS provisional, " +

                    "o.status AS operationStatus, " +
                    "o.overrideReason AS overrideReason, " +
                    "o.resultCode AS resultCode, " +
                    "o.resultMessage AS resultMessage " +

                    "FROM inventory_transactions t " +

                    "INNER JOIN inventory_operations o " +
                    "ON o.operationId = t.operationId " +

                    "INNER JOIN medicines m " +
                    "ON m.medicineId = t.medicineId " +

                    "INNER JOIN batches b " +
                    "ON b.batchId = t.batchId " +

                    "ORDER BY " +

                    "(CASE " +
                    "WHEN t.serverCreatedAt > 0 " +
                    "THEN t.serverCreatedAt " +
                    "ELSE t.clientCreatedAt END) DESC, " +

                    "t.clientCreatedAt DESC, " +
                    "t.transactionId DESC " +

                    "LIMIT :limit"
    )
    LiveData<List<MovementHistoryRecord>>
    observeRecentMovement(
            int limit
    );

    @Query(
            "SELECT t.* "
                    +
                    "FROM inventory_transactions t "
                    +
                    "INNER JOIN inventory_operations o "
                    +
                    "ON o.operationId = t.operationId "
                    +
                    "WHERE t.provisional = 0 "
                    +
                    "AND o.status = 'CONFIRMED' "
                    +
                    "AND (CASE "
                    +
                    "WHEN t.serverCreatedAt > 0 "
                    +
                    "THEN t.serverCreatedAt "
                    +
                    "ELSE t.clientCreatedAt END) >= :startInclusive "
                    +
                    "AND (CASE "
                    +
                    "WHEN t.serverCreatedAt > 0 "
                    +
                    "THEN t.serverCreatedAt "
                    +
                    "ELSE t.clientCreatedAt END) < :endExclusive "
                    +
                    "ORDER BY "
                    +
                    "(CASE "
                    +
                    "WHEN t.serverCreatedAt > 0 "
                    +
                    "THEN t.serverCreatedAt "
                    +
                    "ELSE t.clientCreatedAt END) ASC, "
                    +
                    "t.transactionId ASC"
    )
    LiveData<List<InventoryTransactionEntity>>
    observeConfirmedForAnalytics(
            long startInclusive,
            long endExclusive
    );
}