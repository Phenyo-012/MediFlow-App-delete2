/**
 * HTTP handler for the users-detail administration endpoint. It translates the
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
  getApplicationUserDetail,
  setApplicationUserActiveState,
} = require("../userAdminService");

const {
  UserAdminHttpError,
} = require("../userAdminAuthorization");


module.exports = async function handler(
    request,
    response,
) {

  if (request.method !== "GET" &&
      request.method !== "PATCH" &&
      request.method !== "PUT") {
    response.setHeader(
        "Allow",
        "GET, PATCH, PUT",
    );

    return response.status(405).json({
      code:
        "METHOD_NOT_ALLOWED",

      message:
        "Only GET, PATCH and PUT are supported.",
    });
  }


  try {
    const idToken =
      requireBearerToken(
          request,
      );


    /*
     * firebaseAdmin.js owns Firebase Admin SDK
     * initialisation.
     *
     * This MUST happen before getAuth() is used.
     */
    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            idToken,
            true,
        );


    const targetUid =
      routeUid(
          request,
      );


    /*
     * GET /api/admin/users/{uid}
     */
    if (request.method === "GET") {
      const result =
        await getApplicationUserDetail(
            decodedToken.uid,
            targetUid,
        );


      return response
        .status(200)
        .json(
            result,
        );
    }


    /*
     * PATCH /api/admin/users/{uid}
     *
     * Expected body:
     *
     * {
     *   "isActive": true
     * }
     *
     * or
     *
     * {
     *   "isActive": false
     * }
     */
    const body =
      parseRequestBody(
          request.body,
      );


    const active =
      requireActiveState(
          body,
      );


    const result =
      await setApplicationUserActiveState(
          decodedToken.uid,
          targetUid,
          active,
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
 * Gets the UID supplied by Vercel's dynamic route:
 *
 * /api/admin/users/[uid]
 *
 * @param {*} request Incoming request.
 * @return {string|undefined} Target user UID.
 */
/**
 * Contains the route user ID step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function routeUid(
    request,
) {
  const rawUid =
    request.query
      ? request.query.uid
      : null;


  if (Array.isArray(
      rawUid,
  )) {
    return rawUid[0];
  }


  return rawUid;
}


/**
 * Extracts:
 *
 * Authorization: Bearer <Firebase ID token>
 *
 * @param {*} request Incoming request.
 * @return {string} Firebase ID token.
 */
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


  if (
    typeof authorization !==
      "string"
  ) {
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


  if (
    !match ||
    !match[1] ||
    match[1].trim() === ""
  ) {
    throw new ApiAuthenticationError(
        401,
        "INVALID_AUTHORIZATION_HEADER",
        "Authentication is required.",
    );
  }


  return match[1].trim();
}


/**
 * Vercel normally supplies parsed JSON for an
 * application/json request.
 *
 * This additionally accepts a JSON string so the
 * endpoint fails cleanly if body parsing differs.
 *
 * @param {*} body Incoming request body.
 * @return {object} Parsed request body.
 */
/**
 * Checks request body before the value reaches trusted storage. Invalid or
 * ambiguous input should fail here.
 */
function parseRequestBody(
    body,
) {
  if (
    body &&
    typeof body === "object" &&
    !Array.isArray(
        body,
    )
  ) {
    return body;
  }


  if (
    typeof body === "string" &&
    body.trim() !== ""
  ) {
    try {
      const parsed =
        JSON.parse(
            body,
        );


      if (
        parsed &&
        typeof parsed === "object" &&
        !Array.isArray(
            parsed,
        )
      ) {
        return parsed;
      }
    } catch {
      throw new UserAdminHttpError(
          400,
          "INVALID_REQUEST_BODY",
          "The request body is invalid.",
      );
    }
  }


  throw new UserAdminHttpError(
      400,
      "INVALID_REQUEST_BODY",
      "A request body is required.",
  );
}


/**
 * Validates the requested account active state.
 *
 * @param {*} body Parsed request body.
 * @return {boolean} Requested active state.
 */
/**
 * Contains the require active state step, keeping that decision separate from the
 * HTTP handler or test setup.
 */
function requireActiveState(
    body,
) {
  if (
    !body ||
    typeof body.isActive !==
      "boolean"
  ) {
    throw new UserAdminHttpError(
        400,
        "INVALID_ACTIVE_STATE",
        "isActive must be true or false.",
    );
  }


  return body.isActive;
}


/**
 * Converts trusted errors into safe API responses.
 *
 * @param {*} error Error being handled.
 * @param {*} response HTTP response.
 * @return {*} HTTP response.
 */
/**
 * Contains the handle error step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function handleError(
    error,
    response,
) {
  /*
   * Request authentication errors.
   */
  if (
    error instanceof
      ApiAuthenticationError
  ) {
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


  /*
   * Expected user-administration errors.
   *
   * Examples:
   *
   * PERMISSION_DENIED
   * ACCOUNT_INACTIVE
   * USER_NOT_FOUND
   * SELF_DEACTIVATION_NOT_ALLOWED
   * INVALID_ACTIVE_STATE
   */
  if (
    error instanceof
      UserAdminHttpError
  ) {
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


  /*
   * Firebase Authentication failures.
   *
   * Never return token details or internal Firebase
   * credential information.
   */
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


  /*
   * Unexpected server failure.
   *
   * Log only safe diagnostic information.
   */
  console.error(
      "User administration request failed:",
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
        "USER_ADMIN_REQUEST_FAILED",

      message:
        "Unable to process this user request at this time.",
    });
}


/**
 * HTTP-layer authentication error.
 */
class ApiAuthenticationError
    extends Error {
  /**
   * @param {number} statusCode HTTP status.
   * @param {string} code Stable error code.
   * @param {string} message Safe user-facing message.
   */
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