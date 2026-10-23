/**
 * Vercel POST endpoint that returns the authoritative terminal result and affected stock snapshots
 * for one locally pending operation. Firebase verifies the bearer token with revocation checking
 * before the endpoint accepts a UUID; the reconciliation service then enforces active-account and
 * operation-owner checks. This route reads current cloud state and does not replay the operation.
 */
const {
  getAdminAuth,
} = require("../lib/firebaseAdmin");

const {
  getInventoryReconciliation,
  ReconciliationHttpError,
} = require(
  "../lib/inventoryReconciliation"
);


const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;


/**
 * Checks method, credentials, and operation ID before requesting an owner-scoped reconciliation
 * snapshot. Known reconciliation failures map to their HTTP status and stable code; unexpected
 * failures are logged without request credentials and returned as a generic server error.
 *
 * @param {import('http').IncomingMessage} request Vercel request
 * @param {import('http').ServerResponse} response Vercel response
 */
module.exports = async function handler(
  request,
  response,
) {

  if (request.method !== "POST") {

    response
      .status(405)
      .json({
        error: "METHOD_NOT_ALLOWED",
        message: "POST is required.",
      });

    return;
  }


  try {

    const token =
      readBearerToken(
        request.headers.authorization,
      );


    if (!token) {

      response
        .status(401)
        .json({
          error: "AUTHENTICATION_REQUIRED",
          message:
            "A Firebase authentication token is required.",
        });

      return;
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

      response
        .status(401)
        .json({
          error:
            "INVALID_AUTHENTICATION_TOKEN",

          message:
            "The Firebase authentication token is invalid.",
        });

      return;
    }


    const body =
      parseBody(
        request.body,
      );


    const operationId =
      typeof body.operationId === "string"
        ? body.operationId.trim()
        : "";


    if (
      !UUID_PATTERN.test(
        operationId,
      )
    ) {

      response
        .status(400)
        .json({
          error: "INVALID_OPERATION_ID",
          message:
            "A valid inventory operation ID is required.",
        });

      return;
    }


    const result =
      await getInventoryReconciliation(
        operationId,
        decodedToken.uid,
      );


    response
      .status(200)
      .json(result);


  } catch (error) {

    if (
      error instanceof ReconciliationHttpError
    ) {

      response
        .status(
          error.statusCode,
        )
        .json({
          error: error.code,
          message: error.message,
        });

      return;
    }


    console.error(
      "Inventory reconciliation failed.",
      {
        name:
          error &&
          error.name
            ? error.name
            : "Error",
      },
    );


    response
      .status(500)
      .json({
        error:
          "INTERNAL_SERVER_ERROR",

        message:
          "Inventory reconciliation could not be completed.",
      });
  }
};


/** Extracts a non-empty bearer credential; Firebase Admin performs signature and revocation checks. */
function readBearerToken(
  authorizationHeader,
) {

  if (
    typeof authorizationHeader !== "string"
  ) {
    return null;
  }


  const match =
    authorizationHeader.match(
      /^Bearer\s+(.+)$/i,
    );


  if (!match) {
    return null;
  }


  const token =
    match[1].trim();


  return token === ""
    ? null
    : token;
}


/**
 * Normalizes Vercel's parsed object or a JSON string into a request object. Malformed JSON and
 * unsupported body forms become an empty object so required-field validation returns the endpoint's
 * stable invalid-operation response.
 *
 * @param {*} body request body supplied by Vercel
 * @return {object} parsed body or an empty object
 */
function parseBody(
  body,
) {

  if (
    body &&
    typeof body === "object"
  ) {
    return body;
  }


  if (
    typeof body === "string" &&
    body.trim() !== ""
  ) {

    try {
      return JSON.parse(
        body,
      );

    } catch {
      return {};
    }
  }


  return {};
}