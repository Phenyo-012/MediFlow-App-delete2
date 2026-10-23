/**
 * Vercel entry point for processing a stock operation submitted by a device.
 *
 * The actual validation and transaction logic lives in the inventory processor
 * rather than in this HTTP wrapper.
 */
const {
  getAdminAuth,
} = require("../lib/firebaseAdmin");

const {
  processInventoryOperation,
  ProcessorHttpError,
} = require("../lib/inventoryProcessor");


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


/**
 * Applies bearer token on the server. Signing in does not automatically grant this
 * permission.
 */
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
 * Checks body before the value reaches trusted storage. Invalid or ambiguous input
 * should fail here.
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


/**
 * Contains the is valid uuid step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function isValidUuid(
    value,
) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
      .test(
          value,
      );
}