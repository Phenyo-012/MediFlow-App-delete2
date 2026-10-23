package com.medicationinventory.data.repository;

import com.medicationinventory.data.remote.backend.TrustedUserAdminDataSource;
import com.medicationinventory.model.dto.AdminUserSummary;
import com.medicationinventory.model.dto.AdminUserDetail;
import com.medicationinventory.model.dto.AdminRoleSummary;
import com.medicationinventory.model.dto.AdminPermissionSummary;

import java.util.List;



/**
 * Delegates user, role, and permission administration requests to the trusted backend data source.
 */
    /**
     * Loads administrative user data by delegating every read and mutation to the trusted backend source.
     * This class contains no local privilege decision; callers present results while backend authorization remains authoritative.
     */
public class AdminUserRepository
{
    private final TrustedUserAdminDataSource
            trustedUserAdminDataSource;


    public AdminUserRepository(
            TrustedUserAdminDataSource trustedUserAdminDataSource)
    {
        if (trustedUserAdminDataSource == null)
        {
            throw new IllegalArgumentException(
                    "Trusted user administration data source is required."
            );
        }


        this.trustedUserAdminDataSource =
                trustedUserAdminDataSource;
    }


    /** Loads user summaries from the protected backend endpoint. */
    public void loadUsers(
            Callback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User repository callback is required."
            );
        }


        trustedUserAdminDataSource
                .loadUsers(
                        new TrustedUserAdminDataSource.Callback()
                        {
                            @Override

                            public void onSuccess(
                                    List<AdminUserSummary> users)
                            {
                                callback.onSuccess(
                                        users
                                );
                            }


                            @Override

                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }


    public interface Callback
    {
        void onSuccess(
                List<AdminUserSummary> users
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }

    public void loadUserDetail(
            String uid,
            DetailCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User detail repository callback is required."
            );
        }


        trustedUserAdminDataSource
                .loadUserDetail(
                        uid,
                        new TrustedUserAdminDataSource.DetailCallback()
                        {
                            @Override
                            public void onSuccess(
                                    AdminUserDetail user)
                            {
                                callback.onSuccess(
                                        user
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface DetailCallback
    {
        void onSuccess(
                AdminUserDetail user
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }

    /**
     * Changes active state through the backend, which enforces the administrator permission and account rules.
     * @param uid target Firebase UID
     * @param active requested state
     * @param callback receives whether a change occurred or a backend error
     */
    public void setUserActiveState(
            String uid,
            boolean active,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User mutation repository callback is required."
            );
        }


        trustedUserAdminDataSource
                .setUserActiveState(
                        uid,
                        active,
                        new TrustedUserAdminDataSource.MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed)
                            {
                                callback.onSuccess(
                                        changed
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface MutationCallback
    {
        void onSuccess(
                boolean changed
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }


    public void loadRoles(
            RoleCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Role repository callback is required."
            );
        }


        trustedUserAdminDataSource
                .loadRoles(
                        new TrustedUserAdminDataSource.RoleCallback()
                        {
                            @Override
                            public void onSuccess(
                                    List<AdminRoleSummary> roles)
                            {
                                callback.onSuccess(
                                        roles
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public void loadPermissions(
            PermissionCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Permission callback is required."
            );
        }


        trustedUserAdminDataSource
                .loadPermissions(
                        new TrustedUserAdminDataSource
                                .PermissionCallback()
                        {
                            @Override
                            public void onSuccess(
                                    List<AdminPermissionSummary> permissions)
                            {
                                callback.onSuccess(
                                        permissions
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }


    public void setUserPermission(
            String uid,
            String permissionId,
            boolean granted,
            MutationCallback callback)
    {
        trustedUserAdminDataSource
                .setUserPermission(
                        uid,
                        permissionId,
                        granted,
                        new TrustedUserAdminDataSource
                                .MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed)
                            {
                                callback.onSuccess(
                                        changed
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface PermissionCallback
    {
        void onSuccess(
                List<AdminPermissionSummary> permissions
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }

    public void setUserRole(
            String uid,
            String roleId,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Mutation callback is required."
            );
        }


        trustedUserAdminDataSource
                .setUserRole(
                        uid,
                        roleId,
                        new TrustedUserAdminDataSource.MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed)
                            {
                                callback.onSuccess(
                                        changed
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }


    public void createUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String roleId,
            CreateUserCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Create user callback is required."
            );
        }


        trustedUserAdminDataSource
                .createUser(
                        firstName,
                        lastName,
                        email,
                        password,
                        roleId,
                        new TrustedUserAdminDataSource
                                .CreateUserCallback()
                        {
                            @Override
                            public void onSuccess(
                                    String uid)
                            {
                                callback.onSuccess(
                                        uid
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedUserAdminDataSource
                                            .UserAdminRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface CreateUserCallback
    {
        void onSuccess(
                String uid
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }

    public interface RoleCallback
    {
        void onSuccess(
                List<AdminRoleSummary> roles
        );


        void onError(
                TrustedUserAdminDataSource
                        .UserAdminRemoteException exception
        );
    }
}