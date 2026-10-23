package com.medicationinventory.ui.audit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.entity.AuditCacheEntity;
import com.medicationinventory.data.repository.AuditRepository;

import java.util.List;



/**
 * Owns observable screen state and user actions for Audit Log.
 */
public class AuditLogViewModel
        extends ViewModel
{
    private final AuditRepository
            auditRepository;


    private final LiveData<List<AuditCacheEntity>>
            auditRecords;


    private final MutableLiveData<Boolean>
            refreshing =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<String>
            refreshError =
            new MutableLiveData<>(
                    null
            );



    public AuditLogViewModel(
            AuditRepository auditRepository)
    {
        if (auditRepository == null)
        {
            throw new IllegalArgumentException(
                    "Audit repository is required."
            );
        }


        this.auditRepository =
                auditRepository;


        auditRecords =
                auditRepository
                        .observeRecent();
    }


    public LiveData<List<AuditCacheEntity>>
    getAuditRecords()
    {
        return auditRecords;
    }


    public LiveData<Boolean>
    getRefreshing()
    {
        return refreshing;
    }


    public LiveData<String>
    getRefreshError()
    {
        return refreshError;
    }



    public void refresh()
    {
        if (Boolean.TRUE.equals(
                refreshing.getValue()
        ))
        {
            return;
        }


        refreshing.setValue(
                true
        );


        refreshError.setValue(
                null
        );


        auditRepository.refreshRecent(
                new AuditRepository.RefreshCallback()
                {
                    @Override

                    public void onSuccess(
                            int recordCount)
                    {
                        refreshing.setValue(
                                false
                        );
                    }


                    @Override

                    public void onError(
                            Exception exception)
                    {
                        refreshing.setValue(
                                false
                        );



                        refreshError.setValue(
                                "Unable to refresh audit history. "
                                        +
                                        "Showing locally available records."
                        );
                    }
                }
        );
    }
}