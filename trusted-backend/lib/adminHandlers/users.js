/**
 * HTTP handler for the users administration endpoint. It translates the request
 * into a call to the trusted application service.
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
  listApplicationUsers,
} = require("../userAdminService");

const {
  UserAdminHttpError,
} = require("../userAdminAuthorization");


module.exports = async function handler(
    request,
    response,
) {
  if (request.method !== "GET") {
    response.setHeader(
        "Allow",
        "GET",
    );

    return response.status(405).json({
      code:
        "METHOD_NOT_ALLOWED",

      message:
        "Only GET is supported.",
    });
  }


  try {
    const idToken =
      requireBearerToken(
          request,
      );


    /*
     * Ensure the shared Firebase Admin application has
     * been initialised before requesting the Auth service.
     *
     * firebaseAdmin.js owns Admin SDK initialisation for
     * this backend.
     */
    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            idToken,
            true,
        );


    const result =
      await listApplicationUsers(
          decodedToken.uid,
      );


    return response.status(200).json(
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
 * Converts authentication and user-admin failures into stable list-endpoint
 * responses. Unexpected errors are logged and returned with a generic server
 * message instead of their internal details.
 *
 * @param {Error} error failure raised while loading users
 * @param {Object} response Vercel response object
 * @return {*} JSON error response
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


  /*
   * Firebase Authentication errors must not expose
   * token contents or internal credential details.
   */
  if (error &&
      typeof error.code === "string" &&
      error.code.startsWith(
          "auth/",
      )) {
    return response.status(401).json({
      code:
        "INVALID_AUTH_TOKEN",

      message:
        "Authentication could not be verified.",
    });
  }


  console.error(
      "User list request failed:",
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


  return response.status(500).json({
    code:
      "USER_LIST_FAILED",

    message:
      "Unable to load users at this time.",
  });
}


class ApiAuthenticationError extends Error {
  constructor(
      statusCode,
      code,
      message,
  ) {
    super(message);

    this.name =
      "ApiAuthenticationError";

    this.statusCode =
      statusCode;

    this.code =
      code;
  }
}