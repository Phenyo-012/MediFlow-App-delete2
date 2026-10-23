/**
 * Provides request parsing, revocation-aware authentication, and error mapping for medicine writes.
 */

"use strict";

const {getAuth} = require("firebase-admin/auth");
const {getAdminFirestore} = require("./firebaseAdmin");
const {ApplicationHttpError} = require("./applicationAuthorization");

function requireBearerToken(request) {
  const header = request.headers && request.headers.authorization;
  const match = typeof header === "string" ?
    header.match(/^Bearer\s+(.+)$/i) : null;
  if (!match || !match[1].trim()) {
    throw new ApplicationHttpError(401, "AUTHENTICATION_REQUIRED",
        "Authentication is required.");
  }
  return match[1].trim();
}


/**
 * Parses the request body and rejects arrays, absent values, and malformed JSON before
 * callers perform any Firestore mutation.
 */
function requireBody(request) {
  let value = request.body;
  if (typeof value === "string") {
    try {
      value = JSON.parse(value);
    } catch {
      throw new ApplicationHttpError(400, "INVALID_REQUEST_BODY",
          "A valid JSON object is required.");
    }
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new ApplicationHttpError(400, "INVALID_REQUEST_BODY",
        "A JSON object is required.");
  }
  return value;
}


/**
 * Verifies the bearer ID token with revocation checking before returning its UID.
 */
async function authenticate(request) {
  const token = requireBearerToken(request);
  getAdminFirestore();
  const decoded = await getAuth().verifyIdToken(token, true);
  return decoded.uid;
}


/**
 * Maps expected request/authentication failures to their HTTP status and hides
 * unexpected write details behind a generic server error response.
 */
function handleError(error, response) {
  if (error instanceof ApplicationHttpError) {
    return response.status(error.statusCode).json({
      code: error.code, message: error.message,
    });
  }
  if (error && typeof error.code === "string" &&
      error.code.startsWith("auth/")) {
    return response.status(401).json({
      code: "INVALID_AUTH_TOKEN", message: "Authentication could not be verified.",
    });
  }
  console.error("Medicine master mutation failed:", {
    name: error && error.name || "Error",
    message: error && error.message || "Unknown failure",
  });
  return response.status(500).json({
    code: "MEDICINE_WRITE_FAILED", message: "Unable to save medicine at this time.",
  });
}

module.exports = {authenticate, requireBody, handleError};
