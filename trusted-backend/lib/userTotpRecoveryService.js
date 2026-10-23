/**
 * Implements administrator-assisted authenticator recovery with recent sign-in, reason, replay, and audit checks.
 */

"use strict";



const RESET_PERMISSION = "MANAGE_USER_PERMISSIONS";
const VIEW_PERMISSION = "VIEW_USERS";
const MAX_AUTH_AGE_MS = 5 * 60 * 1000;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

class TotpRecoveryError extends Error {
  constructor(statusCode, code, message) {
    super(message);
    this.name = "TotpRecoveryError";
    this.statusCode = statusCode;
    this.code = code;
  }
}


function createTotpRecoveryService({
  authorize = (uid, permission) =>
    require("./userAdminAuthorization")
        .requireUserAdminPermission(uid, permission),
  getAuthClient = () => require("./firebaseAdmin").getAdminAuth(),
  serverTimestamp = () =>
    require("firebase-admin/firestore").FieldValue.serverTimestamp(),
  now = () => Date.now(),
} = {}) {

  function validUid(uid) {
    if (typeof uid !== "string" || !uid.trim() ||
        uid.length > 256 || uid.includes("/") || uid.includes("\\")) {
      throw new TotpRecoveryError(400, "INVALID_USER_ID", "Invalid user ID.");
    }
    return uid.trim();
  }


  async function loadTarget(db, uid) {
    const snapshot = await db.collection("users").doc(uid).get();
    if (!snapshot.exists) {
      throw new TotpRecoveryError(404, "USER_NOT_FOUND", "User not found.");
    }
    const profile = snapshot.data() || {};
    if (profile.uid !== undefined && profile.uid !== uid) {
      throw new TotpRecoveryError(409, "USER_PROFILE_INVALID",
          "The account profile is inconsistent.");
    }
    return profile;
  }


  function enrolledFactors(record) {
    const list = record && record.multiFactor && record.multiFactor.enrolledFactors;
    return Array.isArray(list) ? list : [];
  }


  async function getTotpStatus(actorUid, targetUid) {
    const uid = validUid(targetUid);
    const context = await authorize(actorUid, VIEW_PERMISSION);
    await loadTarget(context.db, uid);
    const record = await getAuthClient().getUser(uid);
    return {
      uid,
      totpEnrolled: enrolledFactors(record).some((factor) => factor.factorId === "totp"),
    };
  }


  /**
   * Resets another user's authenticator only after permission, recent sign-in, request-ID,
   * and reason checks. The audit is written before Firebase factor removal and token revocation.
   *
   * @param actorUid administrator performing recovery
   * @param targetUid account whose authenticator is being reset
   * @param body validated recovery request containing requestId and reason
   * @param authTimeSeconds Firebase authentication time from the verified token
   */
  async function resetTotp(actorUid, targetUid, body, authTimeSeconds) {
    const uid = validUid(targetUid);
    if (actorUid === uid) {
      throw new TotpRecoveryError(403, "SELF_RECOVERY_NOT_ALLOWED",
          "An administrator cannot reset their own authenticator.");
    }
    if (!body || typeof body !== "object" || Array.isArray(body) ||
        typeof body.requestId !== "string" ||
        !UUID_PATTERN.test(body.requestId)) {
      throw new TotpRecoveryError(400, "INVALID_REQUEST_ID",
          "A valid requestId UUID is required.");
    }
    if (typeof body.reason !== "string" ||
        body.reason.trim().length < 12 || body.reason.trim().length > 500) {
      throw new TotpRecoveryError(400, "INVALID_REASON",
          "Provide a recovery reason between 12 and 500 characters.");
    }
    const context = await authorize(actorUid, RESET_PERMISSION);
    const current = now();
    const authenticatedAt = authTimeSeconds * 1000;
    if (!Number.isFinite(authenticatedAt) ||
        authenticatedAt > current + 60 * 1000 ||
        current - authenticatedAt > MAX_AUTH_AGE_MS) {
      throw new TotpRecoveryError(403, "RECENT_SIGN_IN_REQUIRED",
          "Please reauthenticate before resetting an authenticator.");
    }
    await loadTarget(context.db, uid);
    const auth = getAuthClient();
    const record = await auth.getUser(uid);
    const factors = enrolledFactors(record);
    const totp = factors.filter((factor) => factor.factorId === "totp");
    if (totp.length === 0) {
      throw new TotpRecoveryError(409, "TOTP_NOT_ENROLLED",
          "The selected account has no enrolled authenticator.");
    }
    if (totp.length !== factors.length) {
      throw new TotpRecoveryError(409, "OTHER_FACTORS_PRESENT",
          "This account has other MFA factors; use manual recovery.");
    }

    const auditId = `mfa_totp_recovery_${body.requestId.toLowerCase()}`;
    const auditRef = context.db.collection("auditLogs").doc(auditId);
    try {

      await auditRef.create({
        auditId,
        actionType: "MFA_TOTP_RECOVERY",
        entityType: "USER",
        entityId: uid,
        performedBy: actorUid,
        performedAt: serverTimestamp(),
        status: "REQUESTED",
        details: {
          reason: body.reason.trim(),
          removedFactorType: "totp",
        },
      });
    } catch (error) {
      if (error && (error.code === 6 || error.code === "already-exists")) {
        throw new TotpRecoveryError(409, "DUPLICATE_RECOVERY_REQUEST",
            "This recovery request has already been submitted.");
      }
      throw error;
    }

    try {

      await auth.updateUser(uid, {multiFactor: {enrolledFactors: null}});
      await auth.revokeRefreshTokens(uid);
      await auditRef.update({
        status: "COMPLETED",
        completedAt: serverTimestamp(),
      });
    } catch {

      try {
        await auditRef.update({
          status: "REQUIRES_REVIEW",
          lastAttemptAt: serverTimestamp(),
        });
      } catch {

      }
      throw new TotpRecoveryError(503, "RECOVERY_REQUIRES_REVIEW",
          "Recovery could not be confirmed. Check the account before retrying.");
    }
    return {uid, reset: true, requestId: body.requestId};
  }

  return {getTotpStatus, resetTotp};
}

module.exports = {createTotpRecoveryService, TotpRecoveryError};
