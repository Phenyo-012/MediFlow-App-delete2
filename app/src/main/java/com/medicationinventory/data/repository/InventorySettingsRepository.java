package com.medicationinventory.data.repository;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.firestore.ListenerRegistration;

import com.medicationinventory.data.local.dao.InventorySettingsDao;
import com.medicationinventory.data.local.entity.InventorySettingsEntity;

import com.medicationinventory.data.mapper.InventorySettingsMapper;

import com.medicationinventory.data.remote.firestore
        .FirestoreInventorySettingsDataSource;
import com.medicationinventory.data.remote.backend
        .TrustedInventorySettingsDataSource;

import com.medicationinventory.model.domain.InventorySettings;

import com.medicationinventory.util.AppExecutors;


/**
 * Exposes cached inventory settings to app services and refreshes them from Firestore. Setting changes go through the trusted mutation source, then refresh the authoritative settings snapshot; a committed change followed by a failed refresh is surfaced separately from a rejected mutation.
 */
public class InventorySettingsRepository
{
    private final InventorySettingsDao
            inventorySettingsDao;


    private final FirestoreInventorySettingsDataSource
            remoteDataSource;


    private final AppExecutors
            appExecutors;

    private ListenerRegistration
            liveSettingsRegistration;

    private final TrustedInventorySettingsDataSource
            trustedMutationDataSource;


    public InventorySettingsRepository(
            InventorySettingsDao inventorySettingsDao,
            FirestoreInventorySettingsDataSource remoteDataSource,
            TrustedInventorySettingsDataSource trustedMutationDataSource,
            AppExecutors appExecutors)
    {
        if (inventorySettingsDao == null)
        {
            throw new IllegalArgumentException(
                    "Inventory settings DAO is required."
            );
        }


        if (remoteDataSource == null)
        {
            throw new IllegalArgumentException(
                    "Inventory settings remote data source is required."
            );
        }


        if (trustedMutationDataSource == null)
        {
            throw new IllegalArgumentException(
                    "Trusted settings mutation data source is required."
            );
        }


        if (appExecutors == null)
        {
            throw new IllegalArgumentException(
                    "App executors are required."
            );
        }


        this.inventorySettingsDao =
                inventorySettingsDao;


        this.remoteDataSource =
                remoteDataSource;


        this.trustedMutationDataSource =
                trustedMutationDataSource;


        this.appExecutors =
                appExecutors;
    }


    /** Observes Room settings and supplies application defaults until the settings row has been loaded. */
    public LiveData<InventorySettings>
    observeInventorySettings()
    {
        return Transformations.map(
                inventorySettingsDao
                        .observeById(
                                InventorySettings.SETTING_ID
                        ),
                entity ->
                {
                    InventorySettings settings =
                            InventorySettingsMapper
                                    .toDomain(
                                            entity
                                    );


                    return settings == null
                            ? InventorySettings
                            .defaultSettings()
                            : settings;
                }
        );
    }


