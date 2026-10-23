package com.medicationinventory.ui.users;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AdminUserRepository;


/**
 * Creates the User Detail ViewModel with its application dependencies.
 */
public class UserDetailViewModelFactory
        implements ViewModelProvider.Factory
{
    private final String
            userId;


    private final AdminUserRepository
            adminUserRepository;


    public UserDetailViewModelFactory(
            String userId,
            AdminUserRepository adminUserRepository)
    {
        this.userId =
                userId;

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
                UserDetailViewModel.class
        ))
        {
            return (T)
                    new UserDetailViewModel(
                            userId,
                            adminUserRepository
                    );
        }


        throw new IllegalArgumentException(
                "Unknown ViewModel class: "
                        + modelClass.getName()
        );
    }
}