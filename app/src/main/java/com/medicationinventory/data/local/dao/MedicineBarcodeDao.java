package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.MedicineBarcodeEntity;
import java.util.List;

/**
 * Provides local barcode-to-medicine lookup and observation used by scanning and medicine administration. Shared mappings are validated by the repository and trusted backend before becoming authoritative.
 */
@Dao
public interface MedicineBarcodeDao {
    @Query("SELECT * FROM medicine_barcodes ORDER BY barcode ASC")

    LiveData<List<MedicineBarcodeEntity>> observeAll();

    @Query("SELECT * FROM medicine_barcodes WHERE medicineId = :medicineId ORDER BY barcode ASC")

    LiveData<List<MedicineBarcodeEntity>> observeForMedicine(String medicineId);

    @Query("SELECT * FROM medicine_barcodes WHERE barcode = :barcode LIMIT 1")

    MedicineBarcodeEntity getByBarcode(String barcode);

    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insert(MedicineBarcodeEntity entity);

    @Query("DELETE FROM medicine_barcodes WHERE barcode = :barcode AND medicineId = :medicineId")

    int unlink(String barcode, String medicineId);
}
