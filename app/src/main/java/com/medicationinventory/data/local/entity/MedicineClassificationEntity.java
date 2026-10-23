package com.medicationinventory.data.local.entity;


import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;



/**
 * Stores locally cached ABC and VED values for one medicine ID together with their update time.
 * Inventory and analytics screens use this restricted classification cache only after a separate
 * access gate grants permission; cloud mutations remain controlled by the trusted backend.
 */
@Entity(
        tableName = "medicine_classifications",
        foreignKeys = {
                @ForeignKey(
                        entity = MedicineEntity.class,
                        parentColumns = "medicineId",
                        childColumns = "medicineId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.NO_ACTION
                )
        }
)
public class MedicineClassificationEntity
{
    @PrimaryKey
    @NonNull
    private String medicineId;


    @NonNull
    private String abcClassification;


    @NonNull
    private String vedClassification;


    private long updatedAt;


    public MedicineClassificationEntity(
            @NonNull String medicineId,
            @NonNull String abcClassification,
            @NonNull String vedClassification,
            long updatedAt)
    {
        this.medicineId =
                medicineId;

        this.abcClassification =
                abcClassification;

        this.vedClassification =
                vedClassification;

        this.updatedAt =
                updatedAt;
    }


    @NonNull
    public String getMedicineId()
    {
        return medicineId;
    }


    public void setMedicineId(
            @NonNull String medicineId)
    {
        this.medicineId =
                medicineId;
    }


    @NonNull
    public String getAbcClassification()
    {
        return abcClassification;
    }


    public void setAbcClassification(
            @NonNull String abcClassification)
    {
        this.abcClassification =
                abcClassification;
    }


    @NonNull
    public String getVedClassification()
    {
        return vedClassification;
    }


    public void setVedClassification(
            @NonNull String vedClassification)
    {
        this.vedClassification =
                vedClassification;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public void setUpdatedAt(
            long updatedAt)
    {
        this.updatedAt =
                updatedAt;
    }
}