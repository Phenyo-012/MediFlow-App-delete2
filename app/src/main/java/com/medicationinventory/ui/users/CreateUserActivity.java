package com.medicationinventory.ui.users;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.dto.AdminRoleSummary;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;

import java.util.List;



/**
 * Collects a new employee email, initial role, and explicit permission selections, then submits them through CreateUserViewModel. The screen renders server-provided role and permission catalogues and displays creation outcomes; the trusted backend remains responsible for creating the Firebase account and validating administrative authority.
 */
public class CreateUserActivity
        extends AppCompatActivity
{
    private CreateUserViewModel
            viewModel;


    private TextInputEditText
            firstNameInput;

    private TextInputEditText
            lastNameInput;

    private TextInputEditText
            emailInput;

    private TextInputEditText
            passwordInput;


    private MaterialButton
            roleButton;

    private MaterialButton
            submitButton;


    private ProgressBar
            roleProgress;


    private TextView
            errorText;


    private String
            selectedRoleId;


    private boolean
            permissionGranted =
            false;


    private boolean
            rolesRequested =
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


        String currentUserId =
                authenticationRepository
                        .getCurrentUserId();


        if (currentUserId == null
                ||
                currentUserId.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_create_user
        );


        bindViews();


        CreateUserViewModelFactory factory =
                new CreateUserViewModelFactory(
                        application
                                .getAppContainer()
                                .getAdminUserRepository()
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                CreateUserViewModel.class
                        );


        setupActions();

        observeViewModel();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        userRepository
                .observeUser(
                        currentUserId
                )
                .observe(
                        this,
                        this::applyPermission
                );
    }



    private void bindViews()
    {
        firstNameInput =
                findViewById(
                        R.id.createUserFirstNameInput
                );


        lastNameInput =
                findViewById(
                        R.id.createUserLastNameInput
                );


        emailInput =
                findViewById(
                        R.id.createUserEmailInput
                );


        passwordInput =
                findViewById(
                        R.id.createUserPasswordInput
                );


        roleButton =
                findViewById(
                        R.id.createUserRoleButton
                );


        submitButton =
                findViewById(
                        R.id.createUserSubmitButton
                );


        roleProgress =
                findViewById(
                        R.id.createUserRoleProgress
                );


        errorText =
                findViewById(
                        R.id.createUserErrorText
                );
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.createUserBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        roleButton.setOnClickListener(
                view ->
                        showRoleDialog()
        );


        submitButton.setOnClickListener(
                view ->
                        submitUser()
        );
    }



    private void observeViewModel()
    {
        viewModel
                .getRoles()
                .observe(
                        this,
                        roles ->
                        {
                            if (roles != null
                                    &&
                                    !roles.isEmpty())
                            {
                                roleButton.setEnabled(
                                        true
                                );
                            }
                        }
                );


        viewModel
                .getLoadingRoles()
                .observe(
                        this,
                        loading ->
                        {
                            boolean isLoading =
                                    Boolean.TRUE.equals(
                                            loading
                                    );


                            roleProgress.setVisibility(
                                    isLoading
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            roleButton.setEnabled(
                                    permissionGranted
                                            &&
                                            !isLoading
                            );


                            if (isLoading)
                            {
                                roleButton.setText(
                                        "Loading Roles..."
                                );
                            }
                            else if (selectedRoleId == null)
                            {
                                roleButton.setText(
                                        "Select Role"
                                );
                            }
                        }
                );


        viewModel
                .getError()
                .observe(
                        this,
                        message ->
                        {
                            boolean hasError =
                                    message != null
                                            &&
                                            !message.trim()
                                                    .isEmpty();


                            errorText.setText(
                                    hasError
                                            ? message
                                            : ""
                            );


                            errorText.setVisibility(
                                    hasError
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        }
                );

        viewModel
                .getCreating()
                .observe(
                        this,
                        creating ->
                        {
                            boolean isCreating =
                                    Boolean.TRUE.equals(
                                            creating
                                    );


                            submitButton.setEnabled(
                                    permissionGranted
                                            &&
                                            selectedRoleId != null
                                            &&
                                            !isCreating
                            );


                            roleButton.setEnabled(
                                    permissionGranted
                                            &&
                                            !isCreating
                                            &&
                                            !Boolean.TRUE.equals(
                                                    viewModel
                                                            .getLoadingRoles()
                                                            .getValue()
                                            )
                            );


                            firstNameInput.setEnabled(
                                    !isCreating
                            );

                            lastNameInput.setEnabled(
                                    !isCreating
                            );

                            emailInput.setEnabled(
                                    !isCreating
                            );

                            passwordInput.setEnabled(
                                    !isCreating
                            );


                            submitButton.setText(
                                    isCreating
                                            ? "Creating User..."
                                            : "Create User"
                            );
                        }
                );

        viewModel
                .getCreatedUserId()
                .observe(
                        this,
                        uid ->
                        {
                            if (uid == null
                                    ||
                                    uid.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    "User created successfully.",
                                    Toast.LENGTH_SHORT
                            ).show();


                            Intent resultIntent =
                                    new Intent();


                            resultIntent.putExtra(
                                    "created_user_id",
                                    uid
                            );


                            setResult(
                                    RESULT_OK,
                                    resultIntent
                            );


                            viewModel.clearCreatedUser();


                            finish();
                        }
                );
    }


    private void applyPermission(
            UserProfile profile)
    {
        if (profile == null)
        {
            return;
        }


        if (!profile.isActive()
                ||
                !profile.can(
                        Permission.CREATE_USERS
                ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to create users.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        permissionGranted =
                true;

        submitButton.setEnabled(
                selectedRoleId != null
                        &&
                        !Boolean.TRUE.equals(
                                viewModel
                                        .getCreating()
                                        .getValue()
                        )
        );

        roleButton.setEnabled(
                true
        );


        if (!rolesRequested)
        {
            rolesRequested =
                    true;


            viewModel.loadRoles();
        }
    }



    private void showRoleDialog()
    {
        if (!permissionGranted)
        {
            return;
        }


        List<AdminRoleSummary> roles =
                viewModel
                        .getRoles()
                        .getValue();


        if (roles == null
                ||
                roles.isEmpty())
        {
            Toast.makeText(
                    this,
                    "Roles are not available yet.",
                    Toast.LENGTH_SHORT
            ).show();


            viewModel.loadRoles();

            return;
        }


        String[] labels =
                new String[
                        roles.size()
                        ];


        int checkedIndex =
                -1;


        for (int index = 0;
             index < roles.size();
             index++)
        {
            AdminRoleSummary role =
                    roles.get(
                            index
                    );


            labels[index] =
                    role.getRoleName();


            if (role.getRoleId().equals(
                    selectedRoleId
            ))
            {
                checkedIndex =
                        index;
            }
        }


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Select Role"
                )
                .setSingleChoiceItems(
                        labels,
                        checkedIndex,
                        (dialog, which) ->
                        {
                            AdminRoleSummary selectedRole =
                                    roles.get(
                                            which
                                    );


                            selectedRoleId =
                                    selectedRole
                                            .getRoleId();


                            roleButton.setText(
                                    selectedRole
                                            .getRoleName()
                            );

                            submitButton.setEnabled(
                                    permissionGranted
                                            &&
                                            !Boolean.TRUE.equals(
                                                    viewModel
                                                            .getCreating()
                                                            .getValue()
                                            )
                            );


                            dialog.dismiss();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }


    private void submitUser()
    {
        if (!permissionGranted)
        {
            return;
        }


        if (selectedRoleId == null
                ||
                selectedRoleId.trim().isEmpty())
        {
            Toast.makeText(
                    this,
                    "Select a role.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        String firstName =
                textOf(
                        firstNameInput
                );


        String lastName =
                textOf(
                        lastNameInput
                );


        String email =
                textOf(
                        emailInput
                );


        String password =
                textOf(
                        passwordInput
                );


        viewModel.createUser(
                firstName,
                lastName,
                email,
                password,
                selectedRoleId
        );
    }



    private String textOf(
            TextInputEditText input)
    {
        if (input == null
                ||
                input.getText() == null)
        {
            return "";
        }


        return input
                .getText()
                .toString()
                .trim();
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