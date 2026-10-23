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
 * Coordinates local and remote User data access for the rest of the application.
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

    public LiveData<UserProfile> observeUser(String uid)
    {
        LiveData<UserProfileEntity> entityLiveData =
                userDao.observeCurrentUser(uid);
        return Transformations.map(
                entityLiveData,
                UserProfileMapper::toDomain);
    }
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
