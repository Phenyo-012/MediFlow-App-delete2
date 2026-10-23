package com.medicationinventory.data.remote.backend;


import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;



/**
 * Loads and updates the shared inventory settings through the trusted admin API.
 * It attaches a Firebase ID token to requests and delivers results on the main
 * thread, while the backend remains responsible for permission checks and
 * validation of organization-wide settings.
 */
public class TrustedInventorySettingsDataSource
{
    private static final String TAG =
            "TrustedSettings";


    private static final String INVENTORY_SETTING_ID =
            "inventory";


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


    public TrustedInventorySettingsDataSource(
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
     * Sends the requested near-expiry warning threshold to the trusted settings
     * endpoint. Negative values are rejected locally for immediate feedback;
     * the server still validates the setting and determines whether this caller
     * may change it. Completion is delivered through the supplied callback.
     *
     * @param nearExpiryWarningDays number of days used by inventory warning rules
     * @param callback receives the persisted setting or a remote/validation error
     */
    public void setInventorySettings(
            int nearExpiryWarningDays,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Settings mutation callback is required."
            );
        }


        if (nearExpiryWarningDays < 0)
        {
            postError(
                    callback,
                    new SettingsRemoteException(
                            400,
                            "INVALID_NEAR_EXPIRY_WARNING_DAYS",
                            "Near-expiry warning days cannot be negative."
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
                    new SettingsRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "Authentication is required."
                    )
            );

            return;
        }


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
                                        new SettingsRemoteException(
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
                                            executeSetInventorySettings(
                                                    nearExpiryWarningDays,
                                                    token,
                                                    callback
                                            ),
                                    "inventory-settings-update"
                            ).start();
                        }
                );
    }


    private void executeSetInventorySettings(
            int nearExpiryWarningDays,
            String token,
            MutationCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/settings/inventory"
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
                    "nearExpiryWarningDays",
                    nearExpiryWarningDays
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
                SettingsRemoteException remoteException =
                        remoteError(
                                statusCode,
                                responseBody
                        );


                Log.e(
                        TAG,
                        "Settings update failed. HTTP "
                                + statusCode
                                + " code="
                                + remoteException.getCode()
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


            String settingId =
                    requireText(
                            result,
                            "settingId"
                    );


            if (!INVENTORY_SETTING_ID.equals(
                    settingId
            ))
            {
                throw new SettingsRemoteException(
                        0,
                        "INVALID_SERVER_RESPONSE",
                        "The server returned an invalid settings response."
                );
            }


            int resultWarningDays =
                    requireWarningDays(
                            result
                    );


            postSuccess(
                    callback,
                    new MutationResult(
                            changed,
                            created,
                            settingId,
                            resultWarningDays
                    )
            );
        }
        catch (SettingsRemoteException exception)
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
                    "Unexpected inventory settings update failure.",
                    exception
            );


            postError(
                    callback,
                    new SettingsRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to update inventory settings. "
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


    private int requireWarningDays(
            JSONObject object)
            throws SettingsRemoteException
    {
        if (object == null
                ||
                !object.has(
                        "nearExpiryWarningDays"
                )
                ||
                object.isNull(
                        "nearExpiryWarningDays"
                ))
        {
            throw invalidServerResponse();
        }


        Object value =
                object.opt(
                        "nearExpiryWarningDays"
                );


        if (!(value instanceof Number))
        {
            throw invalidServerResponse();
        }


        Number number =
                (Number) value;


        double doubleValue =
                number.doubleValue();


        long longValue =
                number.longValue();


        if (!Double.isFinite(
                doubleValue
        )
                ||
                doubleValue != longValue
                ||
                longValue < 0
                ||
                longValue > Integer.MAX_VALUE)
        {
            throw invalidServerResponse();
        }


        return (int) longValue;
    }


    private SettingsRemoteException invalidServerResponse()
    {
        return new SettingsRemoteException(
                0,
                "INVALID_SERVER_RESPONSE",
                "The server returned an invalid settings response."
        );
    }


    private SettingsRemoteException remoteError(
            int statusCode,
            String responseBody)
    {
        String code =
                "INVENTORY_SETTINGS_UPDATE_FAILED";


        String message =
                "Unable to update inventory settings at this time.";


        try
        {
            if (responseBody != null
                    &&
                    !responseBody.trim().isEmpty())
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


        return new SettingsRemoteException(
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
            throws SettingsRemoteException
    {
        String value =
                optionalText(
                        object,
                        key
                );


        if (value == null)
        {
            throw invalidServerResponse();
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
            SettingsRemoteException exception)
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
                SettingsRemoteException exception
        );
    }


    public static final class MutationResult
    {
        private final boolean changed;

        private final boolean created;

        private final String settingId;

        private final int nearExpiryWarningDays;


        public MutationResult(
                boolean changed,
                boolean created,
                String settingId,
                int nearExpiryWarningDays)
        {
            this.changed =
                    changed;


            this.created =
                    created;


            this.settingId =
                    settingId;


            this.nearExpiryWarningDays =
                    nearExpiryWarningDays;
        }


        public boolean isChanged()
        {
            return changed;
        }


        public boolean isCreated()
        {
            return created;
        }


        public String getSettingId()
        {
            return settingId;
        }


        public int getNearExpiryWarningDays()
        {
            return nearExpiryWarningDays;
        }
    }


    public static class SettingsRemoteException
            extends Exception
    {
        private final int statusCode;

        private final String code;


        public SettingsRemoteException(
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


        public SettingsRemoteException(
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