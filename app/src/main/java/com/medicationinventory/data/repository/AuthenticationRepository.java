package com.medicationinventory.data.repository;

import com.medicationinventory.data.remote.auth.FirebaseAuthDataSource;
import com.medicationinventory.data.remote.firestore.FirestoreUserProfileDataSource;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.security.OfflineSessionController;
import com.google.firebase.auth.MultiFactorResolver;


/**
 * Coordinates Firebase identity with the current application profile and the
 * optional protected offline session. A successful credential check is followed
 * by an active-profile check before the caller receives an authenticated profile.
 */
public class AuthenticationRepository {
    private final FirebaseAuthDataSource authDataSource;
    private final UserRepository userRepository;
    private final OfflineSessionController offlineSession;

    public AuthenticationRepository(FirebaseAuthDataSource authDataSource, UserRepository userRepository)
    {
        this(authDataSource, userRepository, null);
    }
    public AuthenticationRepository(FirebaseAuthDataSource authDataSource,
                                    UserRepository userRepository, OfflineSessionController offlineSession) {
        this.authDataSource = authDataSource;
        this.userRepository = userRepository;
        this.offlineSession = offlineSession;
    }
    public boolean isSignedIn()
    {
        return authDataSource.isSignedIn();
    }
    public String getCurrentUserId()
    {
        return authDataSource.getCurrentUserId();
    }

    /**
     * Authenticates with Firebase, then refreshes and validates the matching active MediFlow profile.
     * When Firebase requires TOTP, the resolver is passed to the callback and profile loading waits for completion.
     * @param email account email; surrounding whitespace is trimmed before authentication
     * @param password Firebase password
     * @param callback receives success, MFA-required, inactive, unauthenticated, or error outcomes
     */
    public void signIn(String email, String password, AuthenticationCallback callback)
    {
        if (email == null || email.trim().isEmpty())
        {
            callback.onError(new IllegalArgumentException("Email address is required."));
            return;
        }

        if (password == null || password.isEmpty())
        {
            callback.onError(new IllegalArgumentException("Password is required."));
            return;
        }
        authDataSource.signIn(
                email.trim(),
                password,
                new FirebaseAuthDataSource.AuthCallback()
                {
                    @Override

                    public void onSuccess(String uid)
                    {
                        loadAuthenticatedUser(uid, callback, true);
                    }
                    @Override

                    public void onError(Exception exception)
                    {
                        callback.onError(exception);
                    }
                    @Override

                    public void onSecondFactorRequired(MultiFactorResolver resolver)
                    {
                        callback.onSecondFactorRequired(resolver);
                    }
                }
        );
    }


    /**
     * Completes a pending Firebase TOTP challenge and loads the profile only after Firebase accepts the code.
     * @param resolver Firebase resolver returned by the initial password sign-in
     * @param code six-digit authenticator code
     * @param callback receives the validated profile or the failure outcome
     */
    public void completeTotpSignIn(MultiFactorResolver resolver, String code,
                                   AuthenticationCallback callback)
    {
        authDataSource.resolveTotpSignIn(resolver, code, new FirebaseAuthDataSource.AuthCallback() {
            @Override public void onSuccess(String uid) {
                // Profile refresh and device enrollment run only AFTER Firebase resolves MFA.
                loadAuthenticatedUser(uid, callback, true);
            }
            @Override public void onError(Exception exception) {
                callback.onError(exception);
            }
        });
    }


    /**
     * Revalidates the current Firebase identity against its server profile after app start or session restoration.
     * No profile request is made when Firebase has no current UID.
     * @param callback receives an active profile or the relevant signed-out, inactive, or error result
     */
    public void refreshCurrentUser(AuthenticationCallback callback)
    {
        String uid = authDataSource.getCurrentUserId();
        if (uid == null)
        {
            callback.onUnauthenticated();
            return;
        }
        loadAuthenticatedUser(uid, callback, false);
    }

    /**
     * Refreshes authorization data after sign-in or revalidation. The UID comparison
     * prevents a delayed Firestore callback from authenticating a different session.
     */
    private void loadAuthenticatedUser(String uid, AuthenticationCallback callback, boolean explicitSignIn) {
        userRepository.refreshUserProfile(
                uid,
                new UserRepository.RefreshCallback() {
                    @Override
                    public void onSuccess(UserProfile userProfile)
                    {

                        if (!uid.equals(authDataSource.getCurrentUserId())) {
                            callback.onUnauthenticated();
                            return;
                        }
                        if (!userProfile.isActive())
                        {
                            revokeIfCurrentUser(uid);
                            callback.onInactiveUser();
                            return;
                        }
                        if (offlineSession != null && !(explicitSignIn
                                ? offlineSession.onOnlineValidation(userProfile)
                                : offlineSession.onServerRevalidation(userProfile))) {
                            callback.onError(new IllegalStateException(
                                    "Secure device session could not be established."));
                            return;
                        }
                        callback.onSuccess(userProfile);
                    }
                    @Override
                    public void onError(Exception exception)
                    {

                        if (exception instanceof FirestoreUserProfileDataSource.ServerProfileMissingException) {
                            revokeIfCurrentUser(uid);
                        }
                        callback.onError(exception);
                    }
                }
        );
    }

    /**
     * Signs out and locks local access only if the supplied, previously verified UID is still current.
     * @param verifiedUid UID associated with the profile or revocation event
     * @return true when this call revoked the matching current session
     */
    public boolean revokeIfCurrentUser(String verifiedUid) {
        if (verifiedUid == null || !verifiedUid.equals(authDataSource.getCurrentUserId())) {
            return false;
        }
        signOut();
        if (offlineSession != null) offlineSession.routeLockedSession();
        return true;
    }

    /** Clears saved offline enrollment, Firebase identity, and the local cached profile for the signed-in UID. */
    public void signOut()
    {
        String uid = authDataSource.getCurrentUserId();
        if (offlineSession != null) offlineSession.clearOnSignOut();
        authDataSource.signOut();
        if (uid != null)
        {
            userRepository.clearUser(uid);
        }
    }

    public interface AuthenticationCallback
    {
        void onSuccess(UserProfile userProfile);

        default void onSecondFactorRequired(MultiFactorResolver resolver) {
            onError(new IllegalStateException("Second-factor verification is required."));
        }
        void onInactiveUser();
        void onUnauthenticated();
        void onError(Exception exception);
    }
}
