/**
 * Implements administrator-assisted recovery for accounts enrolled in Firebase TOTP. Status reads
 * require VIEW_USERS; reset requires MANAGE_USER_PERMISSIONS, a different target UID, a recent
 * administrator sign-in, a UUID request ID, and a documented reason. The service audits the unique
 * request before removing factors and revoking refresh tokens, then marks completion or
 * REQUIRES_REVIEW if Firebase recovery cannot be confirmed. Dependency injection keeps the time,
 * authorization, Auth client, and timestamps controllable in tests.
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


/**
 * Creates the recovery operations with production defaults and optional test seams.
 *
 * @param {object} dependencies authorization, Admin Auth, timestamp, and clock providers
 * @return {{getTotpStatus: Function, resetTotp: Function}} recovery operations
 */
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


  /**
   * Reads whether the selected Firebase account currently has an enrolled TOTP factor. The caller
   * must have VIEW_USERS and the target Firestore profile must exist before Admin Auth is queried.
   *
   * @param {string} actorUid verified administrator UID
   * @param {string} targetUid account whose enrollment status is requested
   * @return {Promise<{uid: string, totpEnrolled: boolean}>} current factor status
   */
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
   * Resets another user's authenticator only after permission, recent sign-in, request-ID, and
   * reason checks. The audit is written before Firebase factor removal and token revocation; a
   * partial Firebase failure is marked for review instead of being reported as a completed reset.
   *
   * @param {string} actorUid verified administrator UID
   * @param {string} targetUid account whose authenticator is being reset
   * @param {object} body request ID and reason from the recovery request
   * @param {number} authTimeSeconds Firebase authentication time from the verified token
   * @return {Promise<{uid: string, reset: boolean, requestId: string}>} reset confirmation
   * @throws {TotpRecoveryError} for authorization, validation, duplicate, or recovery failures
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
        // Preserve the recovery failure if updating its audit status also fails.
      }
      throw new TotpRecoveryError(503, "RECOVERY_REQUIRES_REVIEW",
          "Recovery could not be confirmed. Check the account before retrying.");
    }
    return {uid, reset: true, requestId: body.requestId};
  }

  return {getTotpStatus, resetTotp};
}

module.exports = {createTotpRecoveryService, TotpRecoveryError};
