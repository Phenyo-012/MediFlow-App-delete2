package com.medicationinventory.ui.receive;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.service.ReceiptService;



/**
 * Creates the Receive Stock ViewModel with its application dependencies.
 */
public class ReceiveStockViewModelFactory
        implements ViewModelProvider.Factory
{

    private final String medicineId;

    private final MedicineRepository medicineRepository;

    private final ReceiptService receiptService;
    private final BatchRepository batchRepository;


    public ReceiveStockViewModelFactory(String medicineId, MedicineRepository medicineRepository,
                                        BatchRepository batchRepository, ReceiptService receiptService)
    {
        this.medicineId = medicineId;

        this.medicineRepository = medicineRepository;

        this.receiptService = receiptService;
        this.batchRepository = batchRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass)
    {

        if (modelClass.isAssignableFrom(ReceiveStockViewModel.class))
        {
            return (T) new ReceiveStockViewModel(medicineId, medicineRepository, batchRepository, receiptService);
        }

        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}