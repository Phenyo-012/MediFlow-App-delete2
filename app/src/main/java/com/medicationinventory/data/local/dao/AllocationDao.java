package com.medicationinventory.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.OperationAllocationEntity;

import java.util.List;


/**
 * Declares Room queries and updates for Allocation records used by local repositories.
 */
@Dao
public interface AllocationDao
{
    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertAllocation(OperationAllocationEntity allocation);

    @Insert(onConflict = OnConflictStrategy.ABORT)

    void insertAllocations(List<OperationAllocationEntity> allocations);

    @Query("SELECT * FROM operation_allocations " + "WHERE operationId = :operationId " +
            "ORDER BY allocationOrder ASC")

    List<OperationAllocationEntity> getForOperation(String operationId);

    @Query("DELETE FROM operation_allocations " +
            "WHERE operationId = :operationId AND batchId = :batchId")

    void deleteForRejectedReceiptBatch(String operationId, String batchId);
}