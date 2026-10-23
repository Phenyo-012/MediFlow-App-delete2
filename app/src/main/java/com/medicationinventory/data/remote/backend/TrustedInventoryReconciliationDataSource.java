package com.medicationinventory.data.remote.backend;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.medicationinventory.model.dto.InventoryReconciliationException;
import com.medicationinventory.model.dto.InventoryReconciliationResult;
import com.medicationinventory.model.enums.InventoryOperationStatus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;


/**
 * Reads the trusted processor's terminal result for a previously submitted
 * operation. This source is used when the local sync queue needs to reconcile an
 * uncertain submission; it returns backend status and transaction details to
 * the repository rather than applying inventory changes itself.
 */
public class TrustedInventoryReconciliationDataSource {

    private static final String TAG =
            "InventoryReconcileDS";

    private static final int CONNECT_TIMEOUT_MS =
            15_000;

    private static final int READ_TIMEOUT_MS =
            30_000;


    private final FirebaseAuth firebaseAuth;

    private final String reconciliationUrl;

    private final ExecutorService networkExecutor;


    public TrustedInventoryReconciliationDataSource(
            FirebaseAuth firebaseAuth,
            String backendBaseUrl
    ) {

        this.firebaseAuth =
                firebaseAuth;

        this.reconciliationUrl =
                normalizeBaseUrl(
                        backendBaseUrl
                )
                        + "/api/reconcile-inventory-operation";

        this.networkExecutor =
                Executors.newSingleThreadExecutor();


        Log.d(
                TAG,
                "Reconciliation data source initialized."
        );

        Log.d(
                TAG,
                "Reconciliation URL="
                        + reconciliationUrl
        );
    }


    /**
     * Looks up the trusted processor's result for an operation ID using the
     * current Firebase session. A missing session or blank ID is reported before
     * network work begins; the returned terminal status is for local queue
     * reconciliation and is not applied as a new stock mutation here.
     *
     * @param operationId stable ID assigned to the submitted operation
     * @param callback receives the reconciliation result or a typed failure
     */
    public void fetchReconciliation(
            String operationId,
            Callback callback
    ) {

        if (operationId == null
                || operationId
                .trim()
                .isEmpty()) {

            callback.onError(
                    new InventoryReconciliationException(
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
                    new InventoryReconciliationException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "No authenticated Firebase session is available.",
                            false
                    )
            );

            return;
        }


        obtainTokenAndFetch(
                user,
                operationId.trim(),
                false,
                false,
                callback
        );
    }

