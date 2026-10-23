package com.medicationinventory.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.firestore.ListenerRegistration;

import com.medicationinventory.data.local.converter.UserProfileMapper;
import com.medicationinventory.data.local.dao.UserDao;
import com.medicationinventory.data.local.entity.UserProfileEntity;
import com.medicationinventory.data.remote.firestore.FirestoreUserProfileDataSource;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.util.AppExecutors;



/**
 * Keeps the signed-in user profile available from Room while refreshing it from Firestore for authorization decisions. The profile listener persists server snapshots locally, distinguishes server-confirmed updates from cache emissions, and clears the cached profile when the server reports that it is missing.
 */
public class UserRepository
{
    private final UserDao userDao;
    private final FirestoreUserProfileDataSource remoteDataSource;
    private final AppExecutors appExecutors;

    public UserRepository(
            UserDao userDao,
            FirestoreUserProfileDataSource remoteDataSource,
            AppExecutors appExecutors)
    {
        this.userDao = userDao;
        this.remoteDataSource = remoteDataSource;
        this.appExecutors = appExecutors;
    }

    /**
     * Observes the cached profile for a UID and maps Room updates into the domain model.
     * @param uid Firebase user identifier
     * @return live profile data, which may be null until a profile has been cached
     */
    public LiveData<UserProfile> observeUser(String uid)
    {
        LiveData<UserProfileEntity> entityLiveData =
                userDao.observeCurrentUser(uid);
        return Transformations.map(
                entityLiveData,
                UserProfileMapper::toDomain);
    }
    /**
     * Reads the current cached profile on the database executor and delivers it on the main thread.
     * This does not contact Firestore or establish authorization from stale local data.
     * @param uid Firebase user identifier
     * @param callback receives the mapped profile, or null when no row is cached
     */
    public void getUser(
            String uid,
            UserCallback callback)
    {
        appExecutors.databaseIO().execute(() ->
        {

            UserProfileEntity entity = userDao.getCurrentUser(uid);
            UserProfile profile = UserProfileMapper.toDomain(entity);
            appExecutors.mainThread().execute(() -> callback.onResult(profile));
        });
    }


    /**
     * Fetches a profile from the remote source and upserts it locally before reporting success.
     * Invalid UIDs fail through the callback; a remote failure leaves the caller without a refreshed profile.
     * @param uid Firebase user identifier
     * @param callback receives the server profile or the fetch error
     */
    public void refreshUserProfile(String uid, RefreshCallback callback) {
        if (uid == null || uid.trim().isEmpty())
        {
            appExecutors.mainThread().execute(() ->
                    callback.onError(new IllegalArgumentException("User UID cannot be empty.")));
            return;
        }

        remoteDataSource.getUserProfile(
                uid,
                new FirestoreUserProfileDataSource.UserProfileCallback() {
                    @Override

                    public void onSuccess(UserProfile userProfile) {
                        appExecutors.databaseIO().execute(() -> {
                            UserProfileEntity entity =
                                    UserProfileMapper.toEntity(userProfile);
                            userDao.upsertServerProfile(entity);
                            appExecutors.mainThread().execute(() -> callback.onSuccess(userProfile));
                        });
                    }
                    @Override
                    public void onError(Exception exception) {
                        appExecutors.mainThread().execute(() -> callback.onError(exception));
                    }
                }
        );
    }


    /**
     * Attaches a Firestore profile listener and mirrors received profiles into Room.
     * Only the explicit server-confirmed callback represents fresh authorization; cached snapshots are not new grants.
     * A missing server profile is reported and then removed from the local cache.
     * @param uid Firebase user identifier
     * @param callback receives profile, missing-profile, confirmation, or listener/write errors
     * @return registration that the owner must remove when profile observation ends
     */
    public ListenerRegistration startUserProfileSync(
            String uid,
            ProfileSyncCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Profile sync callback is required."
            );
        }


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            appExecutors
                    .mainThread()
                    .execute(
                            () ->
                                    callback.onError(
                                            new IllegalArgumentException(
                                                    "User UID cannot be empty."
                                            )
                                    )
                    );


            return () ->
            {
                // No active listener.
            };
        }


        String cleanUid =
                uid.trim();


        return remoteDataSource
                .listenToUserProfile(
                        cleanUid,
                        new FirestoreUserProfileDataSource
                                .UserProfileListener()
                        {
                            @Override
                            public void onSuccess(
                                    UserProfile userProfile)
                            {
                                appExecutors
                                        .databaseIO()
                                        .execute(
                                                () ->
                                                {
                                                    try
                                                    {
                                                        UserProfileEntity entity =
                                                                UserProfileMapper
                                                                        .toEntity(
                                                                                userProfile
                                                                        );


                                                        userDao
                                                                .upsertServerProfile(
                                                                        entity
                                                                );


                                                        appExecutors
                                                                .mainThread()
                                                                .execute(
                                                                        () ->
                                                                                callback
                                                                                        .onProfileUpdated(
                                                                                                userProfile
                                                                                        )
                                                                );
                                                    }
                                                    catch (Exception exception)
                                                    {
                                                        appExecutors
                                                                .mainThread()
                                                                .execute(
                                                                        () ->
                                                                                callback
                                                                                        .onError(
                                                                                                exception
                                                                                        )
                                                                );
                                                    }
                                                }
                                        );
                            }


                            @Override

                            public void onServerConfirmed(UserProfile userProfile)
                            {
                                // Never interpret a cached Firestore document as a new grant.
                                appExecutors.mainThread().execute(
                                        () -> callback.onServerConfirmed(userProfile));
                            }

                            @Override

                            public void onMissing()
                            {

                                appExecutors.mainThread().execute(callback::onProfileMissing);
                                appExecutors.databaseIO().execute(() -> {
                                    try {
                                        userDao.clearOnLogout(cleanUid);
                                    } catch (Exception exception) {
                                        appExecutors.mainThread().execute(
                                                () -> callback.onError(exception));
                                    }
                                });
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {

                                appExecutors
                                        .mainThread()
                                        .execute(
                                                () ->
                                                        callback
                                                                .onError(
                                                                        exception
                                                                )
                                        );
                            }
                        }
                );
    }

    public interface ProfileSyncCallback
    {
        void onProfileUpdated(
                UserProfile userProfile
        );


        default void onServerConfirmed(UserProfile userProfile) { }

        void onProfileMissing();


        void onError(
                Exception exception
        );
    }

    /** Removes a non-empty UID profile from the local database after sign-out or revocation. */
    public void clearUser(String uid)
    {
        if (uid == null || uid.trim().isEmpty())
        {
            return;
        }
        appExecutors.databaseIO().execute(() -> userDao.clearOnLogout(uid));
    }
    public interface UserCallback
    {
        void onResult(UserProfile userProfile);
    }
    public interface RefreshCallback
    {
        void onSuccess(UserProfile userProfile);
        void onError(Exception exception);
    }
}
