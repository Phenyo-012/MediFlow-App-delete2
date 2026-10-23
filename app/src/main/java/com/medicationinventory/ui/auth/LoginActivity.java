package com.medicationinventory.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.Intent;
import android.os.Build;
import android.app.KeyguardManager;
import android.content.Context;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.medicationinventory.data.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.medicationinventory.security.OfflineSessionController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.ui.dashboard.DashboardActivity;



/**
 * Owns the sign-in screen presentation and sends password and TOTP input to LoginViewModel. It renders separate idle, loading, second-factor, success, and error states, and offers device-authenticated offline unlock only when the current Firebase account has an eligible saved enrollment.
 */
public class LoginActivity extends AppCompatActivity
{

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton signInButton;
    private ProgressBar loginProgress;
    private TextView loginError;
    private LoginViewModel loginViewModel;
    private MaterialButton offlineUnlockButton;
    private TextInputLayout totpLayout;
    private TextInputEditText totpInput;
    private MaterialButton verifyTotpButton;
    private MaterialButton cancelTotpButton;
    private OfflineSessionController offlineSession;
    private UserRepository userRepository;
    private AuthenticationRepository authenticationRepository;
    private final FirebaseAuth.AuthStateListener authStateListener =
            auth -> renderOfflineUnlockAvailability();

    @Override

    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        bindViews();
        setupViewModel();
        observeLoginState();

