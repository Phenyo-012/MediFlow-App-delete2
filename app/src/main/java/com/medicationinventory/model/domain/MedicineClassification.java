package com.medicationinventory.model.domain;



/**
 * Stores one medicine's ABC and VED labels with the time the shared
 * classification was updated. It is separate from the medicine master because
 * classified values have their own trusted write path and access restrictions.
 */
public class MedicineClassification
{
    private final String medicineId;

    private final String abcClassification;

    private final String vedClassification;

    private final long updatedAt;


    public MedicineClassification(
            String medicineId,
            String abcClassification,
            String vedClassification,
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


    public String getMedicineId()
    {
        return medicineId;
    }


    public String getAbcClassification()
    {
        return abcClassification;
    }


    public String getVedClassification()
    {
        return vedClassification;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public String getDisplayClassification()
    {
        return abcClassification
                + "-"
                + vedClassification;
    }
}