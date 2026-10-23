package com.medicationinventory.ui.users;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.remote.backend.TrustedUserAdminDataSource;
import com.medicationinventory.data.repository.AdminUserRepository;

import com.medicationinventory.model.dto.AdminUserSummary;

import java.util.Collections;
import java.util.List;




/**
 * Requests the user list from AdminUserRepository and exposes loading, result, and error state for the management screen. Refreshing does not grant privileges; backend authorization and profile state determine whether the request succeeds.
 */
public class UserManagementViewModel
        extends ViewModel
{
    private final AdminUserRepository
            adminUserRepository;


    private final MutableLiveData<List<AdminUserSummary>>
            users =
            new MutableLiveData<>(
                    Collections.emptyList()
            );


    private final MutableLiveData<Boolean>
            loading =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<String>
            error =
            new MutableLiveData<>(
                    null
            );



    public UserManagementViewModel(
            AdminUserRepository adminUserRepository)
    {
        if (adminUserRepository == null)
        {
            throw new IllegalArgumentException(
                    "Admin user repository is required."
            );
        }


        this.adminUserRepository =
                adminUserRepository;
    }


    public LiveData<List<AdminUserSummary>>
    getUsers()
    {
        return users;
    }


    public LiveData<Boolean>
    getLoading()
    {
        return loading;
    }


    public LiveData<String>
    getError()
    {
        return error;
    }


    public void refresh()
    {
        if (Boolean.TRUE.equals(
                loading.getValue()
        ))
        {
            return;
        }


        loading.setValue(
                true
        );


        error.setValue(
                null
        );


        adminUserRepository
                .loadUsers(
                        new AdminUserRepository.Callback()
                        {
                            @Override

                            public void onSuccess(
                                    List<AdminUserSummary> result)
                            {
                                loading.setValue(
                                        false
                                );


                                users.setValue(
                                        result == null
                                                ? Collections.emptyList()
                                                : result
                                );
                            }


                            @Override

                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                loading.setValue(
                                        false
                                );


                                error.setValue(
                                        friendlyError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }



    private String friendlyError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to load users.";
        }


        String code =
                exception.getCode();


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You no longer have permission to view users.";
        }


        if ("ACCOUNT_INACTIVE".equals(
                code
        ))
        {
            return "Your account is inactive.";
        }


        if ("AUTHENTICATION_REQUIRED".equals(
                code
        )
                ||
                "INVALID_AUTH_TOKEN".equals(
                        code
                ))
        {
            return "Your session could not be verified. Please sign in again.";
        }


        if ("NETWORK_ERROR".equals(
                code
        ))
        {
            return "Unable to connect. Check your internet connection and try again.";
        }


        return "Unable to load users at this time.";
    }
}