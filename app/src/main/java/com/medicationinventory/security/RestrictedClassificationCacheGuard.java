package com.medicationinventory.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;
import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;



/**
 * Guards the locally cached ABC/VED classifications across Firebase account and profile changes. It revokes the access gate immediately, removes restricted cache data when ownership or permission is uncertain, and grants access only after both the local profile and a server-confirmed profile belong to the current active user with VIEW_ABC_VED. A failed purge remains marked pending so a later startup can retry it.
 */
public final class RestrictedClassificationCacheGuard {
    private static final String TAG = "RestrictedCacheGuard";
    private static final String PREFS_NAME = "restricted_cache_state";
    private static final String KEY_ABC_VED_OWNER_UID = "abc_ved_cache_owner_uid";
    private static final String KEY_PURGE_PENDING = "abc_ved_purge_pending";

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final AuthenticationRepository authenticationRepository;
    private final MedicineClassificationRepository classificationRepository;
    private final RestrictedClassificationAccessGate accessGate;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Observer<UserProfile> profileObserver = this::handleRoomProfileChanged;
    private final FirebaseAuth.AuthStateListener authListener = this::handleAuthChanged;

    private boolean started;
    private boolean purgeInProgress;
    private String observedUid;
    private UserProfile localProfile;
    private UserProfile serverConfirmedProfile;
    private LiveData<UserProfile> observedProfile;
    private ListenerRegistration remoteProfileListener;
    private long listenerGeneration;

    public RestrictedClassificationCacheGuard(
            Context context,
            FirebaseAuth firebaseAuth,
            UserRepository userRepository,
            MedicineClassificationRepository classificationRepository,
            RestrictedClassificationAccessGate accessGate,
            AuthenticationRepository authenticationRepository) {
        if (context == null || firebaseAuth == null || userRepository == null
                || classificationRepository == null || accessGate == null
                || authenticationRepository == null) {
            throw new IllegalArgumentException("Restricted cache guard dependencies required.");
        }
        this.firebaseAuth = firebaseAuth;
        this.userRepository = userRepository;
        this.authenticationRepository = authenticationRepository;
        this.classificationRepository = classificationRepository;
        this.accessGate = accessGate;
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }


    /** Installs the Firebase auth listener once; the listener performs profile and cache checks on the main thread. */
    public void start() {
        if (started) return;
        started = true;
        mainHandler.post(() -> firebaseAuth.addAuthStateListener(authListener));
    }


