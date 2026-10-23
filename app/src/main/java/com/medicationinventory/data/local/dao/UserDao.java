package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.medicationinventory.data.local.entity.UserProfileEntity;


/**
 * Caches server-loaded user profiles for authentication, authorization UI, and live account-state observation. Profile deletion is scoped by UID so signing out one account does not clear another account row.
 */
@Dao
public interface UserDao {
    @Upsert
    void upsertServerProfile(UserProfileEntity userProfile);

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    UserProfileEntity getCurrentUser(String uid);

    @Query("SELECT * FROM user_profiles WHERE uid = :uid LIMIT 1")
    LiveData<UserProfileEntity> observeCurrentUser(String uid);

    @Query("DELETE FROM user_profiles WHERE uid = :uid")
    void clearOnLogout(String uid);
}
