/**
 * HTTP handler for the medicines-classification administration endpoint. It
 * translates the request into a call to the trusted application service.
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
  setMedicineClassification,
} = require(
    "../medicineClassificationAdminService",
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
     * Ensure Firebase Admin is initialised before
     * verifying the Firebase ID token.
     */
    getAdminFirestore();


    const decodedToken =
      await getAuth()
        .verifyIdToken(
            token,
            true,
        );


    const medicineId =
      requireMedicineId(
          request,
      );


    const body =
      requireBody(
          request,
      );


    /*
     * Validation of A/B/C and V/E/D remains inside
     * the trusted service. The route does not decide
     * business rules.
     */
    const result =
      await setMedicineClassification(
          decodedToken.uid,
          medicineId,
          body.abcClassification,
          body.vedClassification,
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
 * Reads and trims the medicine ID supplied by the rewritten admin route, failing
 * with a client error when the selector has no usable ID.
 *
 * @param {Object} request incoming route request
 * @return {string} non-empty medicine ID
 * @throws {ApplicationHttpError} when the medicine ID is missing or blank
 */
function requireMedicineId(
    request,
) {
  const value =
    request.query &&
    request.query.medicineId;


  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new ApplicationHttpError(
        400,
        "MEDICINE_ID_REQUIRED",
        "A medicine is required.",
    );
  }


  return value.trim();
}


/**
 * Accepts an already-parsed object body or parses a non-empty JSON string body.
 * Arrays, primitives, malformed JSON, and missing bodies are rejected before
 * classification fields reach the admin service.
 *
 * @param {Object} request incoming admin request
 * @return {Object} parsed request body
 * @throws {ApplicationHttpError} when the body is absent or not a JSON object
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
 * Maps authentication and classification validation failures to their public
 * status, code, and message. Unexpected failures become a generic server error
 * so the endpoint does not disclose internal exception details.
 *
 * @param {Error} error failure raised by the classification route
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


  /*
   * Firebase Admin authentication failures must not
   * expose internal verification details.
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


  console.error(
      "ABC-VED classification update failed:",
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
        "ABC_VED_UPDATE_FAILED",

      message:
        "Unable to update the ABC-VED classification at this time.",
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