    private void obtainTokenAndFetch(
            FirebaseUser user,
            String operationId,
            boolean forceRefresh,
            boolean alreadyRetriedAuthentication,
            Callback callback
    ) {

        user.getIdToken(
                        forceRefresh
                )
                .addOnSuccessListener(
                        tokenResult -> {

                            String idToken =
                                    extractIdToken(
                                            tokenResult
                                    );


                            if (idToken == null) {

                                callback.onError(
                                        new InventoryReconciliationException(
                                                401,
                                                "TOKEN_UNAVAILABLE",
                                                "Firebase authentication token is unavailable.",
                                                false
                                        )
                                );

                                return;
                            }


                            networkExecutor.execute(
                                    () ->
                                            invokeReconciliationEndpoint(
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
                        exception ->
                                callback.onError(
                                        new InventoryReconciliationException(
                                                "TOKEN_REQUEST_FAILED",
                                                "Unable to obtain an authentication token.",
                                                true,
                                                exception
                                        )
                                )
                );
    }


    private void invokeReconciliationEndpoint(
            FirebaseUser user,
            String operationId,
            String idToken,
            boolean alreadyRetriedAuthentication,
            Callback callback
    ) {

        HttpsURLConnection connection =
                null;


        try {

            URL url =
                    new URL(
                            reconciliationUrl
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
                    "Sending reconciliation request for "
                            + operationId
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


            int statusCode =
                    connection
                            .getResponseCode();


            Log.d(
                    TAG,
                    "Reconciliation HTTP status="
                            + statusCode
            );


            String responseBody =
                    readResponseBody(
                            connection,
                            statusCode
                    );


            if (statusCode == 401
                    && !alreadyRetriedAuthentication) {

                connection.disconnect();


                obtainTokenAndFetch(
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

                InventoryReconciliationResult result =
                        parseSuccessResponse(
                                operationId,
                                responseBody
                        );


                Log.d(
                        TAG,
                        "Reconciliation received. status="
                                + result.getStatus()
                                + ", batches="
                                + result.getBatches().size()
                                + ", transactions="
                                + result.getTransactions().size()
                );


                callback.onSuccess(
                        result
                );


                return;
            }


            callback.onError(
                    parseErrorResponse(
                            statusCode,
                            responseBody
                    )
            );


        } catch (IOException exception) {

            callback.onError(
                    new InventoryReconciliationException(
                            "NETWORK_TEMPORARY",
                            "Unable to contact the reconciliation service.",
                            true,
                            exception
                    )
            );


        } catch (Exception exception) {

            callback.onError(
                    new InventoryReconciliationException(
                            "RECONCILIATION_RESPONSE_INVALID",
                            "The reconciliation service returned an invalid response.",
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


    private InventoryReconciliationResult
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
                    "Reconciliation response returned a different operation ID."
            );
        }


        InventoryOperationStatus status;


        try {

            status =
                    InventoryOperationStatus.valueOf(
                            json.getString(
                                    "status"
                            )
                    );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Unsupported reconciliation status.",
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
                    "Reconciliation requires a terminal operation status."
            );
        }


        String resultCode =
                nullableString(
                        json,
                        "resultCode"
                );


        String resultMessage =
                nullableString(
                        json,
                        "resultMessage"
                );


        long serverCreatedAt =
                json.optLong(
                        "serverCreatedAt",
                        0L
                );


        long processedAt =
                json.optLong(
                        "processedAt",
                        0L
                );


        List<String> affectedBatchIds =
                parseAffectedBatchIds(
                        json.optJSONArray(
                                "affectedBatchIds"
                        )
                );


        List<InventoryReconciliationResult.AuthoritativeBatch>
                batches =
                parseBatches(
                        json.optJSONArray(
                                "batches"
                        )
                );


        List<InventoryReconciliationResult.AuthoritativeTransaction>
                transactions =
                parseTransactions(
                        json.optJSONArray(
                                "transactions"
                        )
                );


        return new InventoryReconciliationResult(
                operationId,
                status,
                resultCode,
                resultMessage,
                serverCreatedAt,
                processedAt,
                affectedBatchIds,
                batches,
                transactions
        );
    }


    private List<String>
    parseAffectedBatchIds(
            JSONArray array
    ) {

        List<String> result =
                new ArrayList<>();


        if (array == null) {
            return result;
        }


        for (int i = 0;
             i < array.length();
             i++) {

            String batchId =
                    array.optString(
                            i,
                            ""
                    );


            if (!batchId
                    .trim()
                    .isEmpty()) {

                result.add(
                        batchId.trim()
                );
            }
        }


        return result;
    }


    private List<InventoryReconciliationResult.AuthoritativeBatch>
    parseBatches(
            JSONArray array
    ) {

        List<InventoryReconciliationResult.AuthoritativeBatch>
                result =
                new ArrayList<>();


        if (array == null) {
            return result;
        }


        for (int i = 0;
             i < array.length();
             i++) {

            JSONObject json =
                    array.optJSONObject(
                            i
                    );


            if (json == null) {
                continue;
            }


            String batchId =
                    nullableString(
                            json,
                            "batchId"
                    );


            if (batchId == null
                    || batchId
                    .trim()
                    .isEmpty()) {

                continue;
            }


            result.add(
                    new InventoryReconciliationResult.AuthoritativeBatch(
                            batchId,
                            nullableString(
                                    json,
                                    "medicineId"
                            ),
                            nullableString(
                                    json,
                                    "batchNumber"
                            ),
                            json.optLong(
                                    "receivedDate",
                                    0L
                            ),
                            json.optLong(
                                    "expiryDate",
                                    0L
                            ),
                            json.optInt(
                                    "initialQuantity",
                                    0
                            ),
                            json.optInt(
                                    "quantityAvailable",
                                    0
                            ),
                            nullableString(
                                    json,
                                    "status"
                            ),
                            json.optLong(
                                    "updatedAt",
                                    0L
                            )
                    )
            );
        }


        return result;
    }


    private List<InventoryReconciliationResult.AuthoritativeTransaction>
    parseTransactions(
            JSONArray array
    ) {

        List<InventoryReconciliationResult.AuthoritativeTransaction>
                result =
                new ArrayList<>();


        if (array == null) {
            return result;
        }


        for (int i = 0;
             i < array.length();
             i++) {

            JSONObject json =
                    array.optJSONObject(
                            i
                    );


            if (json == null) {
                continue;
            }


            String transactionId =
                    nullableString(
                            json,
                            "transactionId"
                    );


            if (transactionId == null
                    || transactionId
                    .trim()
                    .isEmpty()) {

                continue;
            }


            result.add(
                    new InventoryReconciliationResult.AuthoritativeTransaction(
                            transactionId,
                            nullableString(
                                    json,
                                    "operationId"
                            ),
                            nullableString(
                                    json,
                                    "medicineId"
                            ),
                            nullableString(
                                    json,
                                    "batchId"
                            ),
                            nullableString(
                                    json,
                                    "transactionType"
                            ),
                            json.optInt(
                                    "quantityChange",
                                    0
                            ),
                            nullableString(
                                    json,
                                    "reason"
                            ),
                            nullableString(
                                    json,
                                    "performedBy"
                            ),
                            json.optLong(
                                    "clientCreatedAt",
                                    0L
                            ),
                            json.optLong(
                                    "serverCreatedAt",
                                    0L
                            )
                    )
            );
        }


        return result;
    }


    private InventoryReconciliationException
    parseErrorResponse(
            int statusCode,
            String responseBody
    ) {

        String errorCode =
                "RECONCILIATION_HTTP_"
                        + statusCode;


        String message =
                "Inventory reconciliation failed.";


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
        }


        boolean temporary =
                statusCode == 408
                        || statusCode == 429
                        || statusCode >= 500;


        return new InventoryReconciliationException(
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


    private String nullableString(
            JSONObject json,
            String key
    ) {

        if (!json.has(
                key
        )
                || json.isNull(
                key
        )) {

            return null;
        }


        String value =
                json.optString(
                        key,
                        null
                );


        if (value == null) {
            return null;
        }


        value =
                value.trim();


        return value.isEmpty()
                ? null
                : value;
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
                InventoryReconciliationResult result
        );


        void onError(
                InventoryReconciliationException exception
        );
    }
}