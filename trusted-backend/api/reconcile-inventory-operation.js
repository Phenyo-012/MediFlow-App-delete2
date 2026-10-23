/**
 * Vercel HTTP entry point for inventory reconciliation.
 *
 * Keep HTTP validation separate from the service that reconciles authoritative
 * state.
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


/**
 * Applies read bearer token on the server. Signing in does not automatically grant
 * this permission.
 */
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
 * Checks body before the value reaches trusted storage. Invalid or ambiguous input
 * should fail here.
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