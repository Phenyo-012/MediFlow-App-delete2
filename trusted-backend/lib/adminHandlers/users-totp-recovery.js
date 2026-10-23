/**
 * HTTP handler for the users-totp-recovery administration endpoint. It translates
 * the request into a call to the trusted application service.
 *
 * Keep authentication, allowed methods and error responses explicit so a malformed
 * request does not accidentally reach a write operation.
 */
"use strict";

const {getAdminAuth} = require("../firebaseAdmin");
const {
  createTotpRecoveryService,
  TotpRecoveryError,
} = require("../userTotpRecoveryService");
const {ApplicationHttpError} = require("../applicationAuthorization");

const recovery = createTotpRecoveryService();

module.exports = async function handler(request, response) {
  if (request.method !== "GET" && request.method !== "POST") {
    response.setHeader("Allow", "GET, POST");
    return response.status(405).json({code: "METHOD_NOT_ALLOWED",
      message: "Only GET and POST are supported."});
  }
  const authorization = request.headers && request.headers.authorization;
  const match = typeof authorization === "string" ?
    authorization.match(/^Bearer\s+(\S+)$/i) : null;
  if (!match) {
    return response.status(401).json({code: "AUTHENTICATION_REQUIRED",
      message: "Authentication is required."});
  }

  try {
    const decoded = await getAdminAuth().verifyIdToken(match[1], true);
    const uid = request.query && request.query.uid;
    const result = request.method === "GET" ?
      await recovery.getTotpStatus(decoded.uid, uid) :
      await recovery.resetTotp(decoded.uid, uid, request.body, decoded.auth_time);
    return response.status(200).json(result);
  } catch (error) {
    if (error instanceof TotpRecoveryError || error instanceof ApplicationHttpError) {
      return response.status(error.statusCode).json({
        code: error.code, message: error.message,
      });
    }
    if (error && error.code === "auth/user-not-found") {
      return response.status(404).json({code: "USER_NOT_FOUND",
        message: "User not found."});
    }
    if (error && typeof error.code === "string" && error.code.startsWith("auth/")) {
      return response.status(401).json({code: "INVALID_AUTH_TOKEN",
        message: "Authentication could not be verified."});
    }
    console.error("TOTP recovery failed", {code: error && error.code || "UNKNOWN"});
    return response.status(500).json({code: "RECOVERY_REQUEST_FAILED",
      message: "Unable to complete this request."});
  }
};
