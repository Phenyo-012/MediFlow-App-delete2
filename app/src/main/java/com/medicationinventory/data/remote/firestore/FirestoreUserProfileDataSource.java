package com.medicationinventory.data.remote.firestore;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Source;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.model.enums.UserRole;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;


/**
 * Loads application user profiles from the Firestore {@code users} collection,
 * mapping role identifiers and permission values into the domain model. The
 * server-only lookup deliberately distinguishes a missing profile from a
 * Firebase-authenticated identity: callers can reject sign-in when the app-side
 * account record is absent or malformed.
 */
public class FirestoreUserProfileDataSource
{
    public static final class ServerProfileMissingException extends IllegalStateException {

        public ServerProfileMissingException() {
            super("Application user profile was not found.");
        }
    }

    private static final String USERS_COLLECTION = "users";
    private final FirebaseFirestore firestore;

    public FirestoreUserProfileDataSource()
    {
        this.firestore = FirebaseFirestore.getInstance();
    }
    /**
     * Fetches the application profile for a Firebase UID from the server and
     * converts Firestore role and permission fields into a domain profile. Missing
     * documents and malformed records are returned as errors so authentication
     * callers can distinguish them from a valid Firebase identity.
     *
     * @param uid Firebase account UID whose application profile is required
     * @param callback receives the mapped profile or lookup/mapping failure
     */
    public void getUserProfile(String uid, UserProfileCallback callback)
    {
        if (uid == null || uid.trim().isEmpty())
        {
            callback.onError(new IllegalArgumentException("User UID cannot be empty."));
            return;
        }

        firestore.collection(USERS_COLLECTION)
                .document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists())
                    {
                        callback.onError(new ServerProfileMissingException());
                        return;
                    }

                    try
                    {
                        UserProfile userProfile = mapDocumentToUserProfile(uid, documentSnapshot);
                        callback.onSuccess(userProfile);
                    }
                    catch (Exception exception)
                    {
                        callback.onError(exception);
                    }
                }).addOnFailureListener(callback::onError);
    }

    private UserProfile mapDocumentToUserProfile(String uid, DocumentSnapshot document)
    {
        String firstName = document.getString("firstName");
        String lastName = document.getString("lastName");
        String email = document.getString("email");
        String roleId = document.getString("roleId");
        Boolean activeValue = document.getBoolean("isActive");
        UserRole role = UserRole.fromRoleId(roleId);

        if (role == null)
        {
            throw new IllegalStateException("User profile contains an invalid role.");
        }

        Set<Permission> permissions = parsePermissions(document.get("permissions"));
        boolean isActive = Boolean.TRUE.equals(activeValue);
        return new UserProfile(
                uid,
                firstName,
                lastName,
                email,
                role,
                permissions,
                isActive,
                System.currentTimeMillis());
    }


    private Set<Permission> parsePermissions(Object permissionsObject)
    {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

        if (!(permissionsObject instanceof List<?>))
        {
            return permissions;
        }

        List<?> permissionValues = (List<?>) permissionsObject;

        for (Object value : permissionValues)
        {
            if (!(value instanceof String))
            {
                continue;
            }

            Permission permission = Permission.fromPermissionId((String) value);

            if (permission != null)
            {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    /**
     * Observes profile changes for the supplied UID and forwards mapped profile
     * snapshots or listener errors. The returned registration must be removed by
     * the owner when the observation is no longer needed.
     *
     * @param uid Firebase account UID to observe
     * @param callback receives profile snapshots and listener failures
     * @return registration used to stop the Firestore listener
     */
    public ListenerRegistration listenToUserProfile(
            String uid,
            UserProfileListener callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User profile listener is required."
            );
        }


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            callback.onError(
                    new IllegalArgumentException(
                            "User UID cannot be empty."
                    )
            );


            return () ->
            {
                // No active listener.
            };
        }


        String cleanUid =
                uid.trim();


        return firestore
                .collection(
                        USERS_COLLECTION
                )
                .document(
                        cleanUid
                )
                .addSnapshotListener(
                        MetadataChanges.INCLUDE,
                        (documentSnapshot, exception) ->
                        {
                            if (exception != null)
                            {

                                callback.onError(
                                        exception
                                );

                                return;
                            }


                            if (documentSnapshot == null || documentSnapshot.getMetadata().isFromCache()
                                    || documentSnapshot.getMetadata().hasPendingWrites()) {
                                return;
                            }
                            if (!documentSnapshot.exists()) {
                                callback.onMissing();
                                return;
                            }
                            try {
                                UserProfile userProfile = mapDocumentToUserProfile(cleanUid, documentSnapshot);

                                callback.onSuccess(userProfile);
                                callback.onServerConfirmed(userProfile);
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

    public interface UserProfileListener
    {
        void onSuccess(
                UserProfile userProfile
        );


        default void onServerConfirmed(UserProfile userProfile) { }

        void onMissing();


        void onError(
                Exception exception
        );
    }

    public interface UserProfileCallback
    {
        void onSuccess(UserProfile userProfile);
        void onError(Exception exception);
    }
}
