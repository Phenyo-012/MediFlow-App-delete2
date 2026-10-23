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

import android.util.Log;
import java.util.Locale;



/**
 * Writes ABC and VED classifications to the shared medicine-classification
 * resource through the trusted backend. The client authenticates the request
 * with the current Firebase ID token and maps the server response for callers;
 * role and permission decisions are made by the backend.
 */
public class TrustedMedicineClassificationDataSource
{
    private static final String TAG =
            "TrustedAbcVed";

    private static final int CONNECT_TIMEOUT_MS =
            15_000;

    private static final int READ_TIMEOUT_MS =
            20_000;


    private final FirebaseAuth firebaseAuth;

    private final String backendUrl;


    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );



    public TrustedMedicineClassificationDataSource(
            FirebaseAuth firebaseAuth,
            String backendUrl)
    {
        if (firebaseAuth == null)
        {
            throw new IllegalArgumentException(
                    "Firebase Auth is required."
            );
        }


        if (backendUrl == null
                ||
                backendUrl.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Trusted backend URL is required."
            );
        }


        this.firebaseAuth =
                firebaseAuth;


        this.backendUrl =
                trimTrailingSlash(
                        backendUrl.trim()
                );
    }


    /**
     * Requests a shared ABC/VED classification update for one medicine. The
     * source validates required values before sending the authenticated request;
     * the trusted backend remains responsible for permission checks and the
     * authoritative stored classification.
     *
     * @param medicineId shared medicine identifier
     * @param abcClassification ABC category supplied by the caller
     * @param vedClassification VED category supplied by the caller
     * @param callback receives the server mutation result or failure
     */
    public void setClassification(
            String medicineId,
            String abcClassification,
            String vedClassification,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Classification mutation callback is required."
            );
        }


        if (!hasText(medicineId))
        {
            postError(
                    callback,
                    new ClassificationRemoteException(
                            400,
                            "MEDICINE_ID_REQUIRED",
                            "A medicine is required."
                    )
            );

            return;
        }


        if (!hasText(abcClassification))
        {
            postError(
                    callback,
                    new ClassificationRemoteException(
                            400,
                            "ABC_CLASSIFICATION_REQUIRED",
                            "An ABC classification is required."
                    )
            );

            return;
        }


        if (!hasText(vedClassification))
        {
            postError(
                    callback,
                    new ClassificationRemoteException(
                            400,
                            "VED_CLASSIFICATION_REQUIRED",
                            "A VED classification is required."
                    )
            );

            return;
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postError(
                    callback,
                    new ClassificationRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "Authentication is required."
                    )
            );

            return;
        }


        String cleanMedicineId =
                medicineId.trim();

        String cleanAbc =
                abcClassification
                        .trim()
                        .toUpperCase(Locale.ROOT);

        String cleanVed =
                vedClassification
                        .trim()
                        .toUpperCase(Locale.ROOT);


        currentUser
                .getIdToken(
                        false
                )
                .addOnCompleteListener(
                        task ->
                        {
                            if (!task.isSuccessful()
                                    ||
                                    task.getResult() == null
                                    ||
                                    task.getResult()
                                            .getToken() == null)
                            {
                                postError(
                                        callback,
                                        new ClassificationRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            String token =
                                    task.getResult()
                                            .getToken();


                            new Thread(
                                    () ->
                                            executeSetClassification(
                                                    cleanMedicineId,
                                                    cleanAbc,
                                                    cleanVed,
                                                    token,
                                                    callback
                                            ),
                                    "abc-ved-classification-update"
                            ).start();
                        }
                );
    }


    private void executeSetClassification(
            String medicineId,
            String abcClassification,
            String vedClassification,
            String token,
            MutationCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            String encodedMedicineId =
                    URLEncoder.encode(
                            medicineId,
                            StandardCharsets.UTF_8.name()
                    );


            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/medicines/"
                                    + encodedMedicineId
                                    + "/classification"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "PUT"
            );


            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS
            );


            connection.setReadTimeout(
                    READ_TIMEOUT_MS
            );


            connection.setDoOutput(
                    true
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + token
            );


            JSONObject requestBody =
                    new JSONObject();


            requestBody.put(
                    "abcClassification",
                    abcClassification
            );


            requestBody.put(
                    "vedClassification",
                    vedClassification
            );


            byte[] requestBytes =
                    requestBody
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


            connection.setFixedLengthStreamingMode(
                    requestBytes.length
            );


            try (
                    OutputStream outputStream =
                            connection
                                    .getOutputStream()
            )
            {
                outputStream.write(
                        requestBytes
                );

                outputStream.flush();
            }


            int statusCode =
                    connection
                            .getResponseCode();


            String responseBody =
                    readBody(
                            statusCode >= 200
                                    &&
                                    statusCode < 300
                                    ? connection
                                    .getInputStream()
                                    : connection
                                    .getErrorStream()
                    );


            if (statusCode < 200
                    ||
                    statusCode >= 300)
            {
                Log.e(
                        TAG,
                        "ABC-VED update failed. "
                                + "HTTP "
                                + statusCode
                                + " body="
                                + responseBody
                );


                ClassificationRemoteException remoteException =
                        remoteError(
                                statusCode,
                                responseBody
                        );


                Log.e(
                        TAG,
                        "ABC-VED error code="
                                + remoteException.getCode()
                                + " message="
                                + remoteException.getMessage()
                );


                postError(
                        callback,
                        remoteException
                );

                return;
            }


            JSONObject result =
                    new JSONObject(
                            responseBody
                    );


            boolean changed =
                    result.optBoolean(
                            "changed",
                            false
                    );


            boolean created =
                    result.optBoolean(
                            "created",
                            false
                    );


            String resultMedicineId =
                    requireText(
                            result,
                            "medicineId"
                    );


            String resultAbc =
                    requireText(
                            result,
                            "abcClassification"
                    );


            String resultVed =
                    requireText(
                            result,
                            "vedClassification"
                    );


            postSuccess(
                    callback,
                    new MutationResult(
                            changed,
                            created,
                            resultMedicineId,
                            resultAbc,
                            resultVed
                    )
            );
        }
        catch (ClassificationRemoteException exception)
        {
            postError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            Log.e(
                    TAG,
                    "Unexpected ABC-VED update failure.",
                    exception
            );


            postError(
                    callback,
                    new ClassificationRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to update the ABC-VED classification. "
                                    + "Check your connection and try again.",
                            exception
                    )
            );
        }
        finally
        {
            if (connection != null)
            {
                connection.disconnect();
            }
        }
    }


    private ClassificationRemoteException remoteError(
            int statusCode,
            String responseBody)
    {
        String code =
                "ABC_VED_UPDATE_FAILED";


        String message =
                "Unable to update the ABC-VED classification at this time.";


        try
        {
            if (responseBody != null
                    &&
                    !responseBody
                            .trim()
                            .isEmpty())
            {
                JSONObject error =
                        new JSONObject(
                                responseBody
                        );


                String responseCode =
                        optionalText(
                                error,
                                "code"
                        );


                String responseMessage =
                        optionalText(
                                error,
                                "message"
                        );


                if (responseCode != null)
                {
                    code =
                            responseCode;
                }


                if (responseMessage != null)
                {
                    message =
                            responseMessage;
                }
            }
        }
        catch (Exception ignored)
        {
            /*
             * Preserve safe fallback values.
             */
        }


        return new ClassificationRemoteException(
                statusCode,
                code,
                message
        );
    }


    private String readBody(
            InputStream inputStream)
            throws Exception
    {
        if (inputStream == null)
        {
            return "";
        }


        StringBuilder builder =
                new StringBuilder();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        )
        {
            String line;


            while (
                    (
                            line =
                                    reader.readLine()
                    )
                            != null
            )
            {
                builder.append(
                        line
                );
            }
        }


        return builder.toString();
    }


    private String requireText(
            JSONObject object,
            String key)
            throws ClassificationRemoteException
    {
        String value =
                optionalText(
                        object,
                        key
                );


        if (value == null)
        {
            throw new ClassificationRemoteException(
                    0,
                    "INVALID_SERVER_RESPONSE",
                    "The server returned an invalid classification response."
            );
        }


        return value;
    }


    private String optionalText(
            JSONObject object,
            String key)
    {
        if (object == null
                ||
                key == null
                ||
                object.isNull(
                        key
                ))
        {
            return null;
        }


        String value =
                object.optString(
                        key,
                        null
                );


        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim();


        return normalized.isEmpty()
                ? null
                : normalized;
    }


    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim()
                        .isEmpty();
    }


    private String trimTrailingSlash(
            String value)
    {
        String result =
                value;


        while (result.endsWith(
                "/"
        ))
        {
            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }


        return result;
    }


    private void postSuccess(
            MutationCallback callback,
            MutationResult result)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                result
                        )
        );
    }


    private void postError(
            MutationCallback callback,
            ClassificationRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }


    public interface MutationCallback
    {
        void onSuccess(
                MutationResult result
        );


        void onError(
                ClassificationRemoteException exception
        );
    }


    public static final class MutationResult
    {
        private final boolean changed;

        private final boolean created;

        private final String medicineId;

        private final String abcClassification;

        private final String vedClassification;


        public MutationResult(
                boolean changed,
                boolean created,
                String medicineId,
                String abcClassification,
                String vedClassification)
        {
            this.changed =
                    changed;

            this.created =
                    created;

            this.medicineId =
                    medicineId;

            this.abcClassification =
                    abcClassification;

            this.vedClassification =
                    vedClassification;
        }


        public boolean isChanged()
        {
            return changed;
        }


        public boolean isCreated()
        {
            return created;
        }


        public String getMedicineId()
        {
            return medicineId;
        }


        public String getAbcClassification()
        {
            return abcClassification;
        }


        public String getVedClassification()
        {
            return vedClassification;
        }
    }


    public static class ClassificationRemoteException
            extends Exception
    {
        private final int statusCode;

        private final String code;


        public ClassificationRemoteException(
                int statusCode,
                String code,
                String message)
        {
            super(
                    message
            );


            this.statusCode =
                    statusCode;

            this.code =
                    code;
        }

        public ClassificationRemoteException(
                int statusCode,
                String code,
                String message,
                Throwable cause)
        {
            super(
                    message,
                    cause
            );


            this.statusCode =
                    statusCode;

            this.code =
                    code;
        }


        public int getStatusCode()
        {
            return statusCode;
        }


        public String getCode()
        {
            return code;
        }
    }
}