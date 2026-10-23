package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.medicationinventory.data.local.entity.MedicineEntity;
import com.medicationinventory.data.local.projection.MedicineStockProjection;

import java.util.List;

/**
 * Stores medicine master rows and exposes active lists, categories, report inputs, and the inventory stock projection. Deactivation retains the master row for historical references while excluding it from active screens and calculations.
 */
@Dao
public interface MedicineDao
{

    @Query("SELECT * FROM medicines " + "WHERE isActive = 1 " + "ORDER BY medicineName ASC")

    LiveData<List<MedicineEntity>> observeActiveMedicines();


    @Query("SELECT COUNT(*) FROM medicines " + "WHERE isActive = 1")

    LiveData<Integer> observeActiveMedicineCount();

    @Query("SELECT * FROM medicines " + "WHERE medicineId = :medicineId " + "LIMIT 1")

    MedicineEntity getById(String medicineId);

    @Query("SELECT * FROM medicines " + "WHERE medicineCode = :medicineCode " + "LIMIT 1")

    MedicineEntity getByCode(String medicineCode);

    @Upsert

    void upsert(MedicineEntity medicine);

    @Query("UPDATE medicines " + "SET isActive = 0 " + "WHERE medicineId = :medicineId")

    void deactivate(String medicineId);

    @Query("SELECT * FROM medicines " + "WHERE medicineId = :medicineId " + "LIMIT 1")

    LiveData<MedicineEntity> observeById(String medicineId);

    @Query("SELECT * FROM medicines " + "ORDER BY medicineName COLLATE NOCASE ASC")

    List<MedicineEntity> getAllForAlertEvaluation();

    @Query("SELECT * FROM medicines " + "WHERE isActive = 1 " +
            "ORDER BY medicineName COLLATE NOCASE ASC, " + "medicineId ASC")

    LiveData<List<MedicineEntity>> observeActiveForReorderRecommendations();

    @Query("SELECT DISTINCT category " + "FROM medicines " + "WHERE isActive = 1 "
            + "AND TRIM(category) != '' " + "ORDER BY category COLLATE NOCASE ASC")

    LiveData<List<String>> observeActiveCategories();

    @Query("SELECT * FROM medicines " + "ORDER BY medicineName COLLATE NOCASE ASC, " + "medicineId ASC")

    LiveData<List<MedicineEntity>> observeAllForAnalytics();

    @Query("SELECT m.*, " + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :today "
            + "THEN b.quantityAvailable ELSE 0 END), 0) AS usableUnits, "
            + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 " + "AND b.expiryDate < :today "
            + "THEN b.quantityAvailable ELSE 0 END), 0) AS expiredUnits, "
            + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :today "
            + "AND b.expiryDate < :day15 " + "THEN b.quantityAvailable ELSE 0 END), 0) AS expiry0To14Units, "
            + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :day15 " + "AND b.expiryDate < :day31 "
            + "THEN b.quantityAvailable ELSE 0 END), 0) AS expiry15To30Units, "
            + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :day31 " + "AND b.expiryDate < :day91 "
            + "THEN b.quantityAvailable ELSE 0 END), 0) AS expiry31To90Units, "
            + "COALESCE(SUM(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :day91 "
            + "THEN b.quantityAvailable ELSE 0 END), 0) AS expiryAfter90Units, "
            + "MIN(CASE WHEN b.quantityAvailable > 0 "
            + "AND b.status = 'ACTIVE' AND b.expiryDate >= :today " + "THEN b.expiryDate ELSE NULL END) AS nearestUsableExpiry "
            + "FROM medicines m " + "LEFT JOIN batches b ON b.medicineId = m.medicineId " + "WHERE m.isActive = 1 "
            + "GROUP BY m.medicineId " + "ORDER BY m.medicineName COLLATE NOCASE ASC, m.medicineId ASC")

    LiveData<List<MedicineStockProjection>> observeInventoryStock(long today, long day15, long day31, long day91);
}
