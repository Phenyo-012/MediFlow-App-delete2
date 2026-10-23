package com.medicationinventory.data.local.entity;


import androidx.annotation.NonNull;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;



/**
 * Room cache of the shared medicine master record used by inventory screens and batch relationships.
 * It stores the stable medicine ID, human-entered code and names, category, stock thresholds, and
 * active state. The unique medicine-code index prevents conflicting local catalog entries; shared
 * edits are still submitted through the trusted medicine backend.
 */
@Entity(
        tableName = "medicines",
        indices = {
                @Index(
                        value = "medicineCode",
                        unique = true
                ),
                @Index(
                        value = "medicineName"
                ),
                @Index(
                        value = "isActive"
                ),
                @Index(
                        value = "category"
                )
        }
)
public class MedicineEntity
{
    @PrimaryKey
    @NonNull
    private String medicineId;


    @NonNull
    private String medicineCode;


    @NonNull
    private String medicineName;


    private String genericName;


    @NonNull
    @ColumnInfo(
            defaultValue = "'Uncategorised'"
    )
    private String category;


    private String strength;


    @NonNull
    private String dosageForm;


    @NonNull
    private String unitOfMeasure;


    private int reorderLevel;


    private int targetStockLevel;


    private boolean isActive;


    private long updatedAt;


    private long syncVersion;


    public MedicineEntity(
            @NonNull String medicineId,
            @NonNull String medicineCode,
            @NonNull String medicineName,
            String genericName,
            @NonNull String category,
            String strength,
            @NonNull String dosageForm,
            @NonNull String unitOfMeasure,
            int reorderLevel,
            int targetStockLevel,
            boolean isActive,
            long updatedAt,
            long syncVersion)
    {
        this.medicineId =
                medicineId;

        this.medicineCode =
                medicineCode;

        this.medicineName =
                medicineName;

        this.genericName =
                genericName;

        this.category =
                category;

        this.strength =
                strength;

        this.dosageForm =
                dosageForm;

        this.unitOfMeasure =
                unitOfMeasure;

        this.reorderLevel =
                reorderLevel;

        this.targetStockLevel =
                targetStockLevel;

        this.isActive =
                isActive;

        this.updatedAt =
                updatedAt;


        this.syncVersion =
                syncVersion;
    }


    @Ignore
    public MedicineEntity(
            @NonNull String medicineId,
            @NonNull String medicineCode,
            @NonNull String medicineName,
            String genericName,
            String strength,
            @NonNull String dosageForm,
            @NonNull String unitOfMeasure,
            int reorderLevel,
            int targetStockLevel,
            boolean isActive,
            long updatedAt,
            long syncVersion)
    {
        this(
                medicineId,
                medicineCode,
                medicineName,
                genericName,
                "Uncategorised",
                strength,
                dosageForm,
                unitOfMeasure,
                reorderLevel,
                targetStockLevel,
                isActive,
                updatedAt,
                syncVersion
        );
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
    public String getMedicineCode()
    {
        return medicineCode;
    }


    public void setMedicineCode(
            @NonNull String medicineCode)
    {
        this.medicineCode =
                medicineCode;
    }


    @NonNull
    public String getMedicineName()
    {
        return medicineName;
    }


    public void setMedicineName(
            @NonNull String medicineName)
    {
        this.medicineName =
                medicineName;
    }


    public String getGenericName()
    {
        return genericName;
    }


    public void setGenericName(
            String genericName)
    {
        this.genericName =
                genericName;
    }


    @NonNull
    public String getCategory()
    {
        return category;
    }


    public void setCategory(
            @NonNull String category)
    {
        this.category =
                category;
    }


    public String getStrength()
    {
        return strength;
    }


    public void setStrength(
            String strength)
    {
        this.strength =
                strength;
    }


    @NonNull
    public String getDosageForm()
    {
        return dosageForm;
    }


    public void setDosageForm(
            @NonNull String dosageForm)
    {
        this.dosageForm =
                dosageForm;
    }


    @NonNull
    public String getUnitOfMeasure()
    {
        return unitOfMeasure;
    }


    public void setUnitOfMeasure(
            @NonNull String unitOfMeasure)
    {
        this.unitOfMeasure =
                unitOfMeasure;
    }


    public int getReorderLevel()
    {
        return reorderLevel;
    }


    public void setReorderLevel(
            int reorderLevel)
    {
        this.reorderLevel =
                reorderLevel;
    }


    public int getTargetStockLevel()
    {
        return targetStockLevel;
    }


    public void setTargetStockLevel(
            int targetStockLevel)
    {
        this.targetStockLevel =
                targetStockLevel;
    }


    public boolean isActive()
    {
        return isActive;
    }


    public void setActive(
            boolean active)
    {
        isActive =
                active;
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


    public long getSyncVersion()
    {
        return syncVersion;
    }


    public void setSyncVersion(
            long syncVersion)
    {

        this.syncVersion =
                syncVersion;
    }
}