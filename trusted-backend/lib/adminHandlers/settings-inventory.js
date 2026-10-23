/**
 * HTTP handler for the settings-inventory administration endpoint. It translates
 * the request into a call to the trusted application service.
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
  setInventorySettings,
} = require(
    "../inventorySettingsAdminService",
);

const {
  ApplicationHttpError,
} = require(
    "../applicationAuthorization",
);


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


    /*
     * Ensure Firebase Admin has been initialised
     * before verifying the ID token.
     */
    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            token,
            true,
        );


    const body =
      requireBody(
          request,
      );


    const result =
      await setInventorySettings(
          decodedToken.uid,
          body.nearExpiryWarningDays,
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
 * Accepts a parsed object or parses a JSON-string body, rejecting missing or
 * non-object payloads before settings validation and trusted mutation.
 *
 * @param {Object} request incoming settings request
 * @return {Object} parsed settings payload
 * @throws {ApplicationHttpError} when the request body is missing or malformed
 */
function requireBody(
    request,
) {
  if (
    request.body &&
    typeof request.body === "object" &&
    !Array.isArray(
        request.body,
    )
  ) {
    return request.body;
  }


  if (
    typeof request.body === "string" &&
    request.body.trim() !== ""
  ) {
    try {
      const parsed =
        JSON.parse(
            request.body,
        );


      if (
        !parsed ||
        typeof parsed !== "object" ||
        Array.isArray(
            parsed,
        )
      ) {
        throw new Error(
            "Body is not an object.",
        );
      }


      return parsed;
    } catch {
      throw new ApplicationHttpError(
          400,
          "INVALID_REQUEST_BODY",
          "The request body is invalid.",
      );
    }
  }


  throw new ApplicationHttpError(
      400,
      "INVALID_REQUEST_BODY",
      "A request body is required.",
  );
}


/**
 * Maps authentication and settings validation failures to their public HTTP
 * status and error code. Unexpected errors are logged and returned as a generic
 * server failure so internal details are not exposed to the caller.
 *
 * @param {Error} error failure raised while processing the settings update
 * @param {Object} response Vercel response object
 * @return {*} JSON error response
 */
function handleError(
    error,
    response,
) {
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


  if (
    error instanceof
      ApplicationHttpError
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
      "Inventory settings update failed:",
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
        "INVENTORY_SETTINGS_UPDATE_FAILED",

      message:
        "Unable to update inventory settings at this time.",
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