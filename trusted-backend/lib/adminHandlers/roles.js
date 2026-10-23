/**
 * HTTP handler for the roles administration endpoint. It translates the request
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
  listApplicationRoles,
} = require("../roleAdminService");

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
    const token =
      requireBearerToken(
          request,
      );


    /*
     * Initialise the shared Firebase Admin app before
     * requesting the Auth service.
     */
    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            token,
            true,
        );


    const result =
      await listApplicationRoles(
          decodedToken.uid,
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
 * Maps authentication and application failures to their expected HTTP responses;
 * other errors are logged and reduced to a generic server error rather than
 * leaking implementation details to the caller.
 *
 * @param {Error} error failure raised while handling the roles request
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
      "Role list request failed:",
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
      "ROLE_LIST_FAILED",

    message:
      "Unable to load roles at this time.",
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