package com.medicationinventory.ui.settings;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;




/**
 * Implements the Android screen for Settings and connects user actions to application state.
 */
public class SettingsActivity
        extends AppCompatActivity
{
    private SettingsViewModel
            viewModel;


    private View content;


    private ProgressBar permissionProgress;

    private ProgressBar saveProgress;


    private TextInputLayout
            warningDaysLayout;


    private TextInputEditText
            warningDaysInput;


    private MaterialButton
            saveButton;


    private boolean permissionGranted =
            false;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        if (!authenticationRepository.isSignedIn())
        {
            goToLogin();

            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_settings
        );


        bindViews();


        SettingsViewModelFactory factory =
                new SettingsViewModelFactory(
                        authenticationRepository,
                        application
                                .getAppContainer()
                                .getUserRepository(),
                        application
                                .getAppContainer()
                                .getInventorySettingsRepository()
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                SettingsViewModel.class
                        );


        observeViewModel();

        setupActions();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        userRepository
                .observeUser(
                        uid
                )
                .observe(
                        this,
                        this::applyPermission
                );
    }



    private void bindViews()
    {
        content =
                findViewById(
                        R.id.settingsContent
                );


        permissionProgress =
                findViewById(
                        R.id.settingsPermissionProgress
                );


        saveProgress =
                findViewById(
                        R.id.settingsSaveProgress
                );


        warningDaysLayout =
                findViewById(
                        R.id.settingsNearExpiryLayout
                );


        warningDaysInput =
                findViewById(
                        R.id.settingsNearExpiryInput
                );


        saveButton =
                findViewById(
                        R.id.settingsSaveButton
                );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.settingsBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        saveButton.setOnClickListener(
                view ->
                {
                    if (!permissionGranted)
                    {
                        return;
                    }


                    warningDaysLayout.setError(
                            null
                    );


                    String value =
                            warningDaysInput
                                    .getText() == null
                                    ? null
                                    : warningDaysInput
                                    .getText()
                                    .toString();


                    viewModel
                            .saveNearExpiryWarningDays(
                                    value
                            );
                }
        );
    }



    private void observeViewModel()
    {
        viewModel
                .getInventorySettings()
                .observe(
                        this,
                        this::renderSettings
                );


        viewModel
                .getActionState()
                .observe(
                        this,
                        this::renderActionState
                );
    }



    private void renderSettings(
            InventorySettings settings)
    {
        if (settings == null)
        {
            return;
        }


        String displayedValue =
                String.valueOf(
                        settings
                                .getNearExpiryWarningDays()
                );


        String currentInput =
                warningDaysInput
                        .getText() == null
                        ? ""
                        : warningDaysInput
                        .getText()
                        .toString();



        if (!warningDaysInput.hasFocus()
                ||
                currentInput.trim().isEmpty())
        {
            warningDaysInput.setText(
                    displayedValue
            );
        }
    }



    private void applyPermission(
            UserProfile profile)
    {
        if (profile == null)
        {

            if (permissionGranted)
            {
                permissionGranted =
                        false;


                updateInteractionState();


                Toast.makeText(
                        this,
                        "Your user profile is no longer available.",
                        Toast.LENGTH_SHORT
                ).show();


                finish();
            }


            return;
        }


        if (!profile.isActive()
                ||
                !profile.can(
                        Permission.MANAGE_SETTINGS
                ))
        {
            permissionGranted =
                    false;


            Toast.makeText(
                    this,
                    "You do not have permission to manage system settings.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        permissionGranted =
                true;


        permissionProgress.setVisibility(
                View.GONE
        );


        content.setVisibility(
                View.VISIBLE
        );


        updateInteractionState();
    }



    private void renderActionState(
            SettingsViewModel.SettingsActionState state)
    {
        if (state == null)
        {
            return;
        }


        boolean loading =
                state.getStatus()
                        ==
                        SettingsViewModel
                                .SettingsActionState
                                .Status
                                .LOADING;


        saveProgress.setVisibility(
                loading
                        ? View.VISIBLE
                        : View.GONE
        );


        updateInteractionState();


        if (loading)
        {
            warningDaysLayout.setError(
                    null
            );

            return;
        }


        if (state.getStatus()
                ==
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .SUCCESS)
        {
            Toast.makeText(
                    this,
                    state.isChanged()
                            ? "Inventory settings updated."
                            : "Inventory settings are already up to date.",
                    Toast.LENGTH_SHORT
            ).show();


            viewModel.clearActionState();

            return;
        }


        if (state.getStatus()
                ==
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .COMMITTED_REFRESH_ERROR)
        {
            Toast.makeText(
                    this,
                    state.getMessage(),
                    Toast.LENGTH_LONG
            ).show();


            viewModel.clearActionState();

            return;
        }


        if (state.getStatus()
                ==
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR)
        {
            warningDaysLayout.setError(
                    state.getMessage()
            );


            viewModel.clearActionState();
        }
    }


    private void updateInteractionState()
    {
        SettingsViewModel.SettingsActionState state =
                viewModel == null
                        ? null
                        : viewModel
                        .getActionState()
                        .getValue();


        boolean loading =
                state != null
                        &&
                        state.getStatus()
                                ==
                                SettingsViewModel
                                        .SettingsActionState
                                        .Status
                                        .LOADING;


        boolean enabled =
                permissionGranted
                        &&
                        !loading;


        warningDaysLayout.setEnabled(
                enabled
        );


        warningDaysInput.setEnabled(
                enabled
        );


        saveButton.setEnabled(
                enabled
        );
    }


    private void goToLogin()
    {
        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }
}