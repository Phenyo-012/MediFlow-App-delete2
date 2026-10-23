package com.medicationinventory.data.remote.backend;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.medicationinventory.model.dto.TrustedProcessorException;
import com.medicationinventory.model.dto.TrustedProcessorResult;
import com.medicationinventory.model.enums.InventoryOperationStatus;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;


/**
 * Submits locally queued inventory operations to the trusted processor endpoint.
 * The client sends the current Firebase ID token and stable operation ID; the
 * backend owns authorization and the authoritative stock transaction. HTTP
 * outcomes are mapped to the operation result or processor exception expected
 * by the sync repository, so a transport failure does not look like a committed
 * stock change.
 */
public class TrustedInventoryProcessorDataSource {

    private static final String TAG =
            "TrustedProcessorDS";

    private static final int CONNECT_TIMEOUT_MS =
            15_000;

    private static final int READ_TIMEOUT_MS =
            30_000;


    private final FirebaseAuth firebaseAuth;

    private final String processorUrl;

    private final ExecutorService networkExecutor;


    public TrustedInventoryProcessorDataSource(
            FirebaseAuth firebaseAuth,
            String backendBaseUrl
    ) {

        this.firebaseAuth =
                firebaseAuth;


        this.processorUrl =
                normalizeBaseUrl(
                        backendBaseUrl
                )
                        + "/api/process-inventory-operation";


        this.networkExecutor =
                Executors.newSingleThreadExecutor();


        Log.d(
                TAG,
                "Trusted processor data source initialized."
        );

        Log.d(
                TAG,
                "Processor URL="
                        + processorUrl
        );
    }


    /**
     * Requests trusted processing for a submitted operation using the current
     * Firebase ID token. Network and HTTP outcomes are delivered through callback.
     *
     * @param operationId stable ID used by the server for idempotent processing
     * @param callback receives accepted, terminal, or transport failure outcome
     */
    public void processOperation(
            String operationId,
            Callback callback
    ) {

        Log.d(
                TAG,
                "processOperation called for "
                        + operationId
        );


        if (operationId == null
                || operationId
                .trim()
                .isEmpty()) {

            Log.e(
                    TAG,
                    "Operation ID is missing."
            );


            callback.onError(
                    new TrustedProcessorException(
                            400,
                            "INVALID_OPERATION_ID",
                            "Operation ID is required.",
                            false
                    )
            );


            return;
        }


        FirebaseUser user =
                firebaseAuth
                        .getCurrentUser();


        Log.d(
                TAG,
                "Firebase user available="
                        + (user != null)
        );


        if (user == null) {

            callback.onError(
                    new TrustedProcessorException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "No authenticated Firebase session is available.",
                            false
                    )
            );


            return;
        }


        Log.d(
                TAG,
                "Requesting Firebase ID token."
        );


