package com.medicationinventory.ui.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.medicationinventory.data.repository.AuthenticationRepository;



/**
 * Creates the Login ViewModel with its application dependencies.
 */
public class LoginViewModelFactory implements ViewModelProvider.Factory
{
    private final AuthenticationRepository authenticationRepository;

    public LoginViewModelFactory(AuthenticationRepository authenticationRepository)
    {
        this.authenticationRepository =
                authenticationRepository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass)
    {

        if (modelClass.isAssignableFrom(LoginViewModel.class))
        {
            return (T) new LoginViewModel(authenticationRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}