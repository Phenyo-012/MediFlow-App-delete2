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
 * Links or unlinks a barcode on the shared medicine master through the trusted
 * backend. Requests are bound to the current Firebase user and use the admin
 * barcode endpoints; server authorization and duplicate-code policy remain
 * authoritative, while callbacks report the remote mutation outcome.
 */
public final class TrustedMedicineBarcodeDataSource {
    private final FirebaseAuth auth;
    private final String baseUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    public TrustedMedicineBarcodeDataSource(FirebaseAuth auth, String baseUrl) {
        if (auth == null || baseUrl == null || !baseUrl.startsWith("https://"))
            throw new IllegalArgumentException("Firebase Auth and HTTPS backend are required.");
        this.auth = auth;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void link(String medicineId, String barcode, Callback callback) {
        mutate(medicineId, barcode, true, callback);
    }

    public void unlink(String medicineId, String barcode, Callback callback) {
        mutate(medicineId, barcode, false, callback);
    }

    private void mutate(String medicineId, String barcode, boolean linking, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required.");
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            fail(callback, new RemoteException(401, "Sign in before changing shared barcodes."));
            return;
        }
        final String uid = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!sameUser(uid) || !task.isSuccessful() || task.getResult() == null ||
                    task.getResult().getToken() == null) {
                fail(callback, new RemoteException(401, "Unable to verify your session."));
                return;
            }
            String token = task.getResult().getToken();
            new Thread(() -> execute(uid, token, medicineId, barcode, linking, callback),
                    "trusted-barcode-mutation").start();
        });
    }

    private void execute(String uid, String token, String medicineId, String barcode,
                         boolean linking, Callback callback) {
        HttpURLConnection connection = null;
        try {
            if (!sameUser(uid)) throw new RemoteException(401, "Session changed.");
            String encodedId = URLEncoder.encode(medicineId, StandardCharsets.UTF_8.name());
            URL url = new URL(baseUrl + "/api/admin/medicines/" + encodedId + "/barcodes");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(linking ? "POST" : "DELETE");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            JSONObject payload = new JSONObject();
            payload.put("barcode", barcode);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            String responseBody = read(connection, status);
            JSONObject json;
            try { json = new JSONObject(responseBody); }
            catch (Exception malformed) { json = null; }
            if (status < 200 || status >= 300) {
                throw new RemoteException(status, json == null ?
                        "Barcode request rejected (HTTP " + status + ")." :
                        json.optString("message", "Barcode request rejected."));
            }
            if (json == null) throw new RemoteException(0,
                    "Cloud returned an invalid result. Reconcile before retrying.");
            if (!medicineId.equals(json.optString("medicineId")) ||
                    !barcode.equals(json.optString("barcode")) ||
                    !json.has("changed") || !(json.opt("changed") instanceof Boolean)) {
                throw new RemoteException(0,
                        "Unverified cloud response. Check synchronisation before trying again.");
            }
            if (!sameUser(uid)) throw new RemoteException(0,
                    "Session changed after the request. Check cloud state before retrying.");
            mainHandler.post(() -> callback.onSuccess());
        } catch (RemoteException error) {
            fail(callback, error);
        } catch (Exception error) {

            fail(callback, new RemoteException(0,
                    "Cloud result is uncertain. Do not retry until the barcode is reconciled."));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(HttpURLConnection connection, int status) throws Exception {
        InputStream input = status >= 200 && status < 300 ?
                connection.getInputStream() : connection.getErrorStream();
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input,
                StandardCharsets.UTF_8))) {
            StringBuilder value = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (value.length() + n > 16384) throw new IllegalStateException("Response too large.");
                value.append(buf, 0, n);
            }
            return value.toString();
        }
    }

    private boolean sameUser(String uid) {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && uid.equals(user.getUid());
    }

    private void fail(Callback callback, RemoteException error) {
        mainHandler.post(() -> callback.onError(error));
    }
    public interface Callback {
        void onSuccess();
        void onError(RemoteException error);
    }
    public static final class RemoteException extends Exception {
        private final int statusCode;

        public RemoteException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        public int getStatusCode() { return statusCode; }
    }
}
