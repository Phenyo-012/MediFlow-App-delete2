package com.medicationinventory.ui.settings;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.enums.Permission;




/**
 * Exposes the Room-backed inventory settings and the one settings mutation supported by this
 * screen: the near-expiry warning period. Saving validates integer input, checks the current
 * signed-in profile and its update permission, guards against overlapping requests and account
 * changes, then delegates to the trusted settings repository. UI state distinguishes rejected
 * writes from a committed value whose follow-up refresh failed.
 */
public class SettingsViewModel
        extends ViewModel
{
    private final AuthenticationRepository
            authenticationRepository;


    private final UserRepository
            userRepository;


    private final InventorySettingsRepository
            inventorySettingsRepository;


    private final LiveData<InventorySettings>
            inventorySettings;


    private final MutableLiveData<SettingsActionState>
            actionState =
            new MutableLiveData<>(
                    SettingsActionState.idle()
            );


    private boolean mutationInProgress =
            false;



    public SettingsViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Settings dependencies are required."
            );
        }


        this.authenticationRepository =
                authenticationRepository;


        this.userRepository =
                userRepository;


        this.inventorySettingsRepository =
                inventorySettingsRepository;


        inventorySettings =
                inventorySettingsRepository
                        .observeInventorySettings();
    }


    public LiveData<InventorySettings>
    getInventorySettings()
    {
        return inventorySettings;
    }


    public LiveData<SettingsActionState>
    getActionState()
    {
        return actionState;
    }



    public void saveNearExpiryWarningDays(
            String value)
    {
        if (mutationInProgress)
        {
            return;
        }


        final int warningDays;


        try
        {
            if (value == null
                    ||
                    value.trim().isEmpty())
            {
                actionState.setValue(
                        SettingsActionState.error(
                                "Near-expiry warning days are required."
                        )
                );

                return;
            }


            warningDays =
                    Integer.parseInt(
                            value.trim()
                    );
        }
        catch (NumberFormatException exception)
        {
            actionState.setValue(
                    SettingsActionState.error(
                            "Near-expiry warning days must be a whole number."
                    )
            );

            return;
        }


        if (warningDays < 0)
        {
            actionState.setValue(
                    SettingsActionState.error(
                            "Near-expiry warning days cannot be negative."
                    )
            );

            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            actionState.setValue(
                    SettingsActionState.error(
                            "Authentication is required."
                    )
            );

            return;
        }


        final String expectedUid =
                uid.trim();


        mutationInProgress =
                true;


        actionState.setValue(
                SettingsActionState.loading()
        );



        userRepository.getUser(
                expectedUid,
                profile ->
                {
                    if (profile == null
                            ||
                            !profile.isActive())
                    {
                        finishWithError(
                                "Your user profile is unavailable or inactive."
                        );

                        return;
                    }


                    if (!profile.can(
                            Permission.MANAGE_SETTINGS
                    ))
                    {
                        finishWithError(
                                "You do not have permission to manage system settings."
                        );

                        return;
                    }



                    String latestUid =
                            authenticationRepository
                                    .getCurrentUserId();


                    if (latestUid == null
                            ||
                            !expectedUid.equals(
                                    latestUid.trim()
                            ))
                    {
                        finishWithError(
                                "Your authentication session changed. Please try again."
                        );

                        return;
                    }


                    performMutation(
                            warningDays
                    );
                }
        );
    }



    private void performMutation(
            int warningDays)
    {
        inventorySettingsRepository
                .setInventorySettings(
                        warningDays,
                        new InventorySettingsRepository
                                .TrustedMutationCallback()
                        {
                            @Override

                            public void onSuccess(
                                    boolean changed,
                                    boolean created,
                                    InventorySettings settings)
                            {
                                mutationInProgress =
                                        false;


                                actionState.setValue(
                                        SettingsActionState.success(
                                                changed,
                                                created
                                        )
                                );
                            }


                            @Override

                            public void onCommittedRefreshError(
                                    boolean changed,
                                    boolean created,
                                    Exception exception)
                            {
                                mutationInProgress =
                                        false;


                                actionState.setValue(
                                        SettingsActionState
                                                .committedRefreshError(
                                                        changed,
                                                        created,
                                                        "The setting was saved, "
                                                                + "but the latest server copy "
                                                                + "could not be refreshed."
                                                )
                                );
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {
                                mutationInProgress =
                                        false;


                                String message =
                                        exception != null
                                                &&
                                                exception.getMessage() != null
                                                &&
                                                !exception.getMessage()
                                                        .trim()
                                                        .isEmpty()
                                                ? exception
                                                .getMessage()
                                                .trim()
                                                : "Unable to update inventory settings.";


                                actionState.setValue(
                                        SettingsActionState.error(
                                                message
                                        )
                                );
                            }
                        }
                );
    }



    private void finishWithError(
            String message)
    {
        mutationInProgress =
                false;


        actionState.setValue(
                SettingsActionState.error(
                        message
                )
        );
    }



    public void clearActionState()
    {
        if (mutationInProgress)
        {
            return;
        }


        actionState.setValue(
                SettingsActionState.idle()
        );
    }


    public static final class SettingsActionState
    {
        public enum Status
        {
            IDLE,
            LOADING,
            SUCCESS,
            COMMITTED_REFRESH_ERROR,
            ERROR
        }


        private final Status status;

        private final boolean changed;

        private final boolean created;

        private final String message;



        private SettingsActionState(
                Status status,
                boolean changed,
                boolean created,
                String message)
        {
            this.status =
                    status;


            this.changed =
                    changed;


            this.created =
                    created;


            this.message =
                    message;
        }



        public static SettingsActionState idle()
        {
            return new SettingsActionState(
                    Status.IDLE,
                    false,
                    false,
                    null
            );
        }



        public static SettingsActionState loading()
        {
            return new SettingsActionState(
                    Status.LOADING,
                    false,
                    false,
                    null
            );
        }



        public static SettingsActionState success(
                boolean changed,
                boolean created)
        {
            return new SettingsActionState(
                    Status.SUCCESS,
                    changed,
                    created,
                    null
            );
        }


        public static SettingsActionState
        committedRefreshError(
                boolean changed,
                boolean created,
                String message)
        {
            return new SettingsActionState(
                    Status.COMMITTED_REFRESH_ERROR,
                    changed,
                    created,
                    message
            );
        }



        public static SettingsActionState error(
                String message)
        {
            return new SettingsActionState(
                    Status.ERROR,
                    false,
                    false,
                    message
            );
        }


        public Status getStatus()
        {
            return status;
        }


        public boolean isChanged()
        {
            return changed;
        }


        public boolean isCreated()
        {
            return created;
        }


        public String getMessage()
        {
            return message;
        }
    }
}