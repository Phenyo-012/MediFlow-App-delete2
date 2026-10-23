package com.medicationinventory.data.remote.firestore;


import com.google.firebase.Timestamp;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;

import com.medicationinventory.model.domain.InventorySettings;


/**
 * Reads or writes Firestore Inventory Settings Data Source data in Firestore and translates asynchronous results for app callers.
 */
public class FirestoreInventorySettingsDataSource
{
    private static final String SETTINGS_COLLECTION =
            "settings";


    private static final String INVENTORY_SETTING_ID =
            InventorySettings.SETTING_ID;


    private final FirebaseFirestore firestore;


    public FirestoreInventorySettingsDataSource(
            FirebaseFirestore firestore)
    {
        if (firestore == null)
        {
            throw new IllegalArgumentException(
                    "FirebaseFirestore is required."
            );
        }


        this.firestore =
                firestore;
    }


    public void getInventorySettings(
            SettingsCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Settings callback is required."
            );
        }


        firestore
                .collection(
                        SETTINGS_COLLECTION
                )
                .document(
                        INVENTORY_SETTING_ID
                )
                .get(
                        Source.SERVER
                )
                .addOnSuccessListener(
                        snapshot ->
                        {
                            if (!snapshot.exists())
                            {

                                callback.onSuccess(
                                        null
                                );

                                return;
                            }


                            try
                            {
                                callback.onSuccess(
                                        mapDocument(
                                                snapshot
                                        )
                                );
                            }
                            catch (Exception exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        callback::onError
                );
    }


    private InventorySettings mapDocument(
            DocumentSnapshot snapshot)
    {
        Long warningDaysValue =
                snapshot.getLong(
                        "nearExpiryWarningDays"
                );


        if (warningDaysValue == null)
        {
            throw new IllegalStateException(
                    "Inventory settings are missing "
                            + "nearExpiryWarningDays."
            );
        }


        if (warningDaysValue < 0
                ||
                warningDaysValue > Integer.MAX_VALUE)
        {
            throw new IllegalStateException(
                    "Inventory settings contain an invalid "
                            + "near-expiry warning period."
            );
        }


        Timestamp updatedAtValue =
                snapshot.getTimestamp(
                        "updatedAt"
                );


        if (updatedAtValue == null)
        {
            throw new IllegalStateException(
                    "Inventory settings are missing updatedAt."
            );
        }


        Object updatedByValue =
                snapshot.get(
                        "updatedBy"
                );


        String updatedBy =
                null;


        if (updatedByValue != null)
        {
            if (!(updatedByValue instanceof String))
            {
                throw new IllegalStateException(
                        "Inventory settings contain an invalid "
                                + "updatedBy value."
                );
            }


            String candidate =
                    ((String) updatedByValue)
                            .trim();


            if (!candidate.isEmpty())
            {
                updatedBy =
                        candidate;
            }
        }


        return new InventorySettings(
                snapshot.getId(),
                warningDaysValue.intValue(),
                updatedAtValue.toDate().getTime(),
                updatedBy
        );
    }

    public ListenerRegistration listenToInventorySettings(
            SettingsCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Settings callback is required."
            );
        }


        return firestore
                .collection(
                        SETTINGS_COLLECTION
                )
                .document(
                        INVENTORY_SETTING_ID
                )
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (snapshot, exception) ->
                        {
                            if (exception != null)
                            {
                                callback.onError(
                                        exception
                                );

                                return;
                            }


                            if (snapshot == null)
                            {
                                callback.onError(
                                        new IllegalStateException(
                                                "Inventory settings snapshot is unavailable."
                                        )
                                );

                                return;
                            }


                            if (snapshot
                                    .getMetadata()
                                    .isFromCache())
                            {
                                return;
                            }


                            if (!snapshot.exists())
                            {
                                callback.onSuccess(
                                        null
                                );

                                return;
                            }


                            try
                            {
                                callback.onSuccess(
                                        mapDocument(
                                                snapshot
                                        )
                                );
                            }
                            catch (Exception mappingException)
                            {
                                callback.onError(
                                        mappingException
                                );
                            }
                        }
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