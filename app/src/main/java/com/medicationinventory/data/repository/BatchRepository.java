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
 * Reads and writes batches through the local Room DAO and maps entities to domain objects.
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