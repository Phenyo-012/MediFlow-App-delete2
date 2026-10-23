package com.medicationinventory.data.remote.backend;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.medicationinventory.model.domain.Medicine;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


/**
 * Creates, edits, and deactivates shared medicine master records through the
 * trusted admin API. It serializes the domain medicine into the endpoint's JSON
 * payload and reports the server response asynchronously; the backend enforces
 * administrator permissions and master-data validation.
 */
public final class TrustedMedicineMasterDataSource {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    private static final int MAX_RESPONSE_CHARS = 16384;

    private final FirebaseAuth auth;
    private final String backendUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    public TrustedMedicineMasterDataSource(FirebaseAuth auth, String backendUrl) {
        if (auth == null) throw new IllegalArgumentException("Firebase Auth is required.");
        if (backendUrl == null || !backendUrl.trim().startsWith("https://")) {
            throw new IllegalArgumentException("An HTTPS trusted-backend URL is required.");
        }
        this.auth = auth;
        this.backendUrl = backendUrl.trim().replaceAll("/+$", "");
    }


    public void createMedicine(Medicine medicine, MutationCallback callback) {
        mutate(medicine, false, callback);
    }

    public void updateMedicine(Medicine medicine, MutationCallback callback) {
        mutate(medicine, true, callback);
    }