    private String currentUid() {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private boolean canView(UserProfile profile, String uid) {
        return profile != null && uid != null && uid.equals(profile.getUid())
                && profile.isActive() && profile.can(Permission.VIEW_ABC_VED);
    }


    /**
     * Resets prior account state, schedules a purge when cache ownership is uncertain, and starts fresh profile observation.
     * Listener generations make delayed callbacks from a previous UID harmless.
     */
    private void handleAuthChanged(FirebaseAuth auth) {
        String uid = currentUid();
        if (uid != null) uid = uid.trim();
        if (uid != null && uid.isEmpty()) uid = null;
        if ((uid == null && observedUid == null && started && observedProfile != null)
                || (uid != null && uid.equals(observedUid) && remoteProfileListener != null)) {
            return;
        }


        final long generation = ++listenerGeneration;

        accessGate.revoke();
        serverConfirmedProfile = null;
        localProfile = null;
        detachPreviousProfile();
        observedUid = uid;

        String previousOwner = preferences.getString(KEY_ABC_VED_OWNER_UID, null);
        if (uid == null || !uid.equals(previousOwner)
                || preferences.getBoolean(KEY_PURGE_PENDING, false)) {
            requestRestrictedPurge();
        }
        if (uid == null) return;

        final String trackedUid = uid;
        observedProfile = userRepository.observeUser(uid);
        observedProfile.observeForever(profileObserver);
        remoteProfileListener = userRepository.startUserProfileSync(uid,
                new UserRepository.ProfileSyncCallback() {
                    @Override public void onProfileUpdated(UserProfile profile) {

                    }
                    @Override public void onServerConfirmed(UserProfile profile) {
                        if (generation != listenerGeneration || !trackedUid.equals(observedUid)
                                || !trackedUid.equals(currentUid())) return;

                        if (profile != null && trackedUid.equals(profile.getUid())
                                && (!profile.isActive() || profile.getRole() == null)) {
                            serverConfirmedProfile = null;
                            requestRestrictedPurge();
                            authenticationRepository.revokeIfCurrentUser(trackedUid);
                            return;
                        }
                        if (!canView(profile, trackedUid)) {
                            serverConfirmedProfile = null;
                            requestRestrictedPurge();
                            return;
                        }
                        serverConfirmedProfile = profile;
                        grantIfReady();
                    }
                    @Override public void onProfileMissing() {
                        if (generation != listenerGeneration || !trackedUid.equals(observedUid)
                                || !trackedUid.equals(currentUid())) return;
                        serverConfirmedProfile = null;
                        requestRestrictedPurge();
                        authenticationRepository.revokeIfCurrentUser(trackedUid);
                    }
                    @Override public void onError(Exception exception) {

                        Log.w(TAG, "Unable to confirm ABC/VED profile from server.", exception);
                    }
                });
    }


    private void detachPreviousProfile() {
        if (observedProfile != null) observedProfile.removeObserver(profileObserver);
        observedProfile = null;
        if (remoteProfileListener != null) remoteProfileListener.remove();
        remoteProfileListener = null;
    }


    private void handleRoomProfileChanged(UserProfile profile) {
        localProfile = profile;
        if (!canView(profile, observedUid)) {
            serverConfirmedProfile = null;
            requestRestrictedPurge();
            return;
        }
        grantIfReady();
    }


    /**
     * Opens the gate only after local and server-confirmed permission checks agree for the current UID.
     * The cache-owner write must succeed and any pending purge must finish before restricted reads are allowed.
     */
    private void grantIfReady() {
        String uid = observedUid;
        if (uid == null || !uid.equals(currentUid()) || purgeInProgress
                || preferences.getBoolean(KEY_PURGE_PENDING, false)
                || !canView(localProfile, uid)
                || !canView(serverConfirmedProfile, uid)) return;
        String previousOwner = preferences.getString(KEY_ABC_VED_OWNER_UID, null);
        if (previousOwner != null && !uid.equals(previousOwner)) {
            requestRestrictedPurge();
            return;
        }

        if (!preferences.edit().putString(KEY_ABC_VED_OWNER_UID, uid).commit()) {
            Log.e(TAG, "Could not persist ABC/VED cache owner; access remains denied.");
            return;
        }
        accessGate.grant(uid);
    }


    /**
     * Revokes access before clearing restricted data and persists a pending marker before asynchronous work starts.
     * The marker survives purge failure so access cannot be granted against uncleared data.
     */
    private void requestRestrictedPurge() {
        accessGate.revoke();
        if (purgeInProgress) return;

        if (!preferences.edit().putBoolean(KEY_PURGE_PENDING, true).commit()) {
            Log.e(TAG, "Unable to persist restricted cache purge state.");
            return;
        }
        purgeInProgress = true;
        classificationRepository.clearRestrictedCache(
                new MedicineClassificationRepository.MutationCallback() {
                    @Override public void onSuccess() {
                        purgeInProgress = false;
                        if (!preferences.edit().remove(KEY_ABC_VED_OWNER_UID)
                                .putBoolean(KEY_PURGE_PENDING, false).commit()) {
                            Log.e(TAG, "Unable to mark restricted cache purge complete.");
                            return;
                        }
                        grantIfReady();
                    }
                    @Override public void onError(Exception exception) {
                        purgeInProgress = false;

                        Log.e(TAG, "Restricted ABC/VED cache purge failed.", exception);
                    }
                });
    }
}