        obtainTokenAndInvoke(
                user,
                operationId.trim(),
                false,
                false,
                callback
        );
    }


    private void obtainTokenAndInvoke(
            FirebaseUser user,
            String operationId,
            boolean forceRefresh,
            boolean alreadyRetriedAuthentication,
            Callback callback
    ) {

        Log.d(
                TAG,
                "Obtaining token. forceRefresh="
                        + forceRefresh
        );


        user.getIdToken(
                        forceRefresh
                )
                .addOnSuccessListener(
                        tokenResult -> {

                            Log.d(
                                    TAG,
                                    "Firebase ID token obtained."
                            );


                            String idToken =
                                    extractIdToken(
                                            tokenResult
                                    );


                            if (idToken == null) {

                                Log.e(
                                        TAG,
                                        "Firebase ID token was empty."
                                );


                                callback.onError(
                                        new TrustedProcessorException(
                                                401,
                                                "TOKEN_UNAVAILABLE",
                                                "Firebase authentication token is unavailable.",
                                                false
                                        )
                                );


                                return;
                            }


                            Log.d(
                                    TAG,
                                    "Scheduling HTTPS request."
                            );


                            networkExecutor.execute(
                                    () ->
                                            invokeProcessor(
                                                    user,
                                                    operationId,
                                                    idToken,
                                                    alreadyRetriedAuthentication,
                                                    callback
                                            )
                            );
                        }
                )
                .addOnFailureListener(
                        exception -> {

                            Log.e(
                                    TAG,
                                    "Firebase token request failed.",
                                    exception
                            );


                            callback.onError(
                                    new TrustedProcessorException(
                                            "TOKEN_REQUEST_FAILED",
                                            "Unable to obtain an authentication token.",
                                            true,
                                            exception
                                    )
                            );
                        }
                );
    }


    private void invokeProcessor(
            FirebaseUser user,
            String operationId,
            String idToken,
            boolean alreadyRetriedAuthentication,
            Callback callback
    ) {

        HttpsURLConnection connection =
                null;


        try {

            Log.d(
                    TAG,
                    "Opening HTTPS connection."
            );


            URL url =
                    new URL(
                            processorUrl
                    );


            connection =
                    (HttpsURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "POST"
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
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + idToken
            );


            JSONObject requestBody =
                    new JSONObject();


            requestBody.put(
                    "operationId",
                    operationId
            );


            byte[] bodyBytes =
                    requestBody
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


            connection.setFixedLengthStreamingMode(
                    bodyBytes.length
            );


            Log.d(
                    TAG,
                    "Sending POST to trusted processor."
            );


            try (
                    OutputStream outputStream =
                            connection.getOutputStream()
            ) {

                outputStream.write(
                        bodyBytes
                );


                outputStream.flush();
            }


            Log.d(
                    TAG,
                    "POST body sent. Waiting for HTTP response."
            );


            int statusCode =
                    connection
                            .getResponseCode();


            Log.d(
                    TAG,
                    "Trusted processor HTTP status="
                            + statusCode
            );


            String responseBody =
                    readResponseBody(
                            connection,
                            statusCode
                    );



            if (statusCode == 401
                    && !alreadyRetriedAuthentication) {

                Log.w(
                        TAG,
                        "Processor returned 401. "
                                + "Refreshing Firebase ID token once."
                );


                connection.disconnect();


                obtainTokenAndInvoke(
                        user,
                        operationId,
                        true,
                        true,
                        callback
                );


                return;
            }


            if (statusCode >= 200
                    && statusCode < 300) {

                TrustedProcessorResult result =
                        parseSuccessResponse(
                                operationId,
                                responseBody
                        );


                Log.d(
                        TAG,
                        "Trusted processor success. status="
                                + result.getStatus()
                                + ", code="
                                + result.getResultCode()
                );


                callback.onSuccess(
                        result
                );


                return;
            }


            TrustedProcessorException parsedError =
                    parseErrorResponse(
                            statusCode,
                            responseBody
                    );


            Log.e(
                    TAG,
                    "Trusted processor rejected request. code="
                            + parsedError.getErrorCode()
                            + ", temporary="
                            + parsedError.isTemporary()
            );


            callback.onError(
                    parsedError
            );


        } catch (IOException exception) {

            Log.e(
                    TAG,
                    "Network error contacting trusted processor.",
                    exception
            );


            callback.onError(
                    new TrustedProcessorException(
                            "NETWORK_TEMPORARY",
                            "Unable to contact the trusted inventory processor.",
                            true,
                            exception
                    )
            );


        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Invalid trusted processor response.",
                    exception
            );


            callback.onError(
                    new TrustedProcessorException(
                            "PROCESSOR_RESPONSE_INVALID",
                            "The trusted processor returned an invalid response.",
                            false,
                            exception
                    )
            );


        } finally {

            if (connection != null) {

                connection.disconnect();
            }
        }
    }


    private TrustedProcessorResult
    parseSuccessResponse(
            String expectedOperationId,
            String responseBody
    ) throws Exception {

        JSONObject json =
                new JSONObject(
                        responseBody
                );


        String operationId =
                json.getString(
                        "operationId"
                );


        if (!expectedOperationId.equals(
                operationId
        )) {

            throw new IllegalStateException(
                    "Processor returned a different operation ID."
            );
        }


        String statusValue =
                json.getString(
                        "status"
                );


        InventoryOperationStatus status;


        try {

            status =
                    InventoryOperationStatus.valueOf(
                            statusValue
                    );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Processor returned an unsupported operation status.",
                    exception
            );
        }


        if (status
                != InventoryOperationStatus.CONFIRMED
                && status
                != InventoryOperationStatus.CONFLICT
                && status
                != InventoryOperationStatus.REJECTED) {

            throw new IllegalStateException(
                    "Processor did not return a terminal operation state."
            );
        }


        String resultCode =
                json.optString(
                        "resultCode",
                        null
                );


        String resultMessage =
                json.optString(
                        "resultMessage",
                        null
                );


        return new TrustedProcessorResult(
                operationId,
                status,
                resultCode,
                resultMessage
        );
    }


    private TrustedProcessorException
    parseErrorResponse(
            int statusCode,
            String responseBody
    ) {

        String errorCode =
                "PROCESSOR_HTTP_"
                        + statusCode;


        String message =
                "Trusted inventory processing failed.";


        try {

            if (responseBody != null
                    && !responseBody
                    .trim()
                    .isEmpty()) {

                JSONObject json =
                        new JSONObject(
                                responseBody
                        );


                errorCode =
                        json.optString(
                                "error",
                                errorCode
                        );


                message =
                        json.optString(
                                "message",
                                message
                        );
            }

        } catch (Exception ignored) {

            /*
             * Do not expose raw backend or HTML
             * responses to the application UI.
             */
        }


        boolean temporary =
                statusCode == 408
                        || statusCode == 429
                        || statusCode >= 500;


        return new TrustedProcessorException(
                statusCode,
                errorCode,
                message,
                temporary
        );
    }


    private String readResponseBody(
            HttpsURLConnection connection,
            int statusCode
    ) throws IOException {

        InputStream stream;


        if (statusCode >= 200
                && statusCode < 400) {

            stream =
                    connection
                            .getInputStream();

        } else {

            stream =
                    connection
                            .getErrorStream();
        }


        if (stream == null) {
            return "";
        }


        StringBuilder builder =
                new StringBuilder();


        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        stream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;


            while ((line =
                    reader.readLine())
                    != null) {

                builder.append(
                        line
                );
            }
        }


        return builder.toString();
    }


    private String extractIdToken(
            GetTokenResult tokenResult
    ) {

        if (tokenResult == null) {
            return null;
        }


        String token =
                tokenResult.getToken();


        if (token == null
                || token
                .trim()
                .isEmpty()) {

            return null;
        }


        return token;
    }


    private static String normalizeBaseUrl(
            String baseUrl
    ) {

        if (baseUrl == null
                || baseUrl
                .trim()
                .isEmpty()) {

            throw new IllegalArgumentException(
                    "Trusted backend URL is required."
            );
        }


        String result =
                baseUrl.trim();


        while (result.endsWith(
                "/"
        )) {

            result =
                    result.substring(
                            0,
                            result.length() - 1
                    );
        }


        if (!result.startsWith(
                "https://"
        )) {

            throw new IllegalArgumentException(
                    "Trusted backend must use HTTPS."
            );
        }


        return result;
    }


    public interface Callback {

        void onSuccess(
                TrustedProcessorResult result
        );


        void onError(
                TrustedProcessorException exception
        );
    }
}