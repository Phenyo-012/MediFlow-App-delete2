/**
 * Vercel POST endpoint for processing a locally queued inventory operation. It requires a
 * revocation-checked Firebase ID token, validates the operation UUID, and passes only the verified
 * UID to the trusted Firestore processor. Known processor failures retain their safe status and
 * error code; unexpected failures are logged by category without credentials and returned as a
 * generic temporary-unavailability response. Stock validation and atomic writes live in the
 * processor so the HTTP boundary cannot bypass those rules.
 */
const {
  getAdminAuth,
} = require("../lib/firebaseAdmin");

const {
  processInventoryOperation,
  ProcessorHttpError,
} = require("../lib/inventoryProcessor");


/**
 * Validates the HTTP envelope and delegates a request only after token verification. The route
 * returns 405 for other methods, 401 for missing or invalid credentials, 400 for malformed input,
 * and processor-specific statuses for business conflicts or authorization failures.
 *
 * @param {import('http').IncomingMessage} request Vercel request
 * @param {import('http').ServerResponse} response Vercel response
 */
module.exports = async function handler(
    request,
    response,
) {
  if (request.method !== "POST") {
    response.setHeader(
        "Allow",
        "POST",
    );

    return response
        .status(405)
        .json({
          error:
            "METHOD_NOT_ALLOWED",
        });
  }


  const token =
    bearerToken(
        request.headers.authorization,
    );

  if (!token) {
    return response
        .status(401)
        .json({
          error:
            "AUTHENTICATION_REQUIRED",
        });
  }


  let decodedToken;

  try {
    decodedToken =
      await getAdminAuth()
          .verifyIdToken(
              token,
              true,
          );
  } catch {
    return response
        .status(401)
        .json({
          error:
            "INVALID_AUTHENTICATION_TOKEN",
        });
  }


  let body;

  try {
    body =
      parseBody(
          request.body,
      );
  } catch {
    return response
        .status(400)
        .json({
          error:
            "INVALID_JSON",
        });
  }


  const operationId =
    body &&
    typeof body.operationId ===
        "string"
        ? body.operationId.trim()
        : "";


  if (!isValidUuid(
      operationId,
  )) {
    return response
        .status(400)
        .json({
          error:
            "INVALID_OPERATION_ID",
        });
  }


  try {
    const result =
      await processInventoryOperation(
          operationId,
          decodedToken.uid,
      );

    return response
        .status(200)
        .json(result);
  } catch (error) {
    if (error instanceof
        ProcessorHttpError) {
      return response
          .status(
              error.statusCode,
          )
          .json({
            error:
              error.code,

            message:
              error.message,
          });
    }

    /*
     * Never log bearer tokens or credentials.
     */
    console.error(
        "Trusted inventory processor failed.",
        {
          operationId,

          category:
            error &&
            error.code
              ? String(error.code)
              : "UNKNOWN",
        },
    );

    return response
        .status(500)
        .json({
          error:
            "PROCESSING_TEMPORARILY_UNAVAILABLE",
        });
  }
};


/** Extracts the credential from an HTTP Bearer authorization header; token validity is checked by Firebase Admin. */
function bearerToken(
    authorizationHeader,
) {
  if (typeof authorizationHeader !==
      "string") {
    return null;
  }

  const match =
    authorizationHeader.match(
        /^Bearer\s+(.+)$/i,
    );

  if (!match) {
    return null;
  }

  return match[1].trim();
}


/**
 * Accepts Vercel's already-parsed object body or decodes a JSON string body. Unsupported body
 * forms become an empty object; malformed JSON throws so the handler can return its 400 response.
 *
 * @param {*} body raw or parsed request body
 * @return {object} parsed object, or an empty object for unsupported input
 * @throws {SyntaxError} when a string body is not valid JSON
 */
function parseBody(
    body,
) {
  if (body &&
      typeof body ===
          "object") {
    return body;
  }

  if (typeof body ===
      "string") {
    return JSON.parse(
        body,
    );
  }

  return {};
}


/** Accepts canonical UUIDs with RFC variant and versions 1 through 5 for operation document IDs. */
function isValidUuid(
    value,
) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      .test(
          value,
      );
}