    /** Loads the Firestore settings snapshot, persists it to Room, and reports remote or local-write failures. */
    public void refreshFromServer(
            SettingsCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Settings callback is required."
            );
        }


        remoteDataSource
                .getInventorySettings(
                        new FirestoreInventorySettingsDataSource
                                .SettingsCallback()
                        {
                            @Override

                            public void onSuccess(
                                    InventorySettings settings)
                            {

                                if (settings == null)
                                {
                                    deleteAuthoritativeAbsence(
                                            callback
                                    );

                                    return;
                                }


                                persistAuthoritativeSettings(
                                        settings,
                                        callback
                                );
                            }


                            @Override

                            public void onError(
                                    Exception exception)
                            {

                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }


    private void persistAuthoritativeSettings(
            InventorySettings settings,
            SettingsCallback callback)
    {
        InventorySettingsEntity entity =
                InventorySettingsMapper
                        .toEntity(
                                settings
                        );


        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                inventorySettingsDao
                                        .upsert(
                                                entity
                                        );


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onSuccess(
                                                                settings
                                                        )
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }


    private void deleteAuthoritativeAbsence(
            SettingsCallback callback)
    {
        appExecutors
                .databaseIO()
                .execute(
                        () ->
                        {
                            try
                            {
                                inventorySettingsDao
                                        .deleteById(
                                                InventorySettings
                                                        .SETTING_ID
                                        );


                                InventorySettings fallback =
                                        InventorySettings
                                                .defaultSettings();


                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onSuccess(
                                                                fallback
                                                        )
                                        );
                            }
                            catch (Exception exception)
                            {
                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback.onError(
                                                                exception
                                                        )
                                        );
                            }
                        }
                );
    }

    public InventorySettings getCurrentSettingsBlocking()
    {
        InventorySettingsEntity entity =
                inventorySettingsDao
                        .getById(
                                InventorySettings.SETTING_ID
                        );


        InventorySettings settings =
                InventorySettingsMapper
                        .toDomain(
                                entity
                        );


        return settings == null
                ? InventorySettings.defaultSettings()
                : settings;
    }

    /**
     * Starts one Firestore listener for settings and writes received snapshots into Room.
     * Call stopLiveSync when the owning application scope no longer needs live updates.
     */
    public synchronized void startLiveSync(
            SettingsCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Settings callback is required."
            );
        }


        stopLiveSync();


        liveSettingsRegistration =
                remoteDataSource
                        .listenToInventorySettings(
                                new FirestoreInventorySettingsDataSource
                                        .SettingsCallback()
                                {
                                    @Override
                                    public void onSuccess(
                                            InventorySettings settings)
                                    {
                                        if (settings == null)
                                        {
                                            deleteAuthoritativeAbsence(
                                                    callback
                                            );

                                            return;
                                        }


                                        persistAuthoritativeSettings(
                                                settings,
                                                callback
                                        );
                                    }


                                    @Override
                                    public void onError(
                                            Exception exception)
                                    {

                                        callback.onError(
                                                exception
                                        );
                                    }
                                }
                        );
    }

    /** Removes the active Firestore listener, if present. */
    public synchronized void stopLiveSync()
    {
        if (liveSettingsRegistration == null)
        {
            return;
        }


        liveSettingsRegistration.remove();

        liveSettingsRegistration =
                null;
    }

    /**
     * Requests an authorized trusted-backend mutation, then refreshes the authoritative settings.
     * A committed mutation with a failed refresh is reported separately from a rejected mutation.
     */
    public void setInventorySettings(
            int nearExpiryWarningDays,
            TrustedMutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Trusted mutation callback is required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            callback.onError(
                    new IllegalArgumentException(
                            "Near-expiry warning days cannot be negative."
                    )
            );

            return;
        }


        trustedMutationDataSource
                .setInventorySettings(
                        nearExpiryWarningDays,
                        new TrustedInventorySettingsDataSource
                                .MutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    TrustedInventorySettingsDataSource
                                            .MutationResult result)
                            {

                                refreshFromServer(
                                        new SettingsCallback()
                                        {
                                            @Override
                                            public void onSuccess(
                                                    InventorySettings settings)
                                            {
                                                callback.onSuccess(
                                                        result.isChanged(),
                                                        result.isCreated(),
                                                        settings
                                                );
                                            }


                                            @Override
                                            public void onError(
                                                    Exception exception)
                                            {

                                                callback.onCommittedRefreshError(
                                                        result.isChanged(),
                                                        result.isCreated(),
                                                        exception
                                                );
                                            }
                                        }
                                );
                            }


                            @Override
                            public void onError(
                                    TrustedInventorySettingsDataSource
                                            .SettingsRemoteException exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                );
    }

    public interface TrustedMutationCallback
    {
        void onSuccess(
                boolean changed,
                boolean created,
                InventorySettings settings
        );


        void onCommittedRefreshError(
                boolean changed,
                boolean created,
                Exception exception
        );


        void onError(
                Exception exception
        );
    }

    public interface SettingsCallback
    {
        void onSuccess(
                InventorySettings settings
        );


        void onError(
                Exception exception
        );
    }
}