        signInButton.setOnClickListener(view -> attemptLogin());
        offlineUnlockButton.setOnClickListener(view -> attemptOfflineUnlock());
        verifyTotpButton.setOnClickListener(view -> {
            String code = totpInput.getText() == null ? "" : totpInput.getText().toString();
            loginViewModel.verifyTotp(code);
        });
        cancelTotpButton.setOnClickListener(view -> {
            totpInput.setText("");
            loginViewModel.cancelTotp();
            renderOfflineUnlockAvailability();
        });
        renderOfflineUnlockAvailability();
    }
    @Override protected void onStart() {
        super.onStart();
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override protected void onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        super.onStop();
    }


    private void bindViews()
    {
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        signInButton = findViewById(R.id.signInButton);
        loginProgress = findViewById(R.id.loginProgress);
        loginError = findViewById(R.id.loginError);
        offlineUnlockButton = findViewById(R.id.offlineUnlockButton);
        totpLayout = findViewById(R.id.totpLayout);
        totpInput = findViewById(R.id.totpInput);
        verifyTotpButton = findViewById(R.id.verifyTotpButton);
        cancelTotpButton = findViewById(R.id.cancelTotpButton);
    }

    private void setupViewModel() {
        MedicationInventoryApplication application = (MedicationInventoryApplication) getApplication();
        authenticationRepository = application.getAppContainer().getAuthenticationRepository();
        userRepository = application.getAppContainer().getUserRepository();
        offlineSession = application.getAppContainer().getOfflineSessionController();
        LoginViewModelFactory factory = new LoginViewModelFactory(authenticationRepository);
        loginViewModel = new ViewModelProvider(this, factory).get(LoginViewModel.class);
    }

    private void observeLoginState()
    {
        loginViewModel.getLoginState().observe(this, state ->
        {
            if (state == null)
            {
                return;
            }
            switch (state.getStatus())
            {
                case IDLE:
                    showIdleState();
                    break;

                case LOADING:
                    showLoadingState();
                    break;

                case SUCCESS:
                    showSuccessState(state.getUserProfile());
                    break;

                case ERROR:
                    showErrorState(state.getMessage());
                    break;
                case MFA_REQUIRED:
                    showTotpChallenge(null);
                    break;
                case MFA_ERROR:
                    showTotpChallenge(state.getMessage());
                    break;
            }
        });
    }

    private void attemptLogin()
    {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        loginError.setVisibility(View.GONE);

        String email = emailInput.getText() == null ? "" : emailInput.getText().toString().trim();
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
        boolean valid = true;

        if (email.isEmpty())
        {
            emailLayout.setError("Email address is required.");
            valid = false;
        }

        if (password.isEmpty())
        {
            passwordLayout.setError("Password is required.");
            valid = false;
        }

        if (!valid)
        {
            return;
        }

        loginViewModel.signIn(email, password);
    }



    private void renderOfflineUnlockAvailability() {
        offlineUnlockButton.setVisibility(View.GONE);
        if (loginViewModel != null && loginViewModel.isTotpPending()) return;

        String uid = authenticationRepository.getCurrentUserId();

        if (uid == null || !offlineSession.mayUnlock(uid)) {
            return;
        }

        userRepository.getUser(uid, profile -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }

            String currentUid = authenticationRepository.getCurrentUserId();

            boolean eligible =
                    uid.equals(currentUid)
                            && offlineSession.mayUnlock(uid)
                            && (loginViewModel == null || !loginViewModel.isTotpPending())
                            && profile != null
                            && profile.isActive()
                            && profile.getRole() != null
                            && profile.getLastValidatedAt() > 0
                            && uid.equals(profile.getUid());

            offlineUnlockButton.setVisibility(
                    eligible ? View.VISIBLE : View.GONE
            );
        });
    }


    private void attemptOfflineUnlock() {
        String uid = authenticationRepository.getCurrentUserId();
        if (!offlineSession.mayUnlock(uid)) {
            showErrorState("Offline access expired or unavailable. Connect to the internet and sign in.");
            return;
        }
        offlineUnlockButton.setEnabled(false);
        userRepository.getUser(uid, profile -> {
            if (isFinishing() || isDestroyed()) return;
            if (profile == null || !profile.isActive() || profile.getRole() == null
                    || profile.getLastValidatedAt() <= 0 || !uid.equals(profile.getUid())
                    || !offlineSession.mayUnlock(uid)) {
                offlineUnlockButton.setEnabled(true);
                showErrorState("No eligible cached account. Connect to the internet to sign in.");
                return;
            }
            final int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean secure = keyguard != null && keyguard.isDeviceSecure();
            boolean available = Build.VERSION.SDK_INT < 30 ? secure
                    : BiometricManager.from(this).canAuthenticate(authenticators)
                      == BiometricManager.BIOMETRIC_SUCCESS;
            if (!available) {
                offlineUnlockButton.setEnabled(true);
                showErrorState("Set up a device screen lock to use offline unlock.");
                return;
            }
            BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            if (!uid.equals(authenticationRepository.getCurrentUserId())
                                    || !offlineSession.unlockOffline(profile)) {
                                offlineUnlockButton.setEnabled(true);
                                showErrorState("Session is no longer eligible. Sign in online.");
                                return;
                            }
                            showSuccessState(profile);
                        }
                        @Override public void onAuthenticationError(int code, CharSequence message) {
                            super.onAuthenticationError(code, message);
                            offlineUnlockButton.setEnabled(true);
                            showErrorState("Offline unlock was not completed.");
                        }
                    });
            BiometricPrompt.PromptInfo.Builder infoBuilder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock MediFlow")
                    .setSubtitle("Confirm your device identity to continue offline");
            if (Build.VERSION.SDK_INT >= 30) {
                infoBuilder.setAllowedAuthenticators(authenticators);
            } else {

                infoBuilder.setDeviceCredentialAllowed(true);
            }
            BiometricPrompt.PromptInfo info = infoBuilder.build();
            prompt.authenticate(info);
        });
    }


    private void showIdleState()
    {
        loginProgress.setVisibility(View.GONE);
        loginError.setVisibility(View.GONE);
        emailLayout.setVisibility(View.VISIBLE);
        passwordLayout.setVisibility(View.VISIBLE);
        signInButton.setVisibility(View.VISIBLE);
        signInButton.setEnabled(true);
        emailInput.setEnabled(true);
        passwordInput.setEnabled(true);
        totpLayout.setVisibility(View.GONE);
        verifyTotpButton.setVisibility(View.GONE);
        cancelTotpButton.setVisibility(View.GONE);
        renderOfflineUnlockAvailability();
    }

    private void showLoadingState()
    {
        loginError.setVisibility(View.GONE);
        loginProgress.setVisibility(View.VISIBLE);
        signInButton.setEnabled(false);
        emailInput.setEnabled(false);
        passwordInput.setEnabled(false);
        verifyTotpButton.setEnabled(false);
    }


    private void showTotpChallenge(String message) {
        loginProgress.setVisibility(View.GONE);
        emailLayout.setVisibility(View.GONE);
        passwordLayout.setVisibility(View.GONE);
        signInButton.setVisibility(View.GONE);
        offlineUnlockButton.setVisibility(View.GONE);
        totpLayout.setVisibility(View.VISIBLE);
        verifyTotpButton.setVisibility(View.VISIBLE);
        cancelTotpButton.setVisibility(View.VISIBLE);
        verifyTotpButton.setEnabled(true);
        totpInput.setEnabled(true);
        totpLayout.setError(null);
        if (message != null && !message.isEmpty()) {
            loginError.setText(message);
            loginError.setVisibility(View.VISIBLE);
        } else {
            loginError.setVisibility(View.GONE);
        }
    }


    private void showSuccessState(UserProfile userProfile)
    {
        loginProgress.setVisibility(View.GONE);
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showErrorState(String message)
    {
        loginProgress.setVisibility(View.GONE);
        signInButton.setEnabled(true);
        emailInput.setEnabled(true);
        passwordInput.setEnabled(true);
        loginError.setText(message);
        loginError.setVisibility(View.VISIBLE);
        if (!loginViewModel.isTotpPending()) {
            emailLayout.setVisibility(View.VISIBLE);
            passwordLayout.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.VISIBLE);
            totpLayout.setVisibility(View.GONE);
            verifyTotpButton.setVisibility(View.GONE);
            cancelTotpButton.setVisibility(View.GONE);
        }
    }
}