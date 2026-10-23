package com.medicationinventory.data.remote.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthMultiFactorException;
import com.google.firebase.auth.MultiFactorInfo;
import com.google.firebase.auth.MultiFactorResolver;
import com.google.firebase.auth.TotpMultiFactorGenerator;


/**
 * Adapts Firebase email/password and authenticator-app sign-in to repository
 * callbacks. Application-profile authorization is checked by the repository after
 * Firebase establishes the user's identity.
 */
public class FirebaseAuthDataSource
{
    private final FirebaseAuth firebaseAuth;

    /** Creates a data source backed by the process-wide Firebase Authentication instance. */
    public FirebaseAuthDataSource()
    {
        this.firebaseAuth = FirebaseAuth.getInstance();
    }
    /** @return whether Firebase currently holds an authenticated user session */
    public boolean isSignedIn()
    {
        return firebaseAuth.getCurrentUser() != null;
    }
    /** @return the current Firebase UID, or {@code null} when signed out */
    public String getCurrentUserId()
    {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null)
        {
            return null;
        }
        return user.getUid();
    }
    /** @return the current Firebase email address, or {@code null} when signed out */
    public String getCurrentUserEmail()
    {
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null)
        {
            return null;
        }
        return user.getEmail();
    }

    /**
     * Starts Firebase email/password authentication. Firebase may return an MFA resolver instead of a UID
     * when the account has an enrolled second factor; application profile and permission checks happen
     * later in {@link com.medicationinventory.data.repository.AuthenticationRepository}.
     * @param email Firebase account email
     * @param password Firebase account password
     * @param callback receives the UID, MFA challenge, or authentication error
     */
    public void signIn(
            String email,
            String password,
            AuthCallback callback)
    {
        firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                    {
                        FirebaseUser user =
                                firebaseAuth.getCurrentUser();
                        if (user != null)
                        {
                            callback.onSuccess(user.getUid());
                        }
                        else
                        {
                            callback.onError
                                    (
                                            new IllegalStateException("Authentication succeeded but no user session was found."));
                        }

                    }
                    else
                    {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthMultiFactorException)
                        {
                            callback.onSecondFactorRequired(
                                    ((FirebaseAuthMultiFactorException) exception).getResolver());
                        }
                        else if (exception != null)
                        {
                            callback.onError(exception);
                        }
                        else
                        {
                            callback.onError(new Exception("Authentication failed."));
                        }
                    }
                });
    }
    /**
     * Validates the six-digit authenticator code and resolves Firebase's pending MFA
     * challenge with the enrolled authenticator-app factor.
     */
    public void resolveTotpSignIn(MultiFactorResolver resolver, String code,
                                  AuthCallback callback)
    {
        if (resolver == null || code == null || !code.matches("[0-9]{6}"))
        {
            callback.onError(new IllegalArgumentException("Enter a valid 6-digit code."));
            return;
        }

        // This flow accepts authenticator-app factors from Firebase's MFA hints.
        MultiFactorInfo totpFactor = null;
        for (MultiFactorInfo hint : resolver.getHints())
        {
            if (TotpMultiFactorGenerator.FACTOR_ID.equals(hint.getFactorId()))
            {
                totpFactor = hint;
                break;
            }
        }
        if (totpFactor == null)
        {
            callback.onError(new IllegalStateException(
                    "No authenticator-app factor is enrolled on this account."));
            return;
        }

        resolver.resolveSignIn(TotpMultiFactorGenerator.getAssertionForSignIn(
                        totpFactor.getUid(), code))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful())
                    {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null)
                        {
                            callback.onSuccess(user.getUid());
                        }
                        else
                        {
                            callback.onError(new IllegalStateException(
                                    "TOTP verified but no authenticated session was found."));
                        }
                    }
                    else
                    {
                        Exception error = task.getException();
                        callback.onError(error == null
                                ? new IllegalStateException("Unable to verify authenticator code.")
                                : error);
                    }
                });
    }

    /** Clears the Firebase Authentication session on this device. */
    public void signOut()
    {
        firebaseAuth.signOut();
    }
    public interface AuthCallback
    {
        void onSuccess(String uid);
        void onError(Exception exception);

        default void onSecondFactorRequired(MultiFactorResolver resolver)
        {
            onError(new IllegalStateException("Second-factor verification is required."));
        }
    }
}