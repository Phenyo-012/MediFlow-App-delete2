package com.medicationinventory.data.mapper;


import com.medicationinventory.data.local.entity.MedicineClassificationEntity;
import com.medicationinventory.model.domain.MedicineClassification;




/**
 * Maps between MedicineClassification domain objects and local MedicineClassificationEntity records.
 */
public final class MedicineClassificationMapper
{

    private MedicineClassificationMapper()
    {
    }


    public static MedicineClassification toDomain(
            MedicineClassificationEntity entity)
    {
        if (entity == null)
        {
            return null;
        }


        return new MedicineClassification(
                entity.getMedicineId(),
                entity.getAbcClassification(),
                entity.getVedClassification(),
                entity.getUpdatedAt()
        );
    }


    public static MedicineClassificationEntity toEntity(
            MedicineClassification classification)
    {
        if (classification == null)
        {
            return null;
        }


        return new MedicineClassificationEntity(
                classification.getMedicineId(),
                classification.getAbcClassification(),
                classification.getVedClassification(),
                classification.getUpdatedAt()
        );
    }
}