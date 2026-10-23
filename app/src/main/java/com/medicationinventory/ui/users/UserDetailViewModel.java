package com.medicationinventory.ui.users;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.remote.backend.TrustedUserAdminDataSource;
import com.medicationinventory.data.repository.AdminUserRepository;
import com.medicationinventory.model.dto.AdminUserDetail;
import com.medicationinventory.model.dto.AdminRoleSummary;
import com.medicationinventory.model.dto.AdminPermissionSummary;

import java.util.Collections;
import java.util.List;



/**
 * Loads a selected user and the backend role/permission catalogues, then exposes account, role, permission, and TOTP-recovery actions as screen state. Mutation outcomes are passed through from AdminUserRepository, including backend rejection and committed-but-refresh-failed cases, so the UI can distinguish them.
 */
public class UserDetailViewModel
        extends ViewModel
{
    private final String
            userId;


    private final AdminUserRepository
            adminUserRepository;


    private final MutableLiveData<AdminUserDetail>
            user =
            new MutableLiveData<>();


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

    private final MutableLiveData<Boolean>
            updatingAccount =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<String>
            actionMessage =
            new MutableLiveData<>(
                    null
            );


    private final MutableLiveData<String>
            actionError =
            new MutableLiveData<>(
                    null
            );

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
            updatingRole =
            new MutableLiveData<>(
                    false
            );

    private final MutableLiveData<List<AdminPermissionSummary>>
            availablePermissions =
            new MutableLiveData<>(
                    Collections.emptyList()
            );


    private final MutableLiveData<Boolean>
            loadingPermissions =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<Boolean>
            updatingPermission =
            new MutableLiveData<>(
                    false
            );


    private final MutableLiveData<String>
            permissionActionError =
            new MutableLiveData<>(
                    null
            );


    private final MutableLiveData<String>
            permissionActionMessage =
            new MutableLiveData<>(
                    null
            );

    private final MutableLiveData<String>
            roleActionError =
            new MutableLiveData<>(
                    null
            );


    private final MutableLiveData<String>
            roleActionMessage =
            new MutableLiveData<>(
                    null
            );



    public UserDetailViewModel(
            String userId,
            AdminUserRepository adminUserRepository)
    {
        if (userId == null
                ||
                userId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        if (adminUserRepository == null)
        {
            throw new IllegalArgumentException(
                    "Admin user repository is required."
            );
        }


        this.userId =
                userId.trim();


        this.adminUserRepository =
                adminUserRepository;
    }


    public LiveData<AdminUserDetail>
    getUser()
    {
        return user;
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

    public LiveData<Boolean>
    getUpdatingAccount()
    {
        return updatingAccount;
    }


    public LiveData<String>
    getActionMessage()
    {
        return actionMessage;
    }


    public LiveData<String>
    getActionError()
    {
        return actionError;
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
    getUpdatingRole()
    {
        return updatingRole;
    }


    public LiveData<String>
    getRoleActionError()
    {
        return roleActionError;
    }


    public LiveData<String>
    getRoleActionMessage()
    {
        return roleActionMessage;
    }

    public LiveData<List<AdminPermissionSummary>>
    getAvailablePermissions()
    {
        return availablePermissions;
    }


    public LiveData<Boolean>
    getLoadingPermissions()
    {
        return loadingPermissions;
    }


    public LiveData<Boolean>
    getUpdatingPermission()
    {
        return updatingPermission;
    }


    public LiveData<String>
    getPermissionActionError()
    {
        return permissionActionError;
    }


    public LiveData<String>
    getPermissionActionMessage()
    {
        return permissionActionMessage;
    }

    /** Requests the active/inactive state change from the backend and updates screen state from its result. */
    public void setActiveState(
            boolean active)
    {
        if (Boolean.TRUE.equals(
                updatingAccount.getValue()
        ))
        {
            return;
        }


        updatingAccount.setValue(
                true
        );


        actionMessage.setValue(
                null
        );


        actionError.setValue(
                null
        );


        adminUserRepository
                .setUserActiveState(
                        userId,
                        active,
                        new AdminUserRepository.MutationCallback()
                        {
                            @Override

                            public void onSuccess(
                                    boolean changed)
                            {
                                updatingAccount.setValue(
                                        false
                                );


                                actionMessage.setValue(
                                        active
                                                ? (
                                                changed
                                                ? "Account activated."
                                                : "Account is already active."
                                        )
                                                : (
                                                changed
                                                ? "Account deactivated."
                                                : "Account is already inactive."
                                        )
                                );


                                /*
                                 * Reload authoritative detail
                                 * after the mutation succeeds.
                                 */
                                load();
                            }


                            @Override

                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                updatingAccount.setValue(
                                        false
                                );


                                actionError.setValue(
                                        friendlyMutationError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }


    private String friendlyMutationError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to update this account.";
        }


        String code =
                exception.getCode();


        if ("SELF_DEACTIVATION_NOT_ALLOWED".equals(
                code
        ))
        {
            return "You cannot deactivate your own signed-in account.";
        }


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You do not have permission to change account status.";
        }


        if ("USER_NOT_FOUND".equals(
                code
        ))
        {
            return "This user account could not be found.";
        }


        if ("ACCOUNT_INACTIVE".equals(
                code
        ))
        {
            return "Your administrator account is inactive.";
        }


        if ("NETWORK_ERROR".equals(
                code
        ))
        {
            return "Unable to connect. Check your internet connection and try again.";
        }


        return "Unable to update this account at this time.";
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


        roleActionError.setValue(
                null
        );


        adminUserRepository
                .loadRoles(
                        new AdminUserRepository.RoleCallback()
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


                                roleActionError.setValue(
                                        friendlyRoleError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }

    /** Requests a role change; the trusted service enforces the administrator permission and role constraints. */
    public void setRole(
            String roleId)
    {
        if (Boolean.TRUE.equals(
                updatingRole.getValue()
        ))
        {
            return;
        }


        if (roleId == null
                ||
                roleId.trim().isEmpty())
        {
            roleActionError.setValue(
                    "Please select a role."
            );

            return;
        }


        updatingRole.setValue(
                true
        );


        roleActionError.setValue(
                null
        );


        roleActionMessage.setValue(
                null
        );


        adminUserRepository
                .setUserRole(
                        userId,
                        roleId.trim(),
                        new AdminUserRepository.MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed)
                            {
                                updatingRole.setValue(
                                        false
                                );


                                if (changed)
                                {
                                    roleActionMessage.setValue(
                                            "User role updated."
                                    );
                                }
                                else
                                {
                                    roleActionMessage.setValue(
                                            "The user already has this role."
                                    );
                                }



                                load();
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                updatingRole.setValue(
                                        false
                                );


                                roleActionError.setValue(
                                        friendlyRoleMutationError(
                                                exception
                                        )
                                );
                            }
                        }
                );
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
            return "You do not have permission to manage user roles.";
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

    private String friendlyRoleMutationError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to change the user's role.";
        }


        String code =
                exception.getCode();


        if ("SELF_ROLE_CHANGE_NOT_ALLOWED".equals(
                code
        ))
        {
            return "You cannot change your own role.";
        }


        if ("ROLE_NOT_FOUND".equals(
                code
        ))
        {
            return "The selected role no longer exists.";
        }


        if ("USER_NOT_FOUND".equals(
                code
        ))
        {
            return "This user account could not be found.";
        }


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You no longer have permission to manage user roles.";
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


        return "Unable to change the user's role at this time.";
    }


    public void loadPermissions()
    {
        if (Boolean.TRUE.equals(
                loadingPermissions.getValue()
        ))
        {
            return;
        }


        loadingPermissions.setValue(
                true
        );


        permissionActionError.setValue(
                null
        );


        adminUserRepository
                .loadPermissions(
                        new AdminUserRepository
                                .PermissionCallback()
                        {
                            @Override
                            public void onSuccess(
                                    List<AdminPermissionSummary> permissions)
                            {
                                loadingPermissions.setValue(
                                        false
                                );


                                availablePermissions.setValue(
                                        permissions == null
                                                ? Collections.emptyList()
                                                : permissions
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                loadingPermissions.setValue(
                                        false
                                );


                                permissionActionError.setValue(
                                        friendlyPermissionError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }


    /** Requests one explicit permission change through the trusted user administration endpoint. */
    public void setPermission(
            String permissionId,
            boolean granted)
    {
        if (Boolean.TRUE.equals(
                updatingPermission.getValue()
        ))
        {
            return;
        }


        if (permissionId == null
                ||
                permissionId.trim().isEmpty())
        {
            permissionActionError.setValue(
                    "A permission must be selected."
            );

            return;
        }


        updatingPermission.setValue(
                true
        );


        permissionActionMessage.setValue(
                null
        );


        permissionActionError.setValue(
                null
        );


        String cleanPermissionId =
                permissionId.trim();


        adminUserRepository
                .setUserPermission(
                        userId,
                        cleanPermissionId,
                        granted,
                        new AdminUserRepository
                                .MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed)
                            {
                                updatingPermission.setValue(
                                        false
                                );


                                if (changed)
                                {
                                    permissionActionMessage.setValue(
                                            granted
                                                    ? "Permission granted."
                                                    : "Permission revoked."
                                    );
                                }
                                else
                                {
                                    permissionActionMessage.setValue(
                                            granted
                                                    ? "The user already has this permission."
                                                    : "The user already does not have this permission."
                                    );
                                }


                                load();
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                updatingPermission.setValue(
                                        false
                                );


                                permissionActionError.setValue(
                                        friendlyPermissionMutationError(
                                                exception
                                        )
                                );
                            }
                        }
                );
    }


    private String friendlyPermissionError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to load available permissions.";
        }


        String code =
                exception.getCode();


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You do not have permission to manage user permissions.";
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


        return "Unable to load available permissions at this time.";
    }


    private String friendlyPermissionMutationError(
            TrustedUserAdminDataSource
                    .UserAdminRemoteException exception)
    {
        if (exception == null)
        {
            return "Unable to update this permission.";
        }


        String code =
                exception.getCode();


        if ("SELF_PERMISSION_CHANGE_NOT_ALLOWED".equals(
                code
        ))
        {
            return "You cannot change your own permissions.";
        }


        if ("INVALID_PERMISSION".equals(
                code
        ))
        {
            return "The selected permission is not valid.";
        }


        if ("PERMISSION_ID_REQUIRED".equals(
                code
        ))
        {
            return "A permission must be selected.";
        }


        if ("USER_ROLE_REQUIRED".equals(
                code
        ))
        {
            return "This user does not have a valid assigned role.";
        }


        if ("ROLE_NOT_FOUND".equals(
                code
        ))
        {
            return "The user's assigned role could not be found.";
        }


        if ("USER_NOT_FOUND".equals(
                code
        ))
        {
            return "This user account could not be found.";
        }


        if ("PERMISSION_DENIED".equals(
                code
        ))
        {
            return "You do not have permission to manage user permissions.";
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


        return "Unable to update this permission at this time.";
    }


    /** Loads the selected profile from the backend and exposes missing-user or transport failures as UI state. */
    public void load()
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
                .loadUserDetail(
                        userId,
                        new AdminUserRepository.DetailCallback()
                        {
                            @Override
                            public void onSuccess(
                                    AdminUserDetail result)
                            {
                                loading.setValue(
                                        false
                                );


                                user.setValue(
                                        result
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
            return "Unable to load this user.";
        }


        String code =
                exception.getCode();


        if ("USER_NOT_FOUND".equals(
                code
        ))
        {
            return "This user account could not be found.";
        }


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


        if ("NETWORK_ERROR".equals(
                code
        ))
        {
            return "Unable to connect. Check your internet connection and try again.";
        }


        return "Unable to load this user at this time.";
    }
}