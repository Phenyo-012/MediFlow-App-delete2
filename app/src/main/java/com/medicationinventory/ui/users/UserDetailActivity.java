package com.medicationinventory.ui.users;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;

import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.data.remote.backend.TrustedTotpRecoveryDataSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.UUID;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.dto.AdminUserDetail;
import com.medicationinventory.model.dto.AdminRoleSummary;
import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.model.dto.AdminPermissionSummary;

import com.medicationinventory.ui.auth.LoginActivity;

import java.util.List;
import java.util.Locale;




/**
 * Presents one employee profile and its administrative actions: active state, role, explicit permissions, and optional TOTP recovery. The Activity gathers confirmation and a recovery reason/request ID, while UserDetailViewModel and the trusted backend perform the mutation; UI visibility is not an authorization boundary.
 */
public class UserDetailActivity
        extends AppCompatActivity
{
    public static final String EXTRA_USER_ID =
            "user_id";


    private UserDetailViewModel
            viewModel;


    private ProgressBar
            progressBar;


    private View
            content;


    private TextView
            errorText;


    private MaterialButton
            retryButton;


    private TextView
            nameText;

    private TextView
            selfText;

    private TextView
            emailText;

    private TextView
            roleText;

    private MaterialButton
            managePermissionsButton;


    private boolean
            canManageUserPermissions =
            false;


    private boolean
            permissionsRequestedOnce =
            false;

    private TextView
            statusText;

    private TextView
            permissionCountText;

    private TextView
            noPermissionsText;


    private ChipGroup
            permissionGroup;


    private boolean
            permissionGranted =
            false;


    private boolean
            loadedOnce =
            false;

    private MaterialButton
            accountStatusButton;

    private MaterialButton
            changeRoleButton;

    private TextView totpRecoveryStatus;
    private MaterialButton totpRecoveryResetButton;
    private TrustedTotpRecoveryDataSource totpRecoveryApi;
    private AuthenticationRepository authenticationRepository;
    private String administratorUid;
    private String recoveryTargetUid;
    private boolean totpStatusRequested;
    private boolean totpEnrolled;
    private boolean totpResetBusy;



    private boolean
            canChangeUserRole =
            false;


    private boolean
            rolesRequestedOnce =
            false;

    private boolean
            canChangeAccountStatus =
            false;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        String targetUserId =
                getIntent()
                        .getStringExtra(
                                EXTRA_USER_ID
                        );


        if (targetUserId == null
                ||
                targetUserId.trim().isEmpty())
        {
            finish();

            return;
        }


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        authenticationRepository =
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


        administratorUid = currentUserId;
        recoveryTargetUid = targetUserId.trim();
        totpRecoveryApi = application.getAppContainer().getTrustedTotpRecoveryDataSource();

        setContentView(
                R.layout.activity_user_detail
        );


        bindViews();


        UserDetailViewModelFactory factory =
                new UserDetailViewModelFactory(
                        targetUserId,
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
                                UserDetailViewModel.class
                        );


        observeViewModel();

        setupActions();


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
        progressBar =
                findViewById(
                        R.id.userDetailProgress
                );


        content =
                findViewById(
                        R.id.userDetailContent
                );


        errorText =
                findViewById(
                        R.id.userDetailErrorText
                );


        retryButton =
                findViewById(
                        R.id.userDetailRetryButton
                );


        nameText =
                findViewById(
                        R.id.userDetailNameText
                );


        selfText =
                findViewById(
                        R.id.userDetailSelfText
                );


        emailText =
                findViewById(
                        R.id.userDetailEmailText
                );


        roleText =
                findViewById(
                        R.id.userDetailRoleText
                );


        statusText =
                findViewById(
                        R.id.userDetailStatusText
                );


        permissionCountText =
                findViewById(
                        R.id.userDetailPermissionCountText
                );


        permissionGroup =
                findViewById(
                        R.id.userDetailPermissionGroup
                );


        noPermissionsText =
                findViewById(
                        R.id.userDetailNoPermissionsText
                );

        accountStatusButton =
                findViewById(
                        R.id.userDetailAccountStatusButton
                );

        changeRoleButton =
                findViewById(
                        R.id.userDetailChangeRoleButton
                );

        managePermissionsButton =
                findViewById(
                        R.id.userDetailManagePermissionsButton
                );

        totpRecoveryStatus = findViewById(R.id.userDetailTotpRecoveryStatus);
        totpRecoveryResetButton = findViewById(R.id.userDetailTotpRecoveryResetButton);
    }



    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.userDetailBackButton
                );


        backButton.setOnClickListener(
                view ->
                        finish()
        );


        retryButton.setOnClickListener(
                view ->
                {
                    if (permissionGranted)
                    {
                        viewModel.load();
                    }
                }
        );

        accountStatusButton.setOnClickListener(
                view ->
                {
                    AdminUserDetail user =
                            viewModel
                                    .getUser()
                                    .getValue();


                    if (user == null
                            ||
                            user.isSelf()
                            ||
                            !canChangeAccountStatus)
                    {
                        return;
                    }


                    boolean newActiveState =
                            !user.isActive();


                    String title =
                            newActiveState
                                    ? "Activate account?"
                                    : "Deactivate account?";


                    String message =
                            newActiveState
                                    ? "This user will regain authorised access according to their current role and permissions."
                                    : "This user will no longer be authorised to access protected application functionality.";


                    String confirmText =
                            newActiveState
                                    ? "Activate"
                                    : "Deactivate";


                    new MaterialAlertDialogBuilder(
                            this
                    )
                            .setTitle(
                                    title
                            )
                            .setMessage(
                                    message
                            )
                            .setNegativeButton(
                                    "Cancel",
                                    null
                            )
                            .setPositiveButton(
                                    confirmText,
                                    (dialog, which) ->
                                            viewModel
                                                    .setActiveState(
                                                            newActiveState
                                                    )
                            )
                            .show();
                }
        );

        changeRoleButton.setOnClickListener(
                view ->
                        showRoleSelectionDialog()
        );

        managePermissionsButton.setOnClickListener(
                view ->
                        showPermissionCatalogueDialog()
        );

        totpRecoveryResetButton.setOnClickListener(v -> showTotpRecoveryConfirmation());
        totpRecoveryStatus.setOnClickListener(v -> {
            if (!totpResetBusy) loadTotpRecoveryStatus();
        });
    }


    private void observeViewModel()
    {
        viewModel
                .getUser()
                .observe(
                        this,
                        this::renderUser
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


                            if (isLoading)
                            {
                                errorText.setVisibility(
                                        View.GONE
                                );


                                retryButton.setVisibility(
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
                                            !message.trim().isEmpty();


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


                            retryButton.setVisibility(
                                    hasError
                                            ? View.VISIBLE
                                            : View.GONE
                            );


                            if (hasError)
                            {
                                content.setVisibility(
                                        View.GONE
                                );
                            }
                        }
                );

        viewModel
                .getUpdatingAccount()
                .observe(
                        this,
                        updating ->
                        {
                            boolean isUpdating =
                                    Boolean.TRUE.equals(
                                            updating
                                    );


                            accountStatusButton.setEnabled(
                                    !isUpdating
                            );


                            if (isUpdating)
                            {
                                accountStatusButton.setText(
                                        "Updating..."
                                );
                            }
                            else
                            {
                                renderAccountStatusAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                );
                            }
                        }
                );

        viewModel
                .getActionMessage()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );

        viewModel
                .getActionError()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );

        viewModel
                .getRoles()
                .observe(
                        this,
                        roles ->
                        {
                            AdminUserDetail user =
                                    viewModel
                                            .getUser()
                                            .getValue();


                            if (user != null)
                            {
                                roleText.setText(
                                        "Role: "
                                                + roleDisplayName(
                                                user.getRoleId()
                                        )
                                );
                            }


                            renderRoleAction(
                                    user
                            );
                        }
                );

        viewModel
                .getLoadingRoles()
                .observe(
                        this,
                        loading ->
                                renderRoleAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                )
                );

        viewModel
                .getUpdatingRole()
                .observe(
                        this,
                        updating ->
                        {
                            renderRoleAction(
                                    viewModel
                                            .getUser()
                                            .getValue()
                            );


                            boolean isUpdatingRole =
                                    Boolean.TRUE.equals(
                                            updating
                                    );


                            if (isUpdatingRole)
                            {
                                accountStatusButton.setEnabled(
                                        false
                                );
                            }
                            else
                            {
                                renderAccountStatusAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                );
                            }
                        }
                );

        viewModel
                .getRoleActionMessage()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );

        viewModel
                .getRoleActionError()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );

        viewModel
                .getAvailablePermissions()
                .observe(
                        this,
                        permissions ->
                                renderPermissionAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                )
                );


        viewModel
                .getLoadingPermissions()
                .observe(
                        this,
                        loading ->
                                renderPermissionAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                )
                );


        viewModel
                .getUpdatingPermission()
                .observe(
                        this,
                        updating ->
                                renderPermissionAction(
                                        viewModel
                                                .getUser()
                                                .getValue()
                                )
                );

        viewModel
                .getPermissionActionMessage()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );


        viewModel
                .getPermissionActionError()
                .observe(
                        this,
                        message ->
                        {
                            if (message == null
                                    ||
                                    message.trim().isEmpty())
                            {
                                return;
                            }


                            Toast.makeText(
                                    this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
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

        canChangeAccountStatus =
                profile.can(
                        Permission.DEACTIVATE_USERS
                );

        canChangeUserRole =
                profile.can(
                        Permission.MANAGE_USER_ROLES
                );

        canChangeUserRole =
                profile.can(
                        Permission.MANAGE_USER_ROLES
                );

        canManageUserPermissions =
                profile.can(
                        Permission.MANAGE_USER_PERMISSIONS
                );

        renderTotpRecoveryButton();

        renderPermissionAction(
                viewModel
                        .getUser()
                        .getValue()
        );

        if (canManageUserPermissions
                &&
                !permissionsRequestedOnce)
        {
            permissionsRequestedOnce =
                    true;


            viewModel.loadPermissions();
        }

        renderAccountStatusAction(
                viewModel
                        .getUser()
                        .getValue()
        );

        renderRoleAction(
                viewModel
                        .getUser()
                        .getValue()
        );

        if (canChangeUserRole
                &&
                !rolesRequestedOnce)
        {
            rolesRequestedOnce =
                    true;


            viewModel.loadRoles();
        }


        if (!loadedOnce)
        {
            loadedOnce =
                    true;


            viewModel.load();
        }
    }


    private void renderUser(
            AdminUserDetail user)
    {
        if (user == null)
        {
            return;
        }


        content.setVisibility(
                View.VISIBLE
        );


        errorText.setVisibility(
                View.GONE
        );


        retryButton.setVisibility(
                View.GONE
        );


        nameText.setText(
                user.getFullName()
        );


        selfText.setVisibility(
                user.isSelf()
                        ? View.VISIBLE
                        : View.GONE
        );


        emailText.setText(
                "Email: "
                        + (
                        hasText(
                                user.getEmail()
                        )
                                ? user.getEmail()
                                : "Unavailable"
                )
        );


        roleText.setText(
                "Role: "
                        + roleDisplayName(
                        user.getRoleId()
                )
        );


        statusText.setText(
                "Status: "
                        + (
                        user.isActive()
                                ? "Active"
                                : "Inactive"
                )
        );


        renderPermissions(
                user.getPermissions()
        );


        renderAccountStatusAction(
                user
        );

        renderRoleAction(
                user
        );

        renderPermissionAction(
                user
        );

        if (!totpStatusRequested) {
            loadTotpRecoveryStatus();
        }
        renderTotpRecoveryButton();
    }


    private void renderAccountStatusAction(
            AdminUserDetail user)
    {
        if (user == null
                ||
                user.isSelf()
                ||
                !canChangeAccountStatus)
        {
            accountStatusButton.setVisibility(
                    View.GONE
            );

            return;
        }


        accountStatusButton.setVisibility(
                View.VISIBLE
        );


        accountStatusButton.setText(
                user.isActive()
                        ? "Deactivate Account"
                        : "Activate Account"
        );
    }


    private void renderPermissions(
            List<String> permissions)
    {
        permissionGroup.removeAllViews();


        int count =
                permissions == null
                        ? 0
                        : permissions.size();


        permissionCountText.setText(
                count
                        + (
                        count == 1
                                ? " permission"
                                : " permissions"
                )
        );


        noPermissionsText.setVisibility(
                count == 0
                        ? View.VISIBLE
                        : View.GONE
        );


        permissionGroup.setVisibility(
                count > 0
                        ? View.VISIBLE
                        : View.GONE
        );


        if (permissions == null)
        {
            return;
        }


        for (String permission : permissions)
        {
            Chip chip =
                    new Chip(
                            this
                    );


            chip.setText(
                    friendlyValue(
                            permission
                    )
            );


            chip.setClickable(
                    false
            );


            chip.setCheckable(
                    false
            );


            permissionGroup.addView(
                    chip
            );
        }
    }


    private String friendlyValue(
            String value)
    {
        if (!hasText(
                value
        ))
        {
            return "Not assigned";
        }


        String normalized =
                value.trim()
                        .replace(
                                '_',
                                ' '
                        )
                        .toLowerCase(Locale.ROOT);


        String[] words =
                normalized.split(
                        "\\s+"
                );


        StringBuilder builder =
                new StringBuilder();


        for (String word : words)
        {
            if (word.isEmpty())
            {
                continue;
            }


            if (builder.length() > 0)
            {
                builder.append(
                        ' '
                );
            }


            builder.append(
                    Character.toUpperCase(
                            word.charAt(
                                    0
                            )
                    )
            );


            if (word.length() > 1)
            {
                builder.append(
                        word.substring(
                                1
                        )
                );
            }
        }


        return builder.toString();
    }

    private void showRoleSelectionDialog()
    {
        AdminUserDetail user =
                viewModel
                        .getUser()
                        .getValue();


        if (user == null
                ||
                user.isSelf()
                ||
                !canChangeUserRole)
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
                    "Loading available roles...",
                    Toast.LENGTH_SHORT
            ).show();


            viewModel.loadRoles();

            return;
        }


        String[] roleNames =
                new String[
                        roles.size()
                        ];


        int currentIndex =
                -1;


        for (int index = 0;
             index < roles.size();
             index++)
        {
            AdminRoleSummary role =
                    roles.get(
                            index
                    );


            roleNames[index] =
                    role.getRoleName();


            if (role.getRoleId().equals(
                    user.getRoleId()
            ))
            {
                currentIndex =
                        index;
            }
        }


        final int[] selectedIndex =
                {
                        currentIndex
                };


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Change Role"
                )
                .setSingleChoiceItems(
                        roleNames,
                        currentIndex,
                        (dialog, which) ->
                                selectedIndex[0] =
                                        which
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Continue",
                        (dialog, which) ->
                        {
                            int index =
                                    selectedIndex[0];


                            if (index < 0
                                    ||
                                    index >= roles.size())
                            {
                                Toast.makeText(
                                        this,
                                        "Please select a role.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }


                            AdminRoleSummary selectedRole =
                                    roles.get(
                                            index
                                    );


                            if (selectedRole
                                    .getRoleId()
                                    .equals(
                                            user.getRoleId()
                                    ))
                            {
                                Toast.makeText(
                                        this,
                                        "The user already has this role.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }


                            confirmRoleChange(
                                    selectedRole
                            );
                        }
                )
                .show();
    }

    private void confirmRoleChange(
            AdminRoleSummary role)
    {
        if (role == null)
        {
            return;
        }


        String message =
                "Change this user's role to "
                        + role.getRoleName()
                        + "?\n\n"
                        + "Their effective permissions will be reset "
                        + "to the baseline permissions for this role.";


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Confirm Role Change"
                )
                .setMessage(
                        message
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Change Role",
                        (dialog, which) ->
                                viewModel.setRole(
                                        role.getRoleId()
                                )
                )
                .show();
    }


    private void renderRoleAction(
            AdminUserDetail user)
    {
        if (user == null
                ||
                user.isSelf()
                ||
                !canChangeUserRole)
        {
            changeRoleButton.setVisibility(
                    View.GONE
            );

            return;
        }


        changeRoleButton.setVisibility(
                View.VISIBLE
        );


        boolean loadingRoles =
                Boolean.TRUE.equals(
                        viewModel
                                .getLoadingRoles()
                                .getValue()
                );


        boolean updatingRole =
                Boolean.TRUE.equals(
                        viewModel
                                .getUpdatingRole()
                                .getValue()
                );


        boolean updatingAccount =
                Boolean.TRUE.equals(
                        viewModel
                                .getUpdatingAccount()
                                .getValue()
                );


        changeRoleButton.setEnabled(
                !loadingRoles
                        &&
                        !updatingRole
                        &&
                        !updatingAccount
        );


        if (updatingRole)
        {
            changeRoleButton.setText(
                    "Updating Role..."
            );
        }
        else if (loadingRoles)
        {
            changeRoleButton.setText(
                    "Loading Roles..."
            );
        }
        else
        {
            changeRoleButton.setText(
                    "Change Role"
            );
        }
    }


    private String roleDisplayName(
            String roleId)
    {
        List<AdminRoleSummary> roles =
                viewModel
                        .getRoles()
                        .getValue();


        if (roles != null
                &&
                roleId != null)
        {
            for (AdminRoleSummary role : roles)
            {
                if (roleId.equals(
                        role.getRoleId()
                ))
                {
                    return role.getRoleName();
                }
            }
        }


        return friendlyValue(
                roleId
        );
    }


    private void showPermissionCatalogueDialog()
    {
        AdminUserDetail user =
                viewModel
                        .getUser()
                        .getValue();


        if (user == null
                ||
                user.isSelf()
                ||
                !canManageUserPermissions)
        {
            return;
        }


        List<AdminPermissionSummary> permissions =
                viewModel
                        .getAvailablePermissions()
                        .getValue();


        if (permissions == null
                ||
                permissions.isEmpty())
        {
            Toast.makeText(
                    this,
                    "Loading available permissions...",
                    Toast.LENGTH_SHORT
            ).show();


            viewModel.loadPermissions();

            return;
        }


        List<String> assignedPermissions =
                user.getPermissions();


        String[] labels =
                new String[
                        permissions.size()
                        ];


        for (int index = 0;
             index < permissions.size();
             index++)
        {
            AdminPermissionSummary permission =
                    permissions.get(
                            index
                    );


            boolean granted =
                    assignedPermissions != null
                            &&
                            assignedPermissions.contains(
                                    permission.getPermissionId()
                            );


            labels[index] =
                    permission.getDisplayName()
                            +
                            (
                                    granted
                                            ? "  • Granted"
                                            : "  • Not granted"
                            );
        }


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        "Manage Permissions"
                )
                .setItems(
                        labels,
                        (dialog, which) ->
                        {
                            AdminPermissionSummary selectedPermission =
                                    permissions.get(
                                            which
                                    );


                            boolean currentlyGranted =
                                    assignedPermissions != null
                                            &&
                                            assignedPermissions.contains(
                                                    selectedPermission
                                                            .getPermissionId()
                                            );


                            showPermissionChangeConfirmation(
                                    selectedPermission,
                                    currentlyGranted
                            );
                        }
                )
                .setNegativeButton(
                        "Close",
                        null
                )
                .show();
    }


    private void renderPermissionAction(
            AdminUserDetail user)
    {
        if (user == null
                ||
                user.isSelf()
                ||
                !canManageUserPermissions)
        {
            managePermissionsButton.setVisibility(
                    View.GONE
            );


            return;
        }


        managePermissionsButton.setVisibility(
                View.VISIBLE
        );


        boolean loadingPermissions =
                Boolean.TRUE.equals(
                        viewModel
                                .getLoadingPermissions()
                                .getValue()
                );


        boolean updatingPermission =
                Boolean.TRUE.equals(
                        viewModel
                                .getUpdatingPermission()
                                .getValue()
                );


        boolean updatingRole =
                Boolean.TRUE.equals(
                        viewModel
                                .getUpdatingRole()
                                .getValue()
                );


        boolean updatingAccount =
                Boolean.TRUE.equals(
                        viewModel
                                .getUpdatingAccount()
                                .getValue()
                );


        managePermissionsButton.setEnabled(
                !loadingPermissions
                        &&
                        !updatingPermission
                        &&
                        !updatingRole
                        &&
                        !updatingAccount
        );


        if (updatingPermission)
        {
            managePermissionsButton.setText(
                    "Updating Permission..."
            );
        }
        else if (loadingPermissions)
        {
            managePermissionsButton.setText(
                    "Loading Permissions..."
            );
        }
        else
        {
            managePermissionsButton.setText(
                    "Manage Permissions"
            );
        }
    }


    private void showPermissionChangeConfirmation(
            AdminPermissionSummary permission,
            boolean currentlyGranted)
    {
        if (permission == null)
        {
            return;
        }


        AdminUserDetail user =
                viewModel
                        .getUser()
                        .getValue();


        if (user == null
                ||
                user.isSelf()
                ||
                !canManageUserPermissions)
        {
            return;
        }


        boolean grant =
                !currentlyGranted;


        String title =
                grant
                        ? "Grant permission?"
                        : "Revoke permission?";


        String message =
                grant
                        ? "Grant "
                          + permission.getDisplayName()
                          + " to "
                          + user.getFullName()
                          + "?"
                        : "Revoke "
                          + permission.getDisplayName()
                          + " from "
                          + user.getFullName()
                          + "?";


        String confirmText =
                grant
                        ? "Grant"
                        : "Revoke";


        new MaterialAlertDialogBuilder(
                this
        )
                .setTitle(
                        title
                )
                .setMessage(
                        message
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        confirmText,
                        (dialog, which) ->
                                viewModel
                                        .setPermission(
                                                permission
                                                        .getPermissionId(),
                                                grant
                                        )
                )
                .show();
    }

    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }


    private boolean isSameRecoveryAdministrator() {
        // Use the active authentication context instead of assuming a previously loaded user is still signed in.
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        return !isFinishing() && !isDestroyed() && current != null
                && administratorUid != null && administratorUid.equals(current.getUid());
    }


    private void loadTotpRecoveryStatus() {
        if (!permissionGranted || !isSameRecoveryAdministrator() || totpResetBusy) return;
        totpStatusRequested = true;
        totpEnrolled = false;
        totpRecoveryResetButton.setVisibility(View.GONE);
        totpRecoveryStatus.setText("Checking authenticator status...");
        totpRecoveryApi.getStatus(recoveryTargetUid,
                new TrustedTotpRecoveryDataSource.StatusCallback() {
                    @Override public void onSuccess(boolean enrolled) {
                        if (!isSameRecoveryAdministrator() || totpResetBusy) return;
                        totpEnrolled = enrolled;
                        totpRecoveryStatus.setText(enrolled
                                ? "Authenticator app: enrolled"
                                : "Authenticator app: not enrolled");
                        renderTotpRecoveryButton();
                    }
                    @Override public void onError(TrustedTotpRecoveryDataSource.RecoveryException e) {
                        if (!isSameRecoveryAdministrator() || totpResetBusy) return;
                        totpRecoveryStatus.setText("Authenticator status unavailable. "
                                + "Check backend connectivity before resetting.");
                        totpEnrolled = false;
                        renderTotpRecoveryButton();
                        // Permit a manual status recheck via tapping the status label.
                        totpStatusRequested = false;
                    }
                });
    }


    private void renderTotpRecoveryButton() {
        if (totpRecoveryResetButton == null) return;
        AdminUserDetail target = viewModel == null ? null : viewModel.getUser().getValue();
        boolean canReset = permissionGranted && canManageUserPermissions && totpEnrolled
                && !totpResetBusy && target != null && !target.isSelf()
                && isSameRecoveryAdministrator();
        totpRecoveryResetButton.setVisibility(canReset ? View.VISIBLE : View.GONE);
    }


    private void showTotpRecoveryConfirmation() {
        if (!permissionGranted || !canManageUserPermissions || !totpEnrolled
                || totpResetBusy || !isSameRecoveryAdministrator()) return;
        AdminUserDetail target = viewModel.getUser().getValue();
        if (target == null || target.isSelf() || !recoveryTargetUid.equals(target.getUid())) return;

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, 0);

        TextView instructions = new TextView(this);
        instructions.setText("Only reset this employee's authenticator after verifying "
                + "their identity. The employee must sign in again afterward. "
                + "Previously enrolled offline access may continue until it expires.");
        content.addView(instructions);

        EditText reasonInput = new EditText(this);
        reasonInput.setHint("Identity verification and recovery reason (12–500 characters)");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setMinLines(2);
        reasonInput.setMaxLines(4);
        content.addView(reasonInput);

        CheckBox identityConfirmed = new CheckBox(this);
        identityConfirmed.setText("I have verified the employee's identity.");
        content.addView(identityConfirmed);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Reset authenticator for " + target.getFullName() + "?")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset authenticator", null)
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(clicked -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.length() < 12 || reason.length() > 500) {
                        reasonInput.setError("Enter a reason between 12 and 500 characters.");
                        return;
                    }
                    if (!identityConfirmed.isChecked()) {
                        Toast.makeText(this, "Confirm employee identity verification.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!isSameRecoveryAdministrator()) {
                        dialog.dismiss();
                        return;
                    }

                    String requestId = UUID.randomUUID().toString();
                    dialog.dismiss();
                    submitTotpRecovery(reason, requestId);
                }));
        dialog.show();
    }


    private void submitTotpRecovery(String reason, String requestId) {
        if (!isSameRecoveryAdministrator() || totpResetBusy) return;
        totpResetBusy = true;
        totpRecoveryStatus.setText("Submitting audited recovery request...");
        renderTotpRecoveryButton();
        totpRecoveryApi.reset(recoveryTargetUid, reason, requestId,
                new TrustedTotpRecoveryDataSource.ResetCallback() {
                    @Override public void onSuccess() {
                        if (!isSameRecoveryAdministrator()) return;
                        totpResetBusy = false;
                        totpEnrolled = false;
                        totpStatusRequested = false;
                        totpRecoveryStatus.setText("Authenticator reset completed. "
                                + "The employee must sign in again. Offline access "
                                + "may remain available until its existing expiry.");
                        renderTotpRecoveryButton();
                        Toast.makeText(UserDetailActivity.this,
                                "Authenticator reset confirmed.", Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(TrustedTotpRecoveryDataSource.RecoveryException e) {
                        if (!isSameRecoveryAdministrator()) return;
                        totpResetBusy = false;
                        totpEnrolled = false;
                        totpStatusRequested = false;
                        renderTotpRecoveryButton();
                        if ("RECENT_SIGN_IN_REQUIRED".equals(e.getCode())) {
                            totpRecoveryStatus.setText("Fresh administrator sign-in required.");
                            showFreshSignInRequired();
                        } else if ("RECOVERY_REQUIRES_REVIEW".equals(e.getCode())
                                || "NETWORK_ERROR".equals(e.getCode())) {
                            totpRecoveryStatus.setText("Recovery result uncertain. "
                                    + "Check the employee's current factor status and "
                                    + "the audit trail before submitting another request.");
                        } else {
                            totpRecoveryStatus.setText("Recovery failed: " + e.getMessage());
                        }
                    }
                });
    }


    private void showFreshSignInRequired() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Recent administrator sign-in required")
                .setMessage("For security, authenticator recovery requires a sign-in "
                        + "within the last five minutes. Sign out, sign in again "
                        + "(including your own TOTP code if enrolled), then reopen "
                        + "this employee's account.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Sign out and sign in", (dialog, which) -> {
                    authenticationRepository.signOut();
                    goToLogin();
                }).show();
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