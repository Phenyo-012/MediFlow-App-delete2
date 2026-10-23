package com.medicationinventory.data.local.converter;

import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.model.domain.Batch;



/**
 * Maps between Room BatchEntity records and the Batch domain model.
 */
public final class BatchMapper
{

    private BatchMapper()
    {
        // Prevent instantiation
    }

    public static Batch toDomain(BatchEntity entity)
    {
        if (entity == null)
        {
            return null;
        }

        return new Batch(
                entity.getBatchId(),
                entity.getMedicineId(),
                entity.getBatchNumber(),
                entity.getReceivedDate(),
                entity.getExpiryDate(),
                entity.getQuantityAvailable(),
                entity.getInitialQuantity(),
                entity.getStatus(),
                entity.getUpdatedAt()
        );
    }

}