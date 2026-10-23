package com.medicationinventory.ui.users;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AdminUserRepository;



/**
 * Creates the User Management ViewModel with its application dependencies.
 */
public class UserManagementViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AdminUserRepository
            adminUserRepository;


    public UserManagementViewModelFactory(
            AdminUserRepository adminUserRepository)
    {
        this.adminUserRepository =
                adminUserRepository;
    }


    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(
            @NonNull Class<T> modelClass)
    {
        if (modelClass.isAssignableFrom(
                UserManagementViewModel.class
        ))
        {
            return (T)
                    new UserManagementViewModel(
                            adminUserRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}