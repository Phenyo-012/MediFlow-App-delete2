package com.medicationinventory.data.local.converter;


import com.medicationinventory.data.local.entity.MedicineEntity;

import com.medicationinventory.model.domain.Medicine;


/**
 * Maps between Room MedicineEntity records and the Medicine domain model.
 */
public final class MedicineMapper
{

    private MedicineMapper()
    {
        // Prevent instantiation.
    }


    public static Medicine toDomain(
            MedicineEntity entity)
    {
        if (entity == null)
        {
            return null;
        }


        return new Medicine(
                entity.getMedicineId(),
                entity.getMedicineCode(),
                entity.getMedicineName(),
                entity.getGenericName(),
                entity.getCategory(),
                entity.getStrength(),
                entity.getDosageForm(),
                entity.getUnitOfMeasure(),
                entity.getReorderLevel(),
                entity.getTargetStockLevel(),
                entity.isActive(),
                entity.getUpdatedAt(),
                entity.getSyncVersion()
        );
    }


    public static MedicineEntity toEntity(
            Medicine medicine)
    {
        if (medicine == null)
        {
            return null;
        }


        return new MedicineEntity(
                medicine.getMedicineId(),
                medicine.getMedicineCode(),
                medicine.getMedicineName(),
                medicine.getGenericName(),
                medicine.getCategory(),
                medicine.getStrength(),
                medicine.getDosageForm(),
                medicine.getUnitOfMeasure(),
                medicine.getReorderLevel(),
                medicine.getTargetStockLevel(),
                medicine.isActive(),
                medicine.getUpdatedAt(),
                medicine.getSyncVersion()
        );
    }
}