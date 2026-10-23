package com.medicationinventory.ui.users;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.remote.backend.TrustedUserAdminDataSource;
import com.medicationinventory.data.repository.AdminUserRepository;
import com.medicationinventory.model.dto.AdminRoleSummary;

import java.util.Collections;
import java.util.List;





/**
 * Loads role and permission options from AdminUserRepository, validates the proposed account fields, and submits creation requests to the trusted backend. Its state keeps catalog loading and mutation results separate so a failed request can be shown without implying that a user account was created.
 */
public class CreateUserViewModel
        extends ViewModel
{
    private final AdminUserRepository
            adminUserRepository;


    private final MutableLiveData<List<AdminRoleSummary>>
            roles =
            new MutableLiveData<>(
                    Collections.emptyList()
            );


    private final MutableLiveData<Boolean>
            loadingRoles =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<Boolean>
            creating =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<String>
            error =
            new MutableLiveData<>(
                    null
            );


    private final MutableLiveData<String>
            createdUserId =
            new MutableLiveData<>(
                    null
            );



    public CreateUserViewModel(
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


    public LiveData<List<AdminRoleSummary>>
    getRoles()
    {
        return roles;
    }


    public LiveData<Boolean>
    getLoadingRoles()
    {
        return loadingRoles;
    }


    public LiveData<Boolean>
    getCreating()
    {
        return creating;
    }


    public LiveData<String>
    getError()
    {
        return error;
    }


    public LiveData<String>
    getCreatedUserId()
    {
        return createdUserId;
    }


    public void loadRoles()
    {
        if (Boolean.TRUE.equals(
                loadingRoles.getValue()
        ))
        {
            return;
        }


        loadingRoles.setValue(
                true
        );


        error.setValue(
                null
        );


        adminUserRepository
                .loadRoles(
                        new AdminUserRepository
                                .RoleCallback()
                        {
                            @Override

                            public void onSuccess(
                                    List<AdminRoleSummary> result)
                            {
                                loadingRoles.setValue(
                                        false
                                );


                                roles.setValue(
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
                                loadingRoles.setValue(
                                        false
                                );


                                error.setValue(
                                        friendlyRoleError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }


    /**
     * Validates the form and forwards a requested account creation to the trusted admin source.
     * Firebase account creation and profile persistence happen remotely; success is emitted only from the backend callback.
     */
    public void createUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String roleId)
    {
        if (Boolean.TRUE.equals(
                creating.getValue()
        ))
        {
            return;
        }


        String validationError =
                validate(
                        firstName,
                        lastName,
                        email,
                        password,
                        roleId
                );


        if (validationError != null)
        {
            error.setValue(
                    validationError
            );

            return;
        }


        creating.setValue(
                true
        );


        error.setValue(
                null
        );


        createdUserId.setValue(
                null
        );


        adminUserRepository
                .createUser(
                        firstName.trim(),
                        lastName.trim(),
                        email.trim(),
                        password,
                        roleId.trim(),
                        new AdminUserRepository
                                .CreateUserCallback()
                        {
                            @Override
                            public void onSuccess(
                                    String uid)
                            {
                                creating.setValue(
                                        false
                                );


                                createdUserId.setValue(
                                        uid
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                creating.setValue(
                                        false
                                );


                                error.setValue(
                                        friendlyCreateError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }



    public void clearCreatedUser()
    {
        createdUserId.setValue(
                null
        );
    }


    private String validate(
            String firstName,
            String lastName,
            String email,
            String password,
            String roleId)
    {
        if (!hasText(firstName))
        {
            return "First name is required.";
        }


        if (!hasText(lastName))
        {
            return "Last name is required.";
        }


        if (!hasText(email)
                ||
                !email.contains("@"))
        {
            return "Enter a valid email address.";
        }


        if (password == null
                ||
                password.length() < 6)
        {
            return "Temporary password must contain at least 6 characters.";
        }


        if (!hasText(roleId))
        {
            return "Select a role.";
        }


        return null;
    }



    private String friendlyRoleError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to load roles.";
        }


        String code =
                exception.getCode();


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You do not have permission to access the role catalogue.";
        }


        if ("ACCOUNT_INACTIVE".equals(
                code
        ))
        {
            return "Your account is inactive.";
        }


        if ("NETWORK_ERROR".equals(
                code
        ))
        {
            return "Unable to connect. Check your internet connection and try again.";
        }


        return "Unable to load roles at this time.";
    }



    private String friendlyCreateError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to create this user.";
        }


        String code =
                exception.getCode();


        if ("EMAIL_ALREADY_IN_USE".equals(
                code
        ))
        {
            return "A user with this email address already exists.";
        }


        if ("INVALID_EMAIL".equals(
                code
        ))
        {
            return "Enter a valid email address.";
        }


        if ("INVALID_PASSWORD".equals(
                code
        ))
        {
            return "The temporary password is invalid.";
        }


        if ("INVALID_ROLE".equals(
                code
        ))
        {
            return "The selected role is no longer available.";
        }


        if ("INVALID_USER_DATA".equals(
                code
        ))
        {
            return "Check the user details and try again.";
        }


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You do not have permission to create users.";
        }


        if ("ACCOUNT_INACTIVE".equals(
                code
        ))
        {
            return "Your account is inactive.";
        }


        if ("NETWORK_ERROR".equals(
                code
        ))
        {
            return "Unable to connect. Check your internet connection and try again.";
        }


        return "Unable to create this user at this time.";
    }



    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }
}