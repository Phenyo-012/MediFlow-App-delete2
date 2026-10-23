package com.medicationinventory.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.model.domain.UserProfile;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.MultiFactorResolver;



/**
 * Implements the Login View Model behavior for its Android screen and the application state it presents.
 */
public class LoginViewModel extends ViewModel
{
    private final AuthenticationRepository authenticationRepository;
    private final MutableLiveData<LoginState> loginState = new MutableLiveData<>(LoginState.idle());

    private MultiFactorResolver pendingTotpResolver;

    public LoginViewModel(AuthenticationRepository authenticationRepository)
    {
        this.authenticationRepository =
                authenticationRepository;
    }

    public LiveData<LoginState> getLoginState()
    {
        return loginState;
    }

    public void signIn(String email, String password)
    {
        pendingTotpResolver = null;
        loginState.setValue(LoginState.loading());
        authenticationRepository.signIn(email, password,
                new AuthenticationRepository.AuthenticationCallback()
                {
                    @Override

                    public void onSuccess(UserProfile userProfile)
                    {
                        pendingTotpResolver = null;
                        loginState.setValue(LoginState.success(userProfile));
                    }
                    @Override

                    public void onSecondFactorRequired(MultiFactorResolver resolver)
                    {
                        pendingTotpResolver = resolver;
                        loginState.setValue(LoginState.mfaRequired());
                    }
                    @Override

                    public void onInactiveUser()
                    {
                        loginState.setValue(LoginState.error("This account is inactive."));
                    }
                    @Override

                    public void onUnauthenticated()
                    {
                        loginState.setValue(LoginState.error("Authentication is required."));
                    }
                    @Override
                    public void onError(Exception exception)
                    {
                        String message;

                        if (exception instanceof FirebaseNetworkException)
                        {
                            message = "No connection. Check your internet connection and try again.";
                        }
                        else if (exception instanceof FirebaseAuthInvalidCredentialsException || exception instanceof FirebaseAuthInvalidUserException)
                        {
                            message = "Invalid email or password.";
                        }
                        else if (exception instanceof IllegalStateException)
                        {
                            message = "Your account is not configured for this application. Contact an administrator.";
                        }
                        else
                        {
                            message = "Unable to sign in. Please try again.";
                        }

                        loginState.setValue(LoginState.error(message));
                    }
                }
        );
    }
    public boolean isTotpPending() {
        return pendingTotpResolver != null;
    }


    public void cancelTotp() {
        pendingTotpResolver = null;
        loginState.setValue(LoginState.idle());
    }


    public void verifyTotp(String code) {
        if (pendingTotpResolver == null) {
            loginState.setValue(LoginState.error("Sign in with your password first."));
            return;
        }
        String normalized = code == null ? "" : code.trim();
        if (!normalized.matches("[0-9]{6}")) {
            loginState.setValue(LoginState.mfaError("Enter the 6-digit authenticator code."));
            return;
        }
        loginState.setValue(LoginState.loading());
        authenticationRepository.completeTotpSignIn(pendingTotpResolver, normalized,
                new AuthenticationRepository.AuthenticationCallback() {
                    @Override public void onSuccess(UserProfile profile) {
                        pendingTotpResolver = null;
                        loginState.setValue(LoginState.success(profile));
                    }
                    @Override public void onInactiveUser() {
                        pendingTotpResolver = null;
                        loginState.setValue(LoginState.error("This account is inactive."));
                    }
                    @Override public void onUnauthenticated() {
                        pendingTotpResolver = null;
                        loginState.setValue(LoginState.error("Sign in again to continue."));
                    }
                    @Override public void onError(Exception exception) {
                        // Keep the resolver so a mistyped or expired OTP can be retried.
                        loginState.setValue(LoginState.mfaError(
                                "Code not accepted. Check your authenticator and try again."));
                    }
                });
    }

    public static class LoginState
    {
        public enum Status
        {
            IDLE, LOADING, SUCCESS, ERROR, MFA_REQUIRED, MFA_ERROR
        }
        private final Status status;
        private final UserProfile userProfile;
        private final String message;

        private LoginState(Status status, UserProfile userProfile, String message)
        {
            this.status = status;
            this.userProfile = userProfile;
            this.message = message;
        }

        public static LoginState idle()
        {
            return new LoginState(
                    Status.IDLE,
                    null,
                    null
            );
        }

        public static LoginState loading()
        {
            return new LoginState(
                    Status.LOADING,
                    null,
                    null
            );
        }

        public static LoginState success(UserProfile userProfile)
        {
            return new LoginState(
                    Status.SUCCESS,
                    userProfile,
                    null
            );
        }

        public static LoginState mfaRequired() {
            return new LoginState(Status.MFA_REQUIRED, null, null);
        }

        public static LoginState mfaError(String message) {
            return new LoginState(Status.MFA_ERROR, null, message);
        }

        public static LoginState error(String message)
        {
            return new LoginState(
                    Status.ERROR,
                    null,
                    message
            );
        }
        public Status getStatus()
        {
            return status;
        }
        public UserProfile getUserProfile()
        {
            return userProfile;
        }
        public String getMessage()
        {
            return message;
        }
    }
}