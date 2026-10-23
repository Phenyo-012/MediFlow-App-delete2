package com.medicationinventory.ui.audit;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.local.entity.AuditCacheEntity;
import com.medicationinventory.data.repository.AuditRepository;

import java.util.List;



/**
 * Exposes the repository's locally cached audit records and coordinates an explicit refresh from
 * the trusted audit source. Refresh state prevents overlapping requests; if the network refresh
 * fails, the current Room cache remains available and the ViewModel reports that fallback rather
 * than replacing it with an empty history.
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



    /** Requests a recent server audit snapshot while leaving cached rows available during failure. */
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