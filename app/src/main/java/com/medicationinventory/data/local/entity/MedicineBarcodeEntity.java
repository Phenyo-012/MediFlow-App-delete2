package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


/**
 * Local barcode-to-medicine lookup used by scanner workflows. The barcode is the primary key, the
 * medicine ID is protected by a Room foreign key, and creation time is retained for local history;
 * cloud-shared mapping changes are sent through the trusted barcode service.
 */
@Entity(tableName = "medicine_barcodes",
        foreignKeys = @ForeignKey(entity = MedicineEntity.class,
                parentColumns = "medicineId", childColumns = "medicineId",
                onUpdate = ForeignKey.NO_ACTION, onDelete = ForeignKey.NO_ACTION),
        indices = {@Index(value = "medicineId")})
public class MedicineBarcodeEntity {
    @PrimaryKey
    @NonNull
    private String barcode;
    @NonNull
    private String medicineId;
    private long createdAt;

    public MedicineBarcodeEntity(@NonNull String barcode,
                                 @NonNull String medicineId, long createdAt) {
        this.barcode = barcode;
        this.medicineId = medicineId;
        this.createdAt = createdAt;
    }
    @NonNull public String getBarcode() { return barcode; }

    public void setBarcode(@NonNull String barcode) { this.barcode = barcode; }
    @NonNull public String getMedicineId() { return medicineId; }
    public void setMedicineId(@NonNull String medicineId) { this.medicineId = medicineId; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
