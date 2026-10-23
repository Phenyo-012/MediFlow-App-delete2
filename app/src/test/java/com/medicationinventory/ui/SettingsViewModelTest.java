package com.medicationinventory.ui.settings;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import androidx.lifecycle.MutableLiveData;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.ui.settings.SettingsViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;




/**
 * Covers settings validation and permission gates, trusted mutation outcomes, refresh failures, and authentication changes during validation.
 */
public class SettingsViewModelTest
{
    private static final String USER_ID =
            "user-1";


    private AuthenticationRepository
            authenticationRepository;


    private UserRepository
            userRepository;


    private InventorySettingsRepository
            inventorySettingsRepository;


    private MutableLiveData<InventorySettings>
            settingsLiveData;


    private SettingsViewModel
            viewModel;


    @Before
    public void setUp()
    {

        ArchTaskExecutor
                .getInstance()
                .setDelegate(
                        new TaskExecutor()
                        {
                            @Override

                            public void executeOnDiskIO(
                                    Runnable runnable)
                            {
                                runnable.run();
                            }


                            @Override

                            public void postToMainThread(
                                    Runnable runnable)
                            {
                                runnable.run();
                            }


                            @Override
                            public boolean isMainThread()
                            {
                                return true;
                            }
                        }
                );


        authenticationRepository =
                mock(
                        AuthenticationRepository.class
                );


        userRepository =
                mock(
                        UserRepository.class
                );


        inventorySettingsRepository =
                mock(
                        InventorySettingsRepository.class
                );


        settingsLiveData =
                new MutableLiveData<>(
                        new InventorySettings(
                                "inventory",
                                90,
                                1000L,
                                "system"
                        )
                );


        when(
                inventorySettingsRepository
                        .observeInventorySettings()
        )
                .thenReturn(
                        settingsLiveData
                );


        viewModel =
                new SettingsViewModel(
                        authenticationRepository,
                        userRepository,
                        inventorySettingsRepository
                );
    }


    @After

    public void tearDown()
    {
        ArchTaskExecutor
                .getInstance()
                .setDelegate(
                        null
                );
    }


    @Test

