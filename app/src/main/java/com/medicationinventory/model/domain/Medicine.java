package com.medicationinventory.model.domain;




/**
 * Medicine master data shared by inventory, receiving, scanning, and reporting.
 * It contains identifying/barcode-facing details, category and dosage metadata,
 * stock thresholds, active state, and cloud version timestamps. Batch quantities
 * are stored separately; a missing category is normalized to the application's
 * default category when this model is constructed.
 */
public class Medicine
{
    public static final String DEFAULT_CATEGORY =
            "Uncategorised";


    private final String medicineId;

    private final String medicineCode;

    private final String medicineName;

    private final String genericName;

    private final String category;

    private final String strength;

    private final String dosageForm;

    private final String unitOfMeasure;

    private final int reorderLevel;

    private final int targetStockLevel;

    private final boolean active;

    private final long updatedAt;

    private final long syncVersion;


    public Medicine(
            String medicineId,
            String medicineCode,
            String medicineName,
            String genericName,
            String category,
            String strength,
            String dosageForm,
            String unitOfMeasure,
            int reorderLevel,
            int targetStockLevel,
            boolean active,
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
                category == null
                        || category.trim().isEmpty()
                        ? DEFAULT_CATEGORY
                        : category.trim();

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

        this.active =
                active;

        this.updatedAt =
                updatedAt;

        this.syncVersion =
                syncVersion;
    }


    public Medicine(
            String medicineId,
            String medicineCode,
            String medicineName,
            String genericName,
            String strength,
            String dosageForm,
            String unitOfMeasure,
            int reorderLevel,
            int targetStockLevel,
            boolean active,
            long updatedAt,
            long syncVersion)
    {
        this(
                medicineId,
                medicineCode,
                medicineName,
                genericName,
                DEFAULT_CATEGORY,
                strength,
                dosageForm,
                unitOfMeasure,
                reorderLevel,
                targetStockLevel,
                active,
                updatedAt,
                syncVersion
        );
    }


    public String getMedicineId()
    {
        return medicineId;
    }


    public String getMedicineCode()
    {
        return medicineCode;
    }


    public String getMedicineName()
    {
        return medicineName;
    }


    public String getGenericName()
    {
        return genericName;
    }


    public String getCategory()
    {
        return category;
    }


    public String getStrength()
    {
        return strength;
    }


    public String getDosageForm()
    {
        return dosageForm;
    }


    public String getUnitOfMeasure()
    {
        return unitOfMeasure;
    }


    public int getReorderLevel()
    {
        return reorderLevel;
    }


    public int getTargetStockLevel()
    {
        return targetStockLevel;
    }


    public boolean isActive()
    {
        return active;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public long getSyncVersion()
    {
        return syncVersion;
    }
}