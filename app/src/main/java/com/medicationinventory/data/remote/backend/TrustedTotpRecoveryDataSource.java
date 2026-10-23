package com.medicationinventory.data.remote.backend;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * Calls the administrator-only TOTP recovery endpoints over HTTPS and maps their status/error body
 * into {@link RecoveryException}. Firebase ID tokens are obtained from the current account, and
 * callbacks are posted on the main thread while HTTP I/O runs on named background threads. This
 * client validates request shape and response identity; role/permission, recent-sign-in, replay,
 * audit, factor removal, and token revocation remain enforced by the trusted backend.
 */
public final class TrustedTotpRecoveryDataSource {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    public interface StatusCallback {
        void onSuccess(boolean enrolled);
        void onError(RecoveryException error);
    }

    public interface ResetCallback {
        void onSuccess();
        void onError(RecoveryException error);
    }

    public static final class RecoveryException extends Exception {
        private final String code;
        private final int statusCode;

        RecoveryException(int statusCode, String code, String message) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        public String getCode() { return code; }
        public int getStatusCode() { return statusCode; }
    }

    private final FirebaseAuth auth;
    private final String backendUrl;
    private final Handler main = new Handler(Looper.getMainLooper());


    public TrustedTotpRecoveryDataSource(FirebaseAuth auth, String backendUrl) {
        if (auth == null || backendUrl == null || backendUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Firebase Auth and backend URL are required.");
        }
        this.auth = auth;
        String normalized = backendUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.backendUrl = normalized;
    }

    /**
     * Fetches enrollment status for a selected user and rejects responses that identify another UID
     * or omit the enrollment field.
     *
     * @param targetUid administrator-selected account UID
     * @param callback main-thread result callback
     */
    public void getStatus(String targetUid, StatusCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is required.");
        if (!isUidValid(targetUid)) {
            main.post(() -> callback.onError(new RecoveryException(400,
                    "INVALID_USER_ID", "Select a valid user.")));
            return;
        }
        withToken(token -> new Thread(() -> {
            try {
                JSONObject body = request("GET", targetUid, token, null);
                if (!targetUid.equals(body.optString("uid")) || !body.has("totpEnrolled")) {
                    throw new RecoveryException(502, "INVALID_RESPONSE", "Invalid recovery status response.");
                }
                boolean enrolled = body.getBoolean("totpEnrolled");
                main.post(() -> callback.onSuccess(enrolled));
            } catch (RecoveryException e) {
                main.post(() -> callback.onError(e));
            } catch (Exception e) {
                main.post(() -> callback.onError(networkError()));
            }
        }, "totp-recovery-status").start(), callback::onError);
    }

    /**
     * Requests an audited authenticator reset using a unique retry identifier. The local length
     * checks provide immediate feedback, while the backend independently enforces fresh
     * authentication, authorization, request uniqueness, and recovery eligibility.
     *
     * @param targetUid account whose TOTP enrollment is to be removed
     * @param reason administrator's required recovery explanation
     * @param requestId UUID used by the backend as an idempotency/audit key
     * @param callback main-thread result callback
     */
    public void reset(String targetUid, String reason, String requestId, ResetCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is required.");
        if (!isUidValid(targetUid) || reason == null || reason.trim().length() < 12
                || reason.trim().length() > 500 || requestId == null || requestId.isEmpty()) {
            main.post(() -> callback.onError(new RecoveryException(400,
                    "INVALID_RECOVERY_REQUEST", "A valid user and reason (12–500 characters) are required.")));
            return;
        }
        withToken(token -> new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("reason", reason.trim());
                payload.put("requestId", requestId);
                JSONObject result = request("POST", targetUid, token, payload);
                if (!result.optBoolean("reset", false) || !targetUid.equals(result.optString("uid"))
                        || !requestId.equals(result.optString("requestId"))) {
                    throw new RecoveryException(502, "INVALID_RESPONSE", "Recovery confirmation was invalid.");
                }
                main.post(callback::onSuccess);
            } catch (RecoveryException e) {
                main.post(() -> callback.onError(e));
            } catch (Exception e) {
                main.post(() -> callback.onError(networkError()));
            }
        }, "totp-recovery-reset").start(), callback::onError);
    }

    private interface WithToken { void run(String token); }
    private interface TokenError { void error(RecoveryException error); }

    private void withToken(WithToken work, TokenError failure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            main.post(() -> failure.error(new RecoveryException(401,
                    "AUTHENTICATION_REQUIRED", "Sign in to manage an authenticator.")));
            return;
        }
        final String callerUid = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            FirebaseUser current = auth.getCurrentUser();
            if (current == null || !callerUid.equals(current.getUid())
                    || !task.isSuccessful() || task.getResult() == null
                    || task.getResult().getToken() == null) {
                failure.error(new RecoveryException(401, "AUTHENTICATION_REQUIRED",
                        "Authentication could not be verified."));
                return;
            }
            work.run(task.getResult().getToken());
        });
    }

    private JSONObject request(String method, String uid, String token, JSONObject body)
            throws Exception {
        String encodedUid = URLEncoder.encode(uid, StandardCharsets.UTF_8.name());
        URL url = new URL(backendUrl + "/api/admin/users/" + encodedUid + "/totp-recovery");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setUseCaches(false);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            String text = readBody(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            JSONObject response;
            try { response = new JSONObject(text); }
            catch (Exception ignored) {
                throw new RecoveryException(status, "INVALID_RESPONSE",
                        "Unexpected backend response. Check whether the recovery API is deployed.");
            }
            if (status < 200 || status >= 300) {
                throw new RecoveryException(status,
                        response.optString("code", "RECOVERY_REQUEST_FAILED"),
                        response.optString("message", "Recovery request failed."));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream input) throws Exception {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                if (out.length() > 32768) {
                    throw new IllegalStateException("Backend response too large.");
                }
            }
            return out.toString();
        }
    }

    private static boolean isUidValid(String uid) {
        return uid != null && !uid.trim().isEmpty() && uid.length() <= 256
                && !uid.contains("/") && !uid.contains("\\");
    }

    private static RecoveryException networkError() {
        return new RecoveryException(0, "NETWORK_ERROR",
                "Could not confirm the recovery request. Check account status and audit history before retrying.");
    }
}
