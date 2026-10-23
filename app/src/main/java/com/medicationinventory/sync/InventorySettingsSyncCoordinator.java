package com.medicationinventory.sync;


import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.domain.UserProfile;




/**
 * Ties the settings Firestore listener to the currently signed-in user profile. It starts live settings observation only for a matching active profile, stops it on sign-out, account switch, or inactive/missing profile, and logs listener failures without treating stale profile data as new authorization.
 */
public final class InventorySettingsSyncCoordinator
{
    private static final String TAG =
            "InventorySettingsSync";


    private final FirebaseAuth firebaseAuth;

    private final UserRepository userRepository;

    private final InventorySettingsRepository
            inventorySettingsRepository;


    private String observedUid;

    private LiveData<UserProfile>
            observedProfile;

    private boolean settingsSyncRunning;


    private final Observer<UserProfile>
            profileObserver =
            this::handleProfileChanged;


    private final FirebaseAuth.AuthStateListener
            authStateListener =
            this::handleAuthStateChanged;



    public InventorySettingsSyncCoordinator(
            FirebaseAuth firebaseAuth,
            UserRepository userRepository,
            InventorySettingsRepository inventorySettingsRepository)
    {
        if (firebaseAuth == null
                ||
                userRepository == null
                ||
                inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Inventory settings sync dependencies are required."
            );
        }


        this.firebaseAuth =
                firebaseAuth;


        this.userRepository =
                userRepository;


        this.inventorySettingsRepository =
                inventorySettingsRepository;
    }


    public void start()
    {
        firebaseAuth.addAuthStateListener(
                authStateListener
        );
    }


    public void stop()
    {
        firebaseAuth.removeAuthStateListener(
                authStateListener
        );


        detachCurrentProfile();

        stopSettingsSync();
    }


    private void handleAuthStateChanged(
            FirebaseAuth auth)
    {
        FirebaseUser user =
                auth.getCurrentUser();


        String uid =
                user == null
                        ? null
                        : user.getUid();


        if (uid != null)
        {
            uid =
                    uid.trim();

            if (uid.isEmpty())
            {
                uid =
                        null;
            }
        }


        if (uid == null
                &&
                observedUid == null)
        {
            return;
        }


        if (uid != null
                &&
                uid.equals(
                        observedUid
                ))
        {
            return;
        }


        switchObservedProfile(
                uid
        );
    }


    private void switchObservedProfile(
            String uid)
    {
        stopSettingsSync();

        detachCurrentProfile();


        observedUid =
                uid;


        if (uid == null)
        {
            return;
        }


        observedProfile =
                userRepository
                        .observeUser(
                                uid
                        );


        observedProfile.observeForever(
                profileObserver
        );
    }


    private void detachCurrentProfile()
    {
        if (observedProfile != null)
        {
            observedProfile.removeObserver(
                    profileObserver
            );


            observedProfile =
                    null;
        }


        observedUid =
                null;
    }


    private void handleProfileChanged(
            UserProfile profile)
    {
        String uid =
                observedUid;


        boolean active =
                uid != null
                        &&
                        profile != null
                        &&
                        uid.equals(
                                profile.getUid()
                        )
                        &&
                        profile.isActive();


        if (!active)
        {
            stopSettingsSync();

            return;
        }


        if (settingsSyncRunning)
        {
            return;
        }


        settingsSyncRunning =
                true;


        inventorySettingsRepository
                .startLiveSync(
                        new InventorySettingsRepository
                                .SettingsCallback()
                        {
                            @Override
                            public void onSuccess(
                                    InventorySettings settings)
                            {
                                // Room has already been updated.
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {
                                Log.w(
                                        TAG,
                                        "Unable to synchronise inventory settings.",
                                        exception
                                );
                            }
                        }
                );
    }


    private void stopSettingsSync()
    {
        inventorySettingsRepository
                .stopLiveSync();


        settingsSyncRunning =
                false;
    }
}