    public void save_validValueAndPermission_performsTrustedMutation()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        true,
                        true
                )
        );


        InventorySettings authoritative =
                new InventorySettings(
                        "inventory",
                        45,
                        2000L,
                        USER_ID
                );


        doAnswer(
                invocation ->
                {
                    InventorySettingsRepository
                            .TrustedMutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onSuccess(
                            true,
                            false,
                            authoritative
                    );


                    return null;
                }
        )
                .when(
                        inventorySettingsRepository
                )
                .setInventorySettings(
                        eq(45),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );


        viewModel.saveNearExpiryWarningDays(
                "45"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .SUCCESS,
                state.getStatus()
        );


        assertTrue(
                state.isChanged()
        );


        assertFalse(
                state.isCreated()
        );


        verify(
                inventorySettingsRepository
        )
                .setInventorySettings(
                        eq(45),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }


    @Test

    public void save_withoutManagePermission_doesNotMutate()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        true,
                        false
                )
        );


        viewModel.saveNearExpiryWarningDays(
                "45"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "You do not have permission to manage system settings.",
                state.getMessage()
        );


        verify(
                inventorySettingsRepository,
                never()
        )
                .setInventorySettings(
                        anyInt(),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }


    @Test

    public void save_inactiveProfile_doesNotMutate()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        false,
                        true
                )
        );


        viewModel.saveNearExpiryWarningDays(
                "45"
        );


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                currentState()
                        .getStatus()
        );


        verify(
                inventorySettingsRepository,
                never()
        )
                .setInventorySettings(
                        anyInt(),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }


    @Test

    public void save_missingProfile_doesNotMutate()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                null
        );


        viewModel.saveNearExpiryWarningDays(
                "45"
        );


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                currentState()
                        .getStatus()
        );


        verify(
                inventorySettingsRepository,
                never()
        )
                .setInventorySettings(
                        anyInt(),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }


    @Test

    public void save_blankValue_rejectedLocally()
    {
        viewModel.saveNearExpiryWarningDays(
                "   "
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "Near-expiry warning days are required.",
                state.getMessage()
        );


        verifyNoMutationAttempt();
    }


    @Test

    public void save_nonIntegerValue_rejectedLocally()
    {
        viewModel.saveNearExpiryWarningDays(
                "30.5"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "Near-expiry warning days must be a whole number.",
                state.getMessage()
        );


        verifyNoMutationAttempt();
    }


    @Test

    public void save_negativeValue_rejectedLocally()
    {
        viewModel.saveNearExpiryWarningDays(
                "-1"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "Near-expiry warning days cannot be negative.",
                state.getMessage()
        );


        verifyNoMutationAttempt();
    }


    @Test

    public void save_idempotentBackendSuccess_returnsSuccessChangedFalse()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        true,
                        true
                )
        );


        InventorySettings authoritative =
                new InventorySettings(
                        "inventory",
                        40,
                        3000L,
                        "previous-admin"
                );


        doAnswer(
                invocation ->
                {
                    InventorySettingsRepository
                            .TrustedMutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onSuccess(
                            false,
                            false,
                            authoritative
                    );


                    return null;
                }
        )
                .when(
                        inventorySettingsRepository
                )
                .setInventorySettings(
                        eq(40),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );


        viewModel.saveNearExpiryWarningDays(
                "40"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .SUCCESS,
                state.getStatus()
        );


        assertFalse(
                state.isChanged()
        );


        assertFalse(
                state.isCreated()
        );
    }


    @Test

    public void save_committedButRefreshFails_returnsCommittedRefreshError()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        true,
                        true
                )
        );


        doAnswer(
                invocation ->
                {
                    InventorySettingsRepository
                            .TrustedMutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onCommittedRefreshError(
                            true,
                            false,
                            new Exception(
                                    "Refresh unavailable."
                            )
                    );


                    return null;
                }
        )
                .when(
                        inventorySettingsRepository
                )
                .setInventorySettings(
                        eq(30),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );


        viewModel.saveNearExpiryWarningDays(
                "30"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .COMMITTED_REFRESH_ERROR,
                state.getStatus()
        );


        assertTrue(
                state.isChanged()
        );


        assertEquals(
                "The setting was saved, but the latest server copy "
                        + "could not be refreshed.",
                state.getMessage()
        );
    }


    @Test

    public void save_backendRejected_returnsError()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID
                );


        stubUserLookup(
                createProfile(
                        true,
                        true
                )
        );


        doAnswer(
                invocation ->
                {
                    InventorySettingsRepository
                            .TrustedMutationCallback callback =
                            invocation.getArgument(
                                    1
                            );


                    callback.onError(
                            new Exception(
                                    "Permission denied."
                            )
                    );


                    return null;
                }
        )
                .when(
                        inventorySettingsRepository
                )
                .setInventorySettings(
                        eq(50),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );


        viewModel.saveNearExpiryWarningDays(
                "50"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "Permission denied.",
                state.getMessage()
        );
    }


    @Test

    public void save_authenticationChangesDuringValidation_doesNotMutate()
    {
        when(
                authenticationRepository
                        .getCurrentUserId()
        )
                .thenReturn(
                        USER_ID,
                        "user-2"
                );


        stubUserLookup(
                createProfile(
                        true,
                        true
                )
        );


        viewModel.saveNearExpiryWarningDays(
                "45"
        );


        SettingsViewModel.SettingsActionState state =
                currentState();


        assertEquals(
                SettingsViewModel
                        .SettingsActionState
                        .Status
                        .ERROR,
                state.getStatus()
        );


        assertEquals(
                "Your authentication session changed. Please try again.",
                state.getMessage()
        );


        verify(
                inventorySettingsRepository,
                never()
        )
                .setInventorySettings(
                        anyInt(),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }



    private UserProfile createProfile(
            boolean active,
            boolean canManageSettings)
    {
        UserProfile profile =
                mock(
                        UserProfile.class
                );


        when(
                profile.isActive()
        )
                .thenReturn(
                        active
                );


        when(
                profile.can(
                        Permission.MANAGE_SETTINGS
                )
        )
                .thenReturn(
                        canManageSettings
                );


        return profile;
    }


    private void stubUserLookup(
            UserProfile profile)
    {
        doAnswer(
                invocation ->
                {
                    Object callback =
                            invocation.getArgument(
                                    1
                            );


                    invokeSingleArgumentCallback(
                            callback,
                            profile
                    );


                    return null;
                }
        )
                .when(
                        userRepository
                )
                .getUser(
                        eq(USER_ID),
                        any()
                );
    }



    private static void invokeSingleArgumentCallback(
            Object callback,
            Object argument)
    {
        if (callback == null)
        {
            throw new AssertionError(
                    "User callback was null."
            );
        }


        for (Class<?> callbackInterface :
                callback.getClass()
                        .getInterfaces())
        {
            for (Method method :
                    callbackInterface.getMethods())
            {
                if (Modifier.isAbstract(
                        method.getModifiers()
                )
                        &&
                        method.getParameterCount() == 1)
                {
                    try
                    {
                        method.invoke(
                                callback,
                                argument
                        );


                        return;
                    }
                    catch (Exception exception)
                    {
                        throw new AssertionError(
                                "Unable to invoke user callback.",
                                exception
                        );
                    }
                }
            }
        }


        throw new AssertionError(
                "No single-argument user callback method was found."
        );
    }


    private SettingsViewModel.SettingsActionState
    currentState()
    {
        SettingsViewModel.SettingsActionState state =
                viewModel
                        .getActionState()
                        .getValue();


        assertNotNull(
                state
        );


        return state;
    }



    private void verifyNoMutationAttempt()
    {
        verify(
                userRepository,
                never()
        )
                .getUser(
                        any(),
                        any()
                );


        verify(
                inventorySettingsRepository,
                never()
        )
                .setInventorySettings(
                        anyInt(),
                        any(
                                InventorySettingsRepository
                                        .TrustedMutationCallback.class
                        )
                );
    }
}