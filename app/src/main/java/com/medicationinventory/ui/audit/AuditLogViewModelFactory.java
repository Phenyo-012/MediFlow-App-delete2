package com.medicationinventory.ui.audit;

import androidx.annotation.NonNull;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuditRepository;



/**
 * Creates the Audit Log ViewModel with its application dependencies.
 */
public class AuditLogViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AuditRepository
            auditRepository;



    public AuditLogViewModelFactory(
            AuditRepository auditRepository)
    {
        this.auditRepository =
                auditRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                AuditLogViewModel.class
        ))
        {
            return (T)
                    new AuditLogViewModel(
                            auditRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}