    private void mutate(Medicine medicine, boolean editing, MutationCallback callback) {
        if (callback == null) throw new IllegalArgumentException("A callback is required.");
        if (medicine == null || medicine.getMedicineId() == null
                || medicine.getMedicineId().trim().isEmpty()) {
            error(callback, new MedicineRemoteException(400, "INVALID_MEDICINE",
                    "A medicine ID is required."));
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            error(callback, new MedicineRemoteException(401, "AUTHENTICATION_REQUIRED",
                    "Sign in to save medicines to the shared inventory."));
            return;
        }
        final String initiatingUid = user.getUid();

        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!sameUser(initiatingUid) || !task.isSuccessful()
                    || task.getResult() == null || task.getResult().getToken() == null) {
                error(callback, new MedicineRemoteException(401, "AUTHENTICATION_REQUIRED",
                        "Your session changed or could not be verified."));
                return;
            }
            final String token = task.getResult().getToken();
            new Thread(() -> execute(medicine, editing, initiatingUid, token, callback),
                    "medicine-master-cloud-mutation").start();
        });
    }


    private void execute(Medicine medicine, boolean editing, String initiatingUid,
                         String token, MutationCallback callback) {
        HttpURLConnection connection = null;
        try {
            if (!sameUser(initiatingUid)) throw new MedicineRemoteException(401,
                    "SESSION_CHANGED", "Your sign-in changed. Please try again.");
            String path = "/api/admin/medicines";
            if (editing) {
                path += "/" + URLEncoder.encode(medicine.getMedicineId(),
                        StandardCharsets.UTF_8.name());
            }
            connection = (HttpURLConnection) new URL(backendUrl + path).openConnection();
            connection.setRequestMethod(editing ? "PUT" : "POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            JSONObject payload = new JSONObject();
            if (editing) payload.put("expectedSyncVersion", medicine.getSyncVersion());
            else payload.put("medicineId", medicine.getMedicineId());
            payload.put("medicineCode", medicine.getMedicineCode());
            payload.put("medicineName", medicine.getMedicineName());
            payload.put("genericName", optional(medicine.getGenericName()));
            payload.put("category", medicine.getCategory());
            payload.put("strength", optional(medicine.getStrength()));
            payload.put("dosageForm", medicine.getDosageForm());
            payload.put("unitOfMeasure", medicine.getUnitOfMeasure());
            payload.put("reorderLevel", medicine.getReorderLevel());
            payload.put("targetStockLevel", medicine.getTargetStockLevel());
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(bytes);
            }
            int status = connection.getResponseCode();
            String body = readResponse(status >= 200 && status < 300 ?
                    connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                JSONObject response = tryJson(body);
                String code = response == null ? "MEDICINE_CLOUD_ERROR" :
                        response.optString("code", "MEDICINE_CLOUD_ERROR");
                String message = response == null ? "Cloud save failed (HTTP " + status + ")." :
                        response.optString("message", "Cloud save failed (HTTP " + status + ").");
                throw new MedicineRemoteException(status, code, message);
            }
            JSONObject result = new JSONObject(body);
            String resultId = result.optString("medicineId", "");
            Object versionValue = result.opt("syncVersion");
            if (!medicine.getMedicineId().equals(resultId) ||
                    !(versionValue instanceof Number) ||
                    ((Number) versionValue).longValue() <= 0 ||
                    ((Number) versionValue).doubleValue() !=
                            ((Number) versionValue).longValue()) {
                throw new MedicineRemoteException(502, "INVALID_SERVER_RESPONSE",
                        "The cloud response could not be verified. Refresh before retrying.");
            }
            if (!sameUser(initiatingUid)) throw new MedicineRemoteException(401,
                    "SESSION_CHANGED", "The cloud request completed after a sign-in change.");
            long version = ((Number) versionValue).longValue();
            success(callback, new MutationResult(resultId, version,
                    result.optBoolean("changed", true), result.optBoolean("created", false)));
        } catch (MedicineRemoteException exception) {
            error(callback, exception);
        } catch (Exception exception) {
            // Timeout may occur AFTER the server commits. Do not auto-retry here.
            error(callback, new MedicineRemoteException(0, "UNKNOWN_SAVE_OUTCOME",
                    "Could not confirm cloud save. Refresh before retrying to avoid duplicates.",
                    exception));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }


    public void deactivateMedicine(String medicineId, long expectedSyncVersion,
                                   MutationCallback callback) {
        if (callback == null) throw new IllegalArgumentException("A callback is required.");
        if (medicineId == null || !medicineId.matches("[a-zA-Z0-9_-]{1,128}") ||
                expectedSyncVersion < 0) {
            error(callback, new MedicineRemoteException(400, "INVALID_MEDICINE",
                    "A valid medicine ID and version are required."));
            return;
        }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            error(callback, new MedicineRemoteException(401, "AUTHENTICATION_REQUIRED",
                    "Sign in to deactivate a shared medicine."));
            return;
        }
        final String uid = user.getUid();
        user.getIdToken(false).addOnCompleteListener(task -> {
            if (!sameUser(uid) || !task.isSuccessful() || task.getResult() == null ||
                    task.getResult().getToken() == null) {
                error(callback, new MedicineRemoteException(401, "AUTHENTICATION_REQUIRED",
                        "Your session changed or could not be verified."));
                return;
            }
            new Thread(() -> executeDeactivation(medicineId, expectedSyncVersion,
                    uid, task.getResult().getToken(), callback),
                    "medicine-deactivation").start();
        });
    }

    private void executeDeactivation(String medicineId, long expectedSyncVersion,
                                     String uid, String token, MutationCallback callback) {
        HttpURLConnection connection = null;
        try {
            if (!sameUser(uid)) throw new MedicineRemoteException(401,
                    "SESSION_CHANGED", "Your sign-in changed.");
            String path = "/api/admin/medicines/" +
                    URLEncoder.encode(medicineId, StandardCharsets.UTF_8.name()) +
                    "/deactivate";
            connection = (HttpURLConnection) new URL(backendUrl + path).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            JSONObject payload = new JSONObject();
            payload.put("expectedSyncVersion", expectedSyncVersion);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(bytes);
            }
            int status = connection.getResponseCode();
            String body = readResponse(status >= 200 && status < 300 ?
                    connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                JSONObject response = tryJson(body);
                throw new MedicineRemoteException(status,
                        response == null ? "MEDICINE_CLOUD_ERROR" :
                                response.optString("code", "MEDICINE_CLOUD_ERROR"),
                        response == null ? "Deactivation rejected (HTTP " + status + ")." :
                                response.optString("message", "Deactivation rejected."));
            }
            JSONObject response = new JSONObject(body);
            Object versionValue = response.opt("syncVersion");
            if (!medicineId.equals(response.optString("medicineId")) ||
                    !(versionValue instanceof Number) ||
                    ((Number) versionValue).longValue() <= 0 ||
                    ((Number) versionValue).doubleValue() !=
                            ((Number) versionValue).longValue()) {
                throw new MedicineRemoteException(502, "INVALID_SERVER_RESPONSE",
                        "Cloud deactivation response is unverified. Refresh before retrying.");
            }
            if (!sameUser(uid)) throw new MedicineRemoteException(401,
                    "SESSION_CHANGED", "Your sign-in changed after the cloud request.");
            success(callback, new MutationResult(medicineId,
                    ((Number) versionValue).longValue(),
                    response.optBoolean("changed", true), false));
        } catch (MedicineRemoteException exception) {
            error(callback, exception);
        } catch (Exception exception) {
            error(callback, new MedicineRemoteException(0, "UNKNOWN_SAVE_OUTCOME",
                    "Deactivation outcome is uncertain; refresh before retrying.", exception));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean sameUser(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    private static Object optional(String text) {
        return text == null ? JSONObject.NULL : text;
    }

    private static JSONObject tryJson(String text) {
        try { return new JSONObject(text); }
        catch (Exception ignored) { return null; }
    }

    private static String readResponse(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > MAX_RESPONSE_CHARS) {
                    throw new IOException("Response too large.");
                }
                result.append(buffer, 0, count);
            }
            return result.toString();
        }
    }

    private void success(MutationCallback callback, MutationResult result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private void error(MutationCallback callback, MedicineRemoteException exception) {
        mainHandler.post(() -> callback.onError(exception));
    }

    public interface MutationCallback {
        void onSuccess(MutationResult result);
        void onError(MedicineRemoteException error);
    }

    public static final class MutationResult {
        private final String medicineId;
        private final long syncVersion;
        private final boolean changed;
        private final boolean created;

        public MutationResult(String medicineId, long syncVersion,
                              boolean changed, boolean created) {
            this.medicineId = medicineId;
            this.syncVersion = syncVersion;
            this.changed = changed;
            this.created = created;
        }
        public String getMedicineId() { return medicineId; }
        public long getSyncVersion() { return syncVersion; }
        public boolean isChanged() { return changed; }
        public boolean isCreated() { return created; }
    }

    public static final class MedicineRemoteException extends Exception {
        private final int statusCode;
        private final String code;

        public MedicineRemoteException(int statusCode, String code, String message) {
            super(message);
            this.statusCode = statusCode;
            this.code = code;
        }

        public MedicineRemoteException(int statusCode, String code, String message, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.code = code;
        }
        public int getStatusCode() { return statusCode; }
        public String getCode() { return code; }
    }
}
