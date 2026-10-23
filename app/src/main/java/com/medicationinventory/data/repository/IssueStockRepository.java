package com.medicationinventory.data.repository;

import com.medicationinventory.data.local.dao.BatchDao;
import com.medicationinventory.data.local.entity.BatchEntity;
import com.medicationinventory.service.FefoAllocationService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads local batch candidates and delegates FEFO allocation for stock issue previews.
 */
public class IssueStockRepository {

    private final BatchDao batchDao;
    private final FefoAllocationService fefoAllocationService;

    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor();


    public IssueStockRepository(
            BatchDao batchDao,
            FefoAllocationService fefoAllocationService
    ) {

        this.batchDao =
                batchDao;

        this.fefoAllocationService =
                fefoAllocationService;
    }


    public void calculateFefo(
            String medicineId,
            int requestedQuantity,
            Callback callback
    ) {

        ioExecutor.execute(() -> {

            try {

                List<BatchEntity> batches =
                        batchDao.getFefoCandidates(
                                medicineId
                        );


                FefoAllocationService.Result result =
                        fefoAllocationService.allocate(
                                batches,
                                requestedQuantity
                        );


                callback.onSuccess(
                        result
                );


            } catch (Exception exception) {

                callback.onError(
                        exception
                );
            }
        });
    }


    public interface Callback {

        void onSuccess(
                FefoAllocationService.Result result
        );


        void onError(
                Exception exception
        );
    }
}