package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.medicationinventory.data.local.entity.BatchEntity;

import java.util.List;


/**
 * Reads and updates medicine batches for inventory screens, FEFO selection, alerts, and reorder calculations. Expiry queries use the supplied business-day boundary, while local stock commits are coordinated by StockOperationDao.
 */
@Dao
public interface BatchDao
{
    @Query("SELECT * FROM batches " + "WHERE medicineId = :medicineId " +
            "ORDER BY expiryDate ASC, receivedDate ASC, batchId ASC")

    LiveData<List<BatchEntity>> observeForMedicine(String medicineId);


    @Query("SELECT * FROM batches " + "WHERE batchId = :batchId " + "LIMIT 1")

    BatchEntity getById(String batchId);

    @Query("SELECT * FROM batches " + "WHERE medicineId = :medicineId " +
            "AND batchNumber = :batchNumber " + "LIMIT 1")

    BatchEntity getByMedicineAndBatchNumber(String medicineId, String batchNumber);

    @Upsert

    void upsert(BatchEntity batch);

    @Query("UPDATE batches " + "SET quantityAvailable = :quantityAvailable, " + "status = :status, "
            + "updatedAt = :updatedAt " + "WHERE batchId = :batchId")

    void updateReconciledQuantity(String batchId, int quantityAvailable, String status,
                                  long updatedAt);

    @Query("DELETE FROM batches " + "WHERE batchId = :batchId")

    void deleteById(String batchId);

    @Query("SELECT * FROM batches " + "WHERE medicineId = :medicineId " +
            "AND quantityAvailable > 0 " + "AND status = 'ACTIVE' " +
            "ORDER BY expiryDate ASC, receivedDate ASC, batchId ASC")

    List<BatchEntity> getFefoCandidates(String medicineId);

    @Query("SELECT * FROM batches " + "WHERE medicineId = :medicineId " +
            "ORDER BY expiryDate ASC, " + "receivedDate ASC, " + "batchId ASC")

    List<BatchEntity> getForMedicineForAlertEvaluation(String medicineId);

    @Query("UPDATE batches " + "SET status = 'EXPIRED' " + "WHERE expiryDate < :todayStartMillis " +
            "AND status != 'EXPIRED'")

    int markExpiredBefore(long todayStartMillis);

    @Query("SELECT * FROM batches " + "ORDER BY medicineId ASC, " + "expiryDate ASC, " +
            "receivedDate ASC, " + "batchId ASC")

    LiveData<List<BatchEntity>> observeAllForReorderRecommendations();
}