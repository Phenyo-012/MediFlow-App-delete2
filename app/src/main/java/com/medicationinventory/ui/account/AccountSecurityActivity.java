package com.medicationinventory.ui.account;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.MultiFactorInfo;
import com.google.firebase.auth.TotpMultiFactorGenerator;
import com.google.firebase.auth.TotpSecret;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.ui.auth.LoginActivity;


/**
 * Manages optional Firebase authenticator enrollment for the currently signed-in account. It requires a fresh online account/profile check and verified email, asks for the current password before generating the Firebase enrollment secret, displays a QR code or setup key, and enrolls only after Firebase accepts a six-digit code. Pending secrets are cleared when the account changes or setup ends; successful enrollment signs out so the next login completes the new MFA challenge.
 */
public final class AccountSecurityActivity extends AppCompatActivity {
    private AuthenticationRepository authenticationRepository;
    private String ownerUid;
    private TotpSecret pendingSecret;
    private String pendingQrUri;
    private boolean working;

    private LinearLayout verifyEmailPanel;
    private LinearLayout passwordPanel;
    private LinearLayout enrollmentPanel;
    private ProgressBar progress;
    private TextView status;
    private TextView setupKey;
    private ImageView qrImage;
    private TextInputLayout passwordLayout;
    private TextInputLayout codeLayout;
    private TextInputEditText passwordInput;
    private TextInputEditText codeInput;
    private MaterialButton beginButton;
    private MaterialButton confirmButton;

