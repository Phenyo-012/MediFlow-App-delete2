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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.users.UserDetailActivity;




/**
 * Displays the trusted backend user list and gates administrative navigation according to the current profile permissions. Selecting a row opens its user-detail screen; the server still rechecks authorization for every privileged operation.
 */
public class UserManagementActivity
        extends AppCompatActivity
{
    private UserManagementViewModel
            viewModel;


    private AdminUserAdapter
            adapter;


    private ProgressBar
            progressBar;


    private RecyclerView
            recyclerView;


    private TextView
            errorText;


    private TextView
            emptyText;


    private TextView
            userCountText;


    private MaterialButton
            refreshButton;


    private boolean
            permissionGranted =
            false;


    private boolean
            loadedOnce =
            false;

    private MaterialButton
            createUserButton;

    private ActivityResultLauncher<Intent>
            createUserLauncher;

    private boolean
            canCreateUsers =
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


        String userId =
                authenticationRepository
                        .getCurrentUserId();


        if (userId == null
                ||
                userId.trim().isEmpty())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_user_management
        );


        bindViews();

        setupList();

        setupViewModel(
                application
        );

        createUserLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result ->
                        {
                            if (result.getResultCode()
                                    == RESULT_OK)
                            {
                                if (permissionGranted)
                                {
                                    viewModel.refresh();
                                }
                            }
                        }
                );

        setupActions();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        userRepository
                .observeUser(
                        userId
                )
                .observe(
                        this,
                        this::applyPermission
                );
    }


    private void bindViews()
    {
        progressBar =
                findViewById(
                        R.id.userManagementProgress
                );


        recyclerView =
                findViewById(
                        R.id.userManagementRecyclerView
                );


        errorText =
                findViewById(
                        R.id.userManagementErrorText
                );


        emptyText =
                findViewById(
                        R.id.userManagementEmptyText
                );


        userCountText =
                findViewById(
                        R.id.userCountText
                );


        refreshButton =
                findViewById(
                        R.id.userRefreshButton
                );

        createUserButton =
                findViewById(
                        R.id.userCreateButton
                );
    }



    private void setupList()
    {
        adapter =
                new AdminUserAdapter(
                        user ->
                        {
                            Intent intent =
                                    new Intent(
                                            this,
                                            UserDetailActivity.class
                                    );


                            intent.putExtra(
                                    UserDetailActivity.EXTRA_USER_ID,
                                    user.getUid()
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        recyclerView.setLayoutManager(
                new LinearLayoutManager(
                        this
                )
        );


        recyclerView.setAdapter(
                adapter
        );
    }



    private void setupViewModel(
            MedicationInventoryApplication application)
    {
        UserManagementViewModelFactory factory =
                new UserManagementViewModelFactory(
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
                                UserManagementViewModel.class
                        );


        viewModel
                .getUsers()
                .observe(
                        this,
                        users ->
                        {
                            adapter.setUsers(
                                    users
                            );


                            int count =
                                    users == null
                                            ? 0
                                            : users.size();


                            userCountText.setText(
                                    count
                                            + (
                                            count == 1
                                                    ? " user"
                                                    : " users"
                                    )
                            );


                            emptyText.setVisibility(
                                    count == 0
                                            &&
                                            !Boolean.TRUE.equals(
                                                    viewModel
                                                            .getLoading()
                                                            .getValue()
                                            )
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            recyclerView.setVisibility(
                                    count > 0
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        }
                );


        viewModel
                .getLoading()
                .observe(
                        this,
                        loading ->
                        {
                            boolean isLoading =
                                    Boolean.TRUE.equals(
                                            loading
                                    );


                            progressBar.setVisibility(
                                    isLoading
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            refreshButton.setEnabled(
                                    !isLoading
                            );


                            if (isLoading)
                            {
                                emptyText.setVisibility(
                                        View.GONE
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
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.userManagementBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        refreshButton.setOnClickListener(
                view ->
                {
                    if (permissionGranted)
                    {
                        viewModel.refresh();
                    }
                }
        );

        createUserButton.setOnClickListener(
                view ->
                {
                    if (!canCreateUsers)
                    {
                        return;
                    }


                    createUserLauncher.launch(
                            new Intent(
                                    this,
                                    CreateUserActivity.class
                            )
                    );
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
                        Permission.VIEW_USERS
                ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to view users.",
                    Toast.LENGTH_SHORT
            ).show();


            finish();

            return;
        }


        permissionGranted =
                true;

        canCreateUsers =
                profile.can(
                        Permission.CREATE_USERS
                );


        createUserButton.setVisibility(
                canCreateUsers
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!loadedOnce)
        {
            loadedOnce =
                    true;


            viewModel.refresh();
        }
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