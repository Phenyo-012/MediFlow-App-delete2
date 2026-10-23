package com.medicationinventory.data.local.dao;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.MedicineClassificationEntity;

import java.util.List;



/**
 * Stores ABC/VED classification rows and exposes medicine-specific and analytics queries. Access control is enforced by MedicineClassificationRepository and its session gate rather than by these local SQL queries.
 */
@Dao
public interface MedicineClassificationDao
{
    @Query("SELECT * FROM medicine_classifications " + "WHERE medicineId = :medicineId " + "LIMIT 1")
    LiveData<MedicineClassificationEntity> observeForMedicine(String medicineId);


    @Query("SELECT * FROM medicine_classifications " + "WHERE medicineId = :medicineId " + "LIMIT 1")
    MedicineClassificationEntity getForMedicine(String medicineId);


    @Query("SELECT * FROM medicine_classifications " + "ORDER BY medicineId ASC")
    List<MedicineClassificationEntity> getAll();


    @Query("SELECT mc.* " + "FROM medicine_classifications mc " + "INNER JOIN medicines m "
            + "ON m.medicineId = mc.medicineId " + "WHERE m.isActive = 1 "
            + "ORDER BY mc.medicineId ASC")
    LiveData<List<MedicineClassificationEntity>> observeActiveForAnalytics();


    @Insert(onConflict = OnConflictStrategy.REPLACE)

    void upsert(MedicineClassificationEntity classification);


    @Insert(onConflict = OnConflictStrategy.REPLACE)

    void upsertAll(List<MedicineClassificationEntity> classifications);


    @Query("DELETE FROM medicine_classifications " + "WHERE medicineId = :medicineId")

    void deleteForMedicine(String medicineId);


    @Query("DELETE FROM medicine_classifications")

    void clearAll();
}