    @Override

    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_account_security);
        MedicationInventoryApplication application =
                (MedicationInventoryApplication) getApplication();
        authenticationRepository = application.getAppContainer().getAuthenticationRepository();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !application.getAppContainer()
                .getOfflineSessionController().isUnlocked()) {
            goToLogin();
            return;
        }
        ownerUid = user.getUid();
        bindViews();
        findViewById(R.id.totpBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.totpSendEmailButton).setOnClickListener(v -> sendVerificationEmail());
        findViewById(R.id.totpRecheckEmailButton).setOnClickListener(v -> checkFirebaseAccount());
        beginButton.setOnClickListener(v -> beginEnrollment());
        confirmButton.setOnClickListener(v -> confirmEnrollment());
        findViewById(R.id.totpCancelButton).setOnClickListener(v -> {
            clearPendingSecret();
            checkFirebaseAccount();
        });
        findViewById(R.id.totpOpenAppButton).setOnClickListener(v -> openAuthenticator());
        checkFirebaseAccount();
    }

    private void bindViews() {
        progress = findViewById(R.id.totpProgress);
        status = findViewById(R.id.totpStatus);
        verifyEmailPanel = findViewById(R.id.totpVerifyEmailPanel);
        passwordPanel = findViewById(R.id.totpPasswordPanel);
        enrollmentPanel = findViewById(R.id.totpEnrollmentPanel);
        passwordLayout = findViewById(R.id.totpPasswordLayout);
        codeLayout = findViewById(R.id.totpCodeLayout);
        passwordInput = findViewById(R.id.totpPasswordInput);
        codeInput = findViewById(R.id.totpCodeInput);
        beginButton = findViewById(R.id.totpBeginButton);
        confirmButton = findViewById(R.id.totpConfirmButton);
        setupKey = findViewById(R.id.totpSetupKey);
        qrImage = findViewById(R.id.totpQrImage);
    }

    private boolean isCurrentAccount() {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return !isFinishing() && !isDestroyed() && user != null
                && ownerUid != null && ownerUid.equals(user.getUid())
                && ((MedicationInventoryApplication) getApplication())
                .getAppContainer().getOfflineSessionController().isUnlocked();
    }

    private void setWorking(boolean busy) {
        working = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        beginButton.setEnabled(!busy);
        confirmButton.setEnabled(!busy);
        findViewById(R.id.totpSendEmailButton).setEnabled(!busy);
        findViewById(R.id.totpRecheckEmailButton).setEnabled(!busy);
        findViewById(R.id.totpCancelButton).setEnabled(!busy);
        findViewById(R.id.totpOpenAppButton).setEnabled(!busy);
    }

    private void showOnly(LinearLayout panel) {
        verifyEmailPanel.setVisibility(panel == verifyEmailPanel ? View.VISIBLE : View.GONE);
        passwordPanel.setVisibility(panel == passwordPanel ? View.VISIBLE : View.GONE);
        enrollmentPanel.setVisibility(panel == enrollmentPanel ? View.VISIBLE : View.GONE);
    }


    private void error(String message) {
        setWorking(false);
        status.setText(message);
    }


    /** Rechecks Firebase identity, email verification, and the active server profile before enabling enrollment. */
    private void checkFirebaseAccount() {
        if (!isCurrentAccount()) { goToLogin(); return; }
        clearPendingSecret();
        showOnly(null);
        setWorking(true);
        status.setText("Checking online account status...");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        user.reload().addOnCompleteListener(task -> {
            if (!isCurrentAccount()) return;
            if (!task.isSuccessful()) {
                error("Could not reach Firebase. Connect to the internet and try again.");
                return;
            }
            FirebaseUser refreshed = FirebaseAuth.getInstance().getCurrentUser();
            if (refreshed == null || !refreshed.isEmailVerified()) {
                showOnly(verifyEmailPanel);
                error("Verify your email before setting up two-factor authentication.");
                return;
            }
            if (hasTotp(refreshed)) {
                showOnly(null);
                error("Authenticator-app two-factor authentication is already enabled for this account.");
                return;
            }

            authenticationRepository.refreshCurrentUser(new AuthenticationRepository.AuthenticationCallback() {
                @Override public void onSuccess(UserProfile profile) {
                    runOnUiThread(() -> {
                        if (!isCurrentAccount()) return;
                        if (profile == null || !profile.isActive() || !ownerUid.equals(profile.getUid())) {
                            error("Account access has been revoked.");
                            return;
                        }
                        showOnly(passwordPanel);
                        error("Enter your password to set up an authenticator app.");
                    });
                }
                @Override public void onInactiveUser() {
                    runOnUiThread(() -> { if (!isFinishing()) goToLogin(); });
                }
                @Override public void onUnauthenticated() {
                    runOnUiThread(() -> { if (!isFinishing()) goToLogin(); });
                }
                @Override public void onError(Exception ex) {
                    runOnUiThread(() -> {
                        if (isCurrentAccount()) error("Unable to verify your active account online. Try again.");
                    });
                }
            });
        });
    }


    private boolean hasTotp(FirebaseUser user) {
        for (MultiFactorInfo info : user.getMultiFactor().getEnrolledFactors()) {
            if (TotpMultiFactorGenerator.FACTOR_ID.equals(info.getFactorId())) return true;
        }
        return false;
    }


    private void sendVerificationEmail() {
        if (working || !isCurrentAccount()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        setWorking(true);
        user.sendEmailVerification().addOnCompleteListener(task -> {
            if (!isCurrentAccount()) return;
            error(task.isSuccessful()
                    ? "Verification email sent. Open the link, then tap 'check again'."
                    : "Couldn't send verification email. Try again later.");
        });
    }

    /**
     * Reauthenticates with the current password, obtains a Firebase MFA session, and generates a TOTP setup secret.
     * The secret is kept in memory until enrollment completes or account/setup state is cleared.
     */
    private void beginEnrollment() {
        if (working || !isCurrentAccount()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user.getEmail();
        if (email == null || email.isEmpty() || !user.isEmailVerified()) {
            checkFirebaseAccount();
            return;
        }
        String password = passwordInput.getText() == null
                ? "" : passwordInput.getText().toString();
        passwordInput.setText(null);
        passwordLayout.setError(null);
        if (password.isEmpty()) {
            passwordLayout.setError("Current password is required.");
            return;
        }
        setWorking(true);
        status.setText("Reauthenticating your account...");
        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                .addOnCompleteListener(result -> {
                    if (!isCurrentAccount()) return;
                    if (!result.isSuccessful()) {
                        error("Password verification failed. Check your password and try again.");
                        return;
                    }
                    if (hasTotp(user)) {
                        error("This account already has an authenticator enrolled.");
                        showOnly(null);
                        return;
                    }
                    user.getMultiFactor().getSession().addOnCompleteListener(sessionTask -> {
                        if (!isCurrentAccount()) return;
                        if (!sessionTask.isSuccessful()) {
                            error("Could not start enrollment. Check the internet connection.");
                            return;
                        }
                        TotpMultiFactorGenerator.generateSecret(sessionTask.getResult())
                                .addOnCompleteListener(secretTask -> {
                                    if (!isCurrentAccount()) return;
                                    if (!secretTask.isSuccessful()) {
                                        error("Could not generate an authenticator setup key.");
                                        return;
                                    }
                                    pendingSecret = secretTask.getResult();
                                    showSecret(user);
                                });
                    });
                });
    }


    private void showSecret(FirebaseUser user) {
        if (pendingSecret == null) return;
        try {
            pendingQrUri = pendingSecret.generateQrCodeUrl(user.getEmail(), "MediFlow");
            Bitmap bitmap = createQrBitmap(pendingQrUri);
            qrImage.setImageBitmap(bitmap);
        } catch (WriterException | RuntimeException ex) {
            qrImage.setImageDrawable(null);
            pendingQrUri = null;
        }
        setupKey.setText(pendingSecret.getSharedSecretKey());
        codeInput.setText(null);
        codeLayout.setError(null);
        showOnly(enrollmentPanel);
        setWorking(false);
        status.setText("Scan the QR code or enter the setup key. Keep it private.");
    }


    /**
     * Converts the Firebase enrollment URI into a QR bitmap; the setup key remains available if QR encoding fails.
     * @param uri Firebase-generated authenticator enrollment URI
     * @return black-and-white QR image
     * @throws WriterException when the URI cannot be encoded
     */
    private Bitmap createQrBitmap(String uri) throws WriterException {
        final int size = 480;
        BitMatrix matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bitmap;
    }

    private void openAuthenticator() {
        if (working || pendingSecret == null || pendingQrUri == null || !isCurrentAccount()) return;
        try {
            pendingSecret.openInOtpApp(pendingQrUri);
        } catch (RuntimeException ex) {
            error("No compatible authenticator app opened. Use the QR code or manual key instead.");
        }
    }

    /** Verifies the entered code with Firebase, clears the secret, and requires a fresh sign-in after enrollment. */
    private void confirmEnrollment() {
        if (working || pendingSecret == null || !isCurrentAccount()) return;
        String code = codeInput.getText() == null ? "" : codeInput.getText().toString().trim();
        codeLayout.setError(null);
        if (!code.matches("[0-9]{6}")) {
            codeLayout.setError("Enter the current six-digit code.");
            return;
        }
        setWorking(true);
        status.setText("Verifying authenticator code with Firebase...");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        user.getMultiFactor().enroll(
                        TotpMultiFactorGenerator.getAssertionForEnrollment(pendingSecret, code),
                        "MediFlow Authenticator")
                .addOnCompleteListener(task -> {
                    if (!isCurrentAccount()) return;
                    if (!task.isSuccessful()) {
                        error("Code could not be verified. Check your device clock or request a fresh setup.");
                        return;
                    }
                    clearPendingSecret();

                    authenticationRepository.signOut();
                    Toast.makeText(this, "2FA enabled. Sign in with your password and authenticator code.",
                            Toast.LENGTH_LONG).show();
                    goToLogin();
                });
    }


    private void clearPendingSecret() {
        pendingSecret = null;
        pendingQrUri = null;
        if (setupKey != null) setupKey.setText("");
        if (codeInput != null) codeInput.setText(null);
        if (qrImage != null) qrImage.setImageDrawable(null);
    }

    private void goToLogin() {
        if (isFinishing()) return;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ownerUid != null && !isCurrentAccount()) goToLogin();
    }

    @Override protected void onDestroy() {
        clearPendingSecret();
        super.onDestroy();
    }
}
