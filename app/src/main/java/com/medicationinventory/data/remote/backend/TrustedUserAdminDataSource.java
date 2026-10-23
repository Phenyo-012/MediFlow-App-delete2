package com.medicationinventory.data.remote.backend;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.medicationinventory.model.dto.AdminUserSummary;
import com.medicationinventory.model.dto.AdminUserDetail;
import com.medicationinventory.model.dto.AdminRoleSummary;
import com.medicationinventory.model.dto.AdminPermissionSummary;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;


/**
 * Provides the Android client with the trusted backend's user-administration
 * operations: listing users and access metadata, creating accounts, and changing
 * account state, role, or permissions. Firebase tokens authenticate requests;
 * backend authorization is the security boundary, and parsed results are
 * returned through callbacks on the main thread.
 */
public class TrustedUserAdminDataSource
{
    private static final int CONNECT_TIMEOUT_MS =
            15_000;


    private static final int READ_TIMEOUT_MS =
            20_000;


    private final FirebaseAuth
            firebaseAuth;


    private final String
            backendUrl;


    private final Handler
            mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );


    public TrustedUserAdminDataSource(
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

    public void createUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String roleId,
            CreateUserCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Create user callback is required."
            );
        }


        if (!hasText(firstName)
                ||
                !hasText(lastName)
                ||
                !hasText(email)
                ||
                password == null
                ||
                password.length() < 6
                ||
                !hasText(roleId))
        {
            postCreateUserError(
                    callback,
                    new UserAdminRemoteException(
                            400,
                            "INVALID_USER_DATA",
                            "Valid user details are required."
                    )
            );

            return;
        }


        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();


        if (currentUser == null)
        {
            postCreateUserError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "Authentication is required."
                    )
            );

            return;
        }


        String cleanFirstName =
                firstName.trim();

        String cleanLastName =
                lastName.trim();

        String cleanEmail =
                email.trim();

        String cleanRoleId =
                roleId.trim();


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
                                postCreateUserError(
                                        callback,
                                        new UserAdminRemoteException(
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
                                            executeCreateUser(
                                                    cleanFirstName,
                                                    cleanLastName,
                                                    cleanEmail,
                                                    password,
                                                    cleanRoleId,
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-create-user"
                            ).start();
                        }
                );
    }

    public void loadUsers(
            Callback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User administration callback is required."
            );
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "You must sign in before loading users."
                    )
            );

            return;
        }


        currentUser
                .getIdToken(
                        false
                )
                .addOnSuccessListener(
                        tokenResult ->
                        {
                            String token =
                                    tokenResult
                                            .getToken();


                            if (token == null
                                    ||
                                    token.trim()
                                            .isEmpty())
                            {
                                postError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            new Thread(
                                    () ->
                                            executeLoadUsers(
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-list"
                            )
                                    .start();
                        }
                )
                .addOnFailureListener(
                        exception ->
                                postError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified.",
                                                exception
                                        )
                                )
                );
    }


    private void executeLoadUsers(
            String idToken,
            Callback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/users"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "GET"
            );


            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS
            );


            connection.setReadTimeout(
                    READ_TIMEOUT_MS
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer "
                            + idToken
            );


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
                throw remoteError(
                        statusCode,
                        responseBody
                );
            }


            List<AdminUserSummary> users =
                    parseUsers(
                            responseBody
                    );


            postSuccess(
                    callback,
                    users
            );
        }
        catch (UserAdminRemoteException exception)
        {
            postError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            postError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to load users. Check your connection and try again.",
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


    private List<AdminUserSummary> parseUsers(
            String responseBody)
            throws Exception
    {
        JSONObject root =
                new JSONObject(
                        responseBody
                );


        JSONArray userArray =
                root.getJSONArray(
                        "users"
                );


        List<AdminUserSummary> users =
                new ArrayList<>();


        for (int index = 0;
             index < userArray.length();
             index++)
        {
            JSONObject user =
                    userArray
                            .getJSONObject(
                                    index
                            );


            String uid =
                    requireText(
                            user,
                            "uid"
                    );


            String firstName =
                    optionalText(
                            user,
                            "firstName"
                    );


            String lastName =
                    optionalText(
                            user,
                            "lastName"
                    );


            String email =
                    optionalText(
                            user,
                            "email"
                    );


            String roleId =
                    optionalText(
                            user,
                            "roleId"
                    );


            boolean active =
                    user.optBoolean(
                            "isActive",
                            false
                    );


            long createdAt =
                    optionalLong(
                            user,
                            "createdAt"
                    );


            long updatedAt =
                    optionalLong(
                            user,
                            "updatedAt"
                    );


            users.add(
                    new AdminUserSummary(
                            uid,
                            firstName,
                            lastName,
                            email,
                            roleId,
                            active,
                            createdAt,
                            updatedAt
                    )
            );
        }


        return users;
    }


    private UserAdminRemoteException remoteError(
            int statusCode,
            String responseBody)
    {
        String code =
                "USER_LIST_FAILED";


        String message =
                "Unable to load users at this time.";


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
             * Keep the safe fallback message.
             * Do not expose raw HTML or server traces.
             */
        }


        return new UserAdminRemoteException(
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
            throws Exception
    {
        String value =
                optionalText(
                        object,
                        key
                );


        if (value == null)
        {
            throw new IllegalStateException(
                    "User response is missing "
                            + key
                            + "."
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


    private long optionalLong(
            JSONObject object,
            String key)
    {
        if (object == null
                ||
                object.isNull(
                        key
                ))
        {
            return 0L;
        }


        return object.optLong(
                key,
                0L
        );
    }


    private void postSuccess(
            Callback callback,
            List<AdminUserSummary> users)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                users
                        )
        );
    }


    private void postError(
            Callback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
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


    public interface Callback
    {
        void onSuccess(
                List<AdminUserSummary> users
        );


        void onError(
                UserAdminRemoteException exception
        );
    }

    public static class UserAdminRemoteException
            extends Exception
    {
        private final int
                statusCode;


        private final String
                code;


        public UserAdminRemoteException(
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


        public UserAdminRemoteException(
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

    public void loadUserDetail(
            String uid,
            DetailCallback callback)
    {
        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User detail callback is required."
            );
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postDetailError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "You must sign in before loading this user."
                    )
            );

            return;
        }


        String targetUid =
                uid.trim();


        currentUser
                .getIdToken(
                        false
                )
                .addOnSuccessListener(
                        tokenResult ->
                        {
                            String token =
                                    tokenResult
                                            .getToken();


                            if (token == null
                                    ||
                                    token.trim()
                                            .isEmpty())
                            {
                                postDetailError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            new Thread(
                                    () ->
                                            executeLoadUserDetail(
                                                    targetUid,
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-detail"
                            )
                                    .start();
                        }
                )
                .addOnFailureListener(
                        exception ->
                                postDetailError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified.",
                                                exception
                                        )
                                )
                );
    }

    private void executeLoadUserDetail(
            String uid,
            String idToken,
            DetailCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            String encodedUid =
                    URLEncoder.encode(
                            uid,
                            StandardCharsets.UTF_8.name()
                    );


            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/users/"
                                    + encodedUid
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "GET"
            );


            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS
            );


            connection.setReadTimeout(
                    READ_TIMEOUT_MS
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer "
                            + idToken
            );


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
                throw remoteError(
                        statusCode,
                        responseBody
                );
            }


            AdminUserDetail detail =
                    parseUserDetail(
                            responseBody
                    );


            postDetailSuccess(
                    callback,
                    detail
            );
        }
        catch (UserAdminRemoteException exception)
        {
            postDetailError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            postDetailError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to load this user. Check your connection and try again.",
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


    private AdminUserDetail parseUserDetail(
            String responseBody)
            throws Exception
    {
        JSONObject user =
                new JSONObject(
                        responseBody
                );


        String uid =
                requireText(
                        user,
                        "uid"
                );


        String firstName =
                optionalText(
                        user,
                        "firstName"
                );


        String lastName =
                optionalText(
                        user,
                        "lastName"
                );


        String email =
                optionalText(
                        user,
                        "email"
                );


        String roleId =
                optionalText(
                        user,
                        "roleId"
                );


        List<String> permissions =
                new ArrayList<>();


        JSONArray permissionArray =
                user.optJSONArray(
                        "permissions"
                );


        if (permissionArray != null)
        {
            for (int index = 0;
                 index < permissionArray.length();
                 index++)
            {
                String permission =
                        permissionArray
                                .optString(
                                        index,
                                        null
                                );


                if (permission != null
                        &&
                        !permission.trim()
                                .isEmpty())
                {
                    permissions.add(
                            permission.trim()
                    );
                }
            }
        }


        return new AdminUserDetail(
                uid,
                firstName,
                lastName,
                email,
                roleId,
                permissions,
                user.optBoolean(
                        "isActive",
                        false
                ),
                user.optBoolean(
                        "isSelf",
                        false
                ),
                optionalLong(
                        user,
                        "createdAt"
                ),
                optionalLong(
                        user,
                        "updatedAt"
                )
        );
    }

    private void postDetailSuccess(
            DetailCallback callback,
            AdminUserDetail detail)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                detail
                        )
        );
    }


    private void postDetailError(
            DetailCallback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }

    public void setUserActiveState(
            String uid,
            boolean active,
            MutationCallback callback)
    {
        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "User mutation callback is required."
            );
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "You must sign in before changing this account."
                    )
            );

            return;
        }


        String targetUid =
                uid.trim();


        currentUser
                .getIdToken(
                        false
                )
                .addOnSuccessListener(
                        tokenResult ->
                        {
                            String token =
                                    tokenResult
                                            .getToken();


                            if (token == null
                                    ||
                                    token.trim().isEmpty())
                            {
                                postMutationError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            new Thread(
                                    () ->
                                            executeSetUserActiveState(
                                                    targetUid,
                                                    active,
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-status"
                            )
                                    .start();
                        }
                )
                .addOnFailureListener(
                        exception ->
                                postMutationError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified.",
                                                exception
                                        )
                                )
                );
    }

    public void setUserRole(
            String uid,
            String roleId,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Mutation callback is required."
            );
        }


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            400,
                            "USER_ID_REQUIRED",
                            "A target user is required."
                    )
            );

            return;
        }


        if (roleId == null
                ||
                roleId.trim().isEmpty())
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            400,
                            "ROLE_ID_REQUIRED",
                            "A role must be selected."
                    )
            );

            return;
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "You must sign in before changing a role."
                    )
            );

            return;
        }


        String cleanUid =
                uid.trim();


        String cleanRoleId =
                roleId.trim();


        currentUser
                .getIdToken(
                        false
                )
                .addOnSuccessListener(
                        tokenResult ->
                        {
                            String token =
                                    tokenResult
                                            .getToken();


                            if (token == null
                                    ||
                                    token.trim().isEmpty())
                            {
                                postMutationError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            new Thread(
                                    () ->
                                            executeSetUserRole(
                                                    cleanUid,
                                                    cleanRoleId,
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-role-change"
                            )
                                    .start();
                        }
                )
                .addOnFailureListener(
                        exception ->
                                postMutationError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified.",
                                                exception
                                        )
                                )
                );
    }

    public void setUserPermission(
            String uid,
            String permissionId,
            boolean granted,
            MutationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Mutation callback is required."
            );
        }


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            400,
                            "USER_ID_REQUIRED",
                            "A target user is required."
                    )
            );

            return;
        }


        if (permissionId == null
                ||
                permissionId.trim().isEmpty())
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            400,
                            "PERMISSION_ID_REQUIRED",
                            "A permission must be selected."
                    )
            );

            return;
        }


        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();


        if (currentUser == null)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "Authentication is required."
                    )
            );

            return;
        }


        String cleanUid =
                uid.trim();

        String cleanPermissionId =
                permissionId.trim();


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
                                postMutationError(
                                        callback,
                                        new UserAdminRemoteException(
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
                                            executeSetUserPermission(
                                                    cleanUid,
                                                    cleanPermissionId,
                                                    granted,
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-permission-change"
                            ).start();
                        }
                );
    }

    private String readBody(
            HttpURLConnection connection,
            int statusCode)
            throws IOException
    {
        InputStream inputStream;

        if (statusCode >= 200
                &&
                statusCode < 400)
        {
            inputStream =
                    connection.getInputStream();
        }
        else
        {
            inputStream =
                    connection.getErrorStream();
        }


        if (inputStream == null)
        {
            return "";
        }


        StringBuilder body =
                new StringBuilder();


        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     inputStream,
                                     StandardCharsets.UTF_8
                             )
                     ))
        {
            String line;


            while ((line =
                    reader.readLine()) != null)
            {
                body.append(
                        line
                );
            }
        }


        return body.toString();
    }

    private void executeSetUserPermission(
            String uid,
            String permissionId,
            boolean granted,
            String token,
            MutationCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            String encodedUid =
                    URLEncoder.encode(
                            uid,
                            StandardCharsets.UTF_8.name()
                    );


            URL url =
                    new URL(
                            trimTrailingSlash(
                                    backendUrl
                            )
                                    + "/api/admin/users/"
                                    + encodedUid
                                    + "/permissions"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "PUT"
            );

            connection.setConnectTimeout(
                    10000
            );

            connection.setReadTimeout(
                    10000
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
                    "permissionId",
                    permissionId
            );

            requestBody.put(
                    "granted",
                    granted
            );


            byte[] requestBytes =
                    requestBody
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


            try (OutputStream outputStream =
                         connection.getOutputStream())
            {
                outputStream.write(
                        requestBytes
                );

                outputStream.flush();
            }


            int statusCode =
                    connection.getResponseCode();


            String body =
                    readBody(
                            connection,
                            statusCode
                    );


            if (statusCode < 200
                    ||
                    statusCode >= 300)
            {
                postMutationError(
                        callback,
                        remoteError(
                                statusCode,
                                body
                        )
                );

                return;
            }


            JSONObject result =
                    new JSONObject(
                            body
                    );


            boolean changed =
                    result.optBoolean(
                            "changed",
                            false
                    );


            postMutationSuccess(
                    callback,
                    changed
            );
        }
        catch (Exception exception)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to connect to the server."
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

    private void executeSetUserRole(
            String uid,
            String roleId,
            String idToken,
            MutationCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            String encodedUid =
                    URLEncoder.encode(
                            uid,
                            StandardCharsets.UTF_8.name()
                    );


            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/users/"
                                    + encodedUid
                                    + "/role"
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
                    "Bearer "
                            + idToken
            );


            JSONObject body =
                    new JSONObject();


            body.put(
                    "roleId",
                    roleId
            );


            byte[] requestBytes =
                    body
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


            connection.setFixedLengthStreamingMode(
                    requestBytes.length
            );


            try (OutputStream outputStream =
                         connection.getOutputStream())
            {
                outputStream.write(
                        requestBytes
                );
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
                throw remoteError(
                        statusCode,
                        responseBody
                );
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


            postMutationSuccess(
                    callback,
                    changed
            );
        }
        catch (UserAdminRemoteException exception)
        {
            postMutationError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to change the user's role. Check your connection and try again.",
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

    private void executeSetUserActiveState(
            String uid,
            boolean active,
            String idToken,
            MutationCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            String encodedUid =
                    URLEncoder.encode(
                            uid,
                            StandardCharsets.UTF_8.name()
                    );


            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/users/"
                                    + encodedUid
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
                    "application/json"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer "
                            + idToken
            );


            JSONObject requestBody =
                    new JSONObject();


            requestBody.put(
                    "isActive",
                    active
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


            try (
                    OutputStream outputStream =
                            connection
                                    .getOutputStream()
            )
            {
                outputStream.write(
                        bodyBytes
                );
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
                throw remoteError(
                        statusCode,
                        responseBody
                );
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


            postMutationSuccess(
                    callback,
                    changed
            );
        }
        catch (UserAdminRemoteException exception)
        {
            postMutationError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            postMutationError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to update this account. Check your connection and try again.",
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


    private void postMutationSuccess(
            MutationCallback callback,
            boolean changed)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                changed
                        )
        );
    }


    private void postMutationError(
            MutationCallback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }

    public void loadRoles(
            RoleCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Role callback is required."
            );
        }


        FirebaseUser currentUser =
                firebaseAuth
                        .getCurrentUser();


        if (currentUser == null)
        {
            postRoleError(
                    callback,
                    new UserAdminRemoteException(
                            401,
                            "AUTHENTICATION_REQUIRED",
                            "You must sign in before loading roles."
                    )
            );

            return;
        }


        currentUser
                .getIdToken(
                        false
                )
                .addOnSuccessListener(
                        tokenResult ->
                        {
                            String token =
                                    tokenResult
                                            .getToken();


                            if (token == null
                                    ||
                                    token.trim().isEmpty())
                            {
                                postRoleError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified."
                                        )
                                );

                                return;
                            }


                            new Thread(
                                    () ->
                                            executeLoadRoles(
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-roles"
                            )
                                    .start();
                        }
                )
                .addOnFailureListener(
                        exception ->
                                postRoleError(
                                        callback,
                                        new UserAdminRemoteException(
                                                401,
                                                "AUTHENTICATION_REQUIRED",
                                                "Authentication could not be verified.",
                                                exception
                                        )
                                )
                );
    }

    private void executeLoadRoles(
            String idToken,
            RoleCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            URL url =
                    new URL(
                            backendUrl
                                    + "/api/admin/roles"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "GET"
            );


            connection.setConnectTimeout(
                    CONNECT_TIMEOUT_MS
            );


            connection.setReadTimeout(
                    READ_TIMEOUT_MS
            );


            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );


            connection.setRequestProperty(
                    "Authorization",
                    "Bearer "
                            + idToken
            );


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
                throw remoteError(
                        statusCode,
                        responseBody
                );
            }


            List<AdminRoleSummary> roles =
                    parseRoles(
                            responseBody
                    );


            postRoleSuccess(
                    callback,
                    roles
            );
        }
        catch (UserAdminRemoteException exception)
        {
            postRoleError(
                    callback,
                    exception
            );
        }
        catch (Exception exception)
        {
            postRoleError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to load roles. Check your connection and try again.",
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

    private List<AdminRoleSummary> parseRoles(
            String responseBody)
            throws Exception
    {
        JSONObject root =
                new JSONObject(
                        responseBody
                );


        JSONArray roleArray =
                root.getJSONArray(
                        "roles"
                );


        List<AdminRoleSummary> roles =
                new ArrayList<>();


        for (int index = 0;
             index < roleArray.length();
             index++)
        {
            JSONObject role =
                    roleArray
                            .getJSONObject(
                                    index
                            );


            String roleId =
                    requireText(
                            role,
                            "roleId"
                    );


            String roleName =
                    requireText(
                            role,
                            "roleName"
                    );


            List<String> baselinePermissions =
                    new ArrayList<>();


            JSONArray permissionArray =
                    role.optJSONArray(
                            "baselinePermissions"
                    );


            if (permissionArray != null)
            {
                for (int permissionIndex = 0;
                     permissionIndex < permissionArray.length();
                     permissionIndex++)
                {
                    String permission =
                            permissionArray
                                    .optString(
                                            permissionIndex,
                                            null
                                    );


                    if (permission != null
                            &&
                            !permission.trim().isEmpty())
                    {
                        baselinePermissions.add(
                                permission.trim()
                        );
                    }
                }
            }


            roles.add(
                    new AdminRoleSummary(
                            roleId,
                            roleName,
                            baselinePermissions
                    )
            );
        }


        return roles;
    }


    private void postRoleSuccess(
            RoleCallback callback,
            List<AdminRoleSummary> roles)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                roles
                        )
        );
    }



    private void postRoleError(
            RoleCallback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }


    public void loadPermissions(
            PermissionCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Permission callback is required."
            );
        }


        FirebaseUser currentUser =
                firebaseAuth.getCurrentUser();


        if (currentUser == null)
        {
            postPermissionError(
                    callback,
                    new UserAdminRemoteException(
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
                                postPermissionError(
                                        callback,
                                        new UserAdminRemoteException(
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
                                            executeLoadPermissions(
                                                    token,
                                                    callback
                                            ),
                                    "user-admin-permission-list"
                            ).start();
                        }
                );
    }


    private void executeLoadPermissions(
            String token,
            PermissionCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            URL url =
                    new URL(
                            trimTrailingSlash(
                                    backendUrl
                            )
                                    + "/api/admin/permissions"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "GET"
            );

            connection.setConnectTimeout(
                    10000
            );

            connection.setReadTimeout(
                    10000
            );

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + token
            );


            int statusCode =
                    connection.getResponseCode();


            String body =
                    readBody(
                            connection,
                            statusCode
                    );


            if (statusCode < 200
                    ||
                    statusCode >= 300)
            {
                postPermissionError(
                        callback,
                        remoteError(
                                statusCode,
                                body
                        )
                );

                return;
            }


            JSONObject root =
                    new JSONObject(
                            body
                    );


            List<AdminPermissionSummary> permissions =
                    parsePermissions(
                            root
                    );


            postPermissionSuccess(
                    callback,
                    permissions
            );
        }
        catch (Exception exception)
        {
            postPermissionError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to connect to the server."
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

    private List<AdminPermissionSummary>
    parsePermissions(
            JSONObject root)
            throws Exception
    {
        JSONArray array =
                root.getJSONArray(
                        "permissions"
                );


        List<AdminPermissionSummary> permissions =
                new ArrayList<>();


        for (int index = 0;
             index < array.length();
             index++)
        {
            JSONObject item =
                    array.getJSONObject(
                            index
                    );


            String permissionId =
                    requireText(
                            item,
                            "permissionId"
                    );


            String displayName =
                    requireText(
                            item,
                            "displayName"
                    );


            permissions.add(
                    new AdminPermissionSummary(
                            permissionId,
                            displayName
                    )
            );
        }


        return permissions;
    }


    private void postPermissionSuccess(
            PermissionCallback callback,
            List<AdminPermissionSummary> permissions)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                permissions
                        )
        );
    }


    private void postPermissionError(
            PermissionCallback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }


    private void executeCreateUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String roleId,
            String token,
            CreateUserCallback callback)
    {
        HttpURLConnection connection =
                null;


        try
        {
            URL url =
                    new URL(
                            trimTrailingSlash(
                                    backendUrl
                            )
                                    + "/api/admin/users/create"
                    );


            connection =
                    (HttpURLConnection)
                            url.openConnection();


            connection.setRequestMethod(
                    "POST"
            );

            connection.setConnectTimeout(
                    10000
            );

            connection.setReadTimeout(
                    10000
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
                    "firstName",
                    firstName
            );

            requestBody.put(
                    "lastName",
                    lastName
            );

            requestBody.put(
                    "email",
                    email
            );

            requestBody.put(
                    "password",
                    password
            );

            requestBody.put(
                    "roleId",
                    roleId
            );


            byte[] requestBytes =
                    requestBody
                            .toString()
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


            try (OutputStream outputStream =
                         connection.getOutputStream())
            {
                outputStream.write(
                        requestBytes
                );

                outputStream.flush();
            }


            int statusCode =
                    connection.getResponseCode();


            String body =
                    readBody(
                            connection,
                            statusCode
                    );


            if (statusCode < 200
                    ||
                    statusCode >= 300)
            {
                postCreateUserError(
                        callback,
                        remoteError(
                                statusCode,
                                body
                        )
                );

                return;
            }


            JSONObject root =
                    new JSONObject(
                            body
                    );


            JSONObject createdUser =
                    root.getJSONObject(
                            "user"
                    );


            String uid =
                    requireText(
                            createdUser,
                            "uid"
                    );


            postCreateUserSuccess(
                    callback,
                    uid
            );
        }
        catch (Exception exception)
        {
            postCreateUserError(
                    callback,
                    new UserAdminRemoteException(
                            0,
                            "NETWORK_ERROR",
                            "Unable to connect to the server."
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


    private void postCreateUserSuccess(
            CreateUserCallback callback,
            String uid)
    {
        mainHandler.post(
                () ->
                        callback.onSuccess(
                                uid
                        )
        );
    }


    private void postCreateUserError(
            CreateUserCallback callback,
            UserAdminRemoteException exception)
    {
        mainHandler.post(
                () ->
                        callback.onError(
                                exception
                        )
        );
    }

    public interface CreateUserCallback
    {
        void onSuccess(
                String uid
        );


        void onError(
                UserAdminRemoteException exception
        );
    }


    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }

    public interface PermissionCallback
    {
        void onSuccess(
                List<AdminPermissionSummary> permissions
        );


        void onError(
                UserAdminRemoteException exception
        );
    }

    public interface DetailCallback
    {
        void onSuccess(
                AdminUserDetail user
        );


        void onError(
                UserAdminRemoteException exception
        );
    }

    public interface MutationCallback
    {
        void onSuccess(
                boolean changed
        );


        void onError(
                UserAdminRemoteException exception
        );
    }

    public interface RoleCallback
    {
        void onSuccess(
                List<AdminRoleSummary> roles
        );


        void onError(
                UserAdminRemoteException exception
        );
    }
}