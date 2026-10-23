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

    public boolean revokeIfCurrentUser(String verifiedUid) {
        if (verifiedUid == null || !verifiedUid.equals(authDataSource.getCurrentUserId())) {
            return false;
        }
        signOut();
        if (offlineSession != null) offlineSession.routeLockedSession();
        return true;
    }

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
