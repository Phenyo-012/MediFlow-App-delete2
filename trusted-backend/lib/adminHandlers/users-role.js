/**
 * HTTP handler for the users-role administration endpoint. It translates the
 * request into a call to the trusted application service.
 *
 * Keep authentication, allowed methods and error responses explicit so a malformed
 * request does not accidentally reach a write operation.
 */
const {
  getAuth,
} = require("firebase-admin/auth");

const {
  getAdminFirestore,
} = require("../firebaseAdmin");

const {
  setApplicationUserRole,
} = require("../roleAdminService");

const {
  UserAdminHttpError,
} = require("../userAdminAuthorization");


module.exports = async function handler(
    request,
    response,
) {
  if (request.method !== "PUT") {
    response.setHeader(
        "Allow",
        "PUT",
    );


    return response
      .status(405)
      .json({
        code:
          "METHOD_NOT_ALLOWED",

        message:
          "Only PUT is supported.",
      });
  }


  try {
    const token =
      requireBearerToken(
          request,
      );


    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            token,
            true,
        );


    const targetUid =
      requireTargetUid(
          request,
      );


    const roleId =
      requireRoleId(
          request.body,
      );


    const result =
      await setApplicationUserRole(
          decodedToken.uid,
          targetUid,
          roleId,
      );


    return response
      .status(200)
      .json(
          result,
      );
  } catch (error) {
    return handleError(
        error,
        response,
    );
  }
};


/**
 * Applies require bearer token on the server. Signing in does not automatically
 * grant this permission.
 */
function requireBearerToken(
    request,
) {
  const authorization =
    request.headers
      .authorization;


  if (typeof authorization !==
      "string") {
    throw new ApiAuthenticationError(
        401,
        "AUTHENTICATION_REQUIRED",
        "Authentication is required.",
    );
  }


  const match =
    authorization.match(
        /^Bearer\s+(.+)$/i,
    );


  if (!match ||
      !match[1] ||
      match[1].trim() === "") {
    throw new ApiAuthenticationError(
        401,
        "INVALID_AUTHORIZATION_HEADER",
        "Authentication is required.",
    );
  }


  return match[1].trim();
}


/**
 * Contains the require target user ID step, keeping that decision separate from
 * the HTTP handler or test setup.
 */
function requireTargetUid(
    request,
) {
  const value =
    request.query &&
    request.query.uid;


  if (typeof value !== "string" ||
      value.trim() === "") {
    throw new UserAdminHttpError(
        400,
        "USER_ID_REQUIRED",
        "A target user is required.",
    );
  }


  return value.trim();
}


/**
 * Contains the require role ID step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function requireRoleId(
    body,
) {
  if (
    !body ||
    typeof body.roleId !== "string" ||
    body.roleId.trim() === ""
  ) {
    throw new UserAdminHttpError(
        400,
        "ROLE_ID_REQUIRED",
        "A role must be selected.",
    );
  }


  return body.roleId.trim();
}


/**
 * Contains the handle error step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function handleError(
    error,
    response,
) {
  if (error instanceof
      ApiAuthenticationError) {
    return response
      .status(
          error.statusCode,
      )
      .json({
        code:
          error.code,

        message:
          error.message,
      });
  }


  if (error instanceof
      UserAdminHttpError) {
    return response
      .status(
          error.statusCode,
      )
      .json({
        code:
          error.code,

        message:
          error.message,
      });
  }


  if (
    error &&
    typeof error.code === "string" &&
    error.code.startsWith(
        "auth/",
    )
  ) {
    return response
      .status(401)
      .json({
        code:
          "INVALID_AUTH_TOKEN",

        message:
          "Authentication could not be verified.",
      });
  }


  console.error(
      "Role assignment failed:",
      {
        name:
          error &&
          error.name
            ? error.name
            : "Error",

        message:
          error &&
          error.message
            ? error.message
            : "Unknown failure",
      },
  );


  return response
    .status(500)
    .json({
      code:
        "ROLE_CHANGE_FAILED",

      message:
        "Unable to change the user's role at this time.",
    });
}


class ApiAuthenticationError
  extends Error {
  constructor(
      statusCode,
      code,
      message,
  ) {
    super(
        message,
    );


    this.name =
      "ApiAuthenticationError";


    this.statusCode =
      statusCode;


    this.code =
      code;
  }
}