package com.medicationinventory.ui.users;


import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AdminUserRepository;



/**
 * Creates the Create User ViewModel with its application dependencies.
 */
public class CreateUserViewModelFactory
        implements ViewModelProvider.Factory
{
    private final AdminUserRepository
            adminUserRepository;



    public CreateUserViewModelFactory(
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
                CreateUserViewModel.class
        ))
        {
            return (T)
                    new CreateUserViewModel(
                            adminUserRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}