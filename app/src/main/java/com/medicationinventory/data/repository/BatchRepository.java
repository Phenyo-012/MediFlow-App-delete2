package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.medicationinventory.data.local.converter.BatchMapper;
import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;


/**
 * Maps local Room batch entities to domain values and provides the batch lookups used by receipt, issue, and inventory screens. Cloud replication is handled by the batch sync coordinator rather than this repository.
 */
public class BatchRepository
{
    private final BatchDao batchDao;
    private final AppExecutors appExecutors;

    public BatchRepository(BatchDao batchDao, AppExecutors appExecutors)
    {
        this.batchDao = batchDao;
        this.appExecutors = appExecutors;
    }

    public LiveData<List<Batch>> observeBatchesForMedicine(String medicineId)
    {
        return Transformations.map(batchDao.observeForMedicine(medicineId),
                entities ->
                {
                    List<Batch> batches = new ArrayList<>();

                    if (entities == null)
                    {
                        return batches;
                    }

                    for (BatchEntity entity : entities)
                    {
                        Batch batch = BatchMapper.toDomain(entity);

                        if (batch != null)
                        {
                            batches.add(batch);
                        }
                    }

                    return batches;
                }
        );
    }

    public void getBatchById(String batchId, BatchCallback callback)
    {
        appExecutors.databaseIO().execute(() ->
        {
            BatchEntity entity = batchDao.getById(batchId);

            Batch batch = BatchMapper.toDomain(entity);

            appExecutors.mainThread().execute(() -> callback.onResult(batch));
        });
    }
    public void getBatchByNumber(String medicineId, String batchNumber, BatchCallback callback)
    {
        appExecutors.databaseIO().execute(() ->
        {
            BatchEntity entity = batchDao.getByMedicineAndBatchNumber(medicineId, batchNumber);

            Batch batch = BatchMapper.toDomain(entity);

            appExecutors.mainThread().execute(() -> callback.onResult(batch));
        });
    }

    public interface BatchCallback
    {
        void onResult(Batch batch);
    }

}