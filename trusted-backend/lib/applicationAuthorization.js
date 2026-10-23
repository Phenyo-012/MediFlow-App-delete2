/**
 * Centralizes application-level authorization before privileged inventory or
 * administration work is accepted.
 *
 * A valid Firebase identity alone is not enough; the account must also have an
 * active profile and the required permission.
 */
const {
  getAdminFirestore,
} = require("./firebaseAdmin");


class ApplicationHttpError extends Error {
  /**
   * @param {number} statusCode HTTP status code.
   * @param {string} code Stable application error code.
   * @param {string} message Safe user-facing message.
   */
  constructor(
      statusCode,
      code,
      message,
  ) {
    super(message);

    this.name =
      "ApplicationHttpError";

    this.statusCode =
      statusCode;

    this.code =
      code;
  }
}


/**
 * Creates the authoritative application-permission
 * checker.
 *
 * Production uses getAdminFirestore().
 *
 * Tests may provide an isolated Firestore provider
 * without connecting to Firebase.
 *
 * @param {Function} firestoreProvider
 * @return {Object}
 */
/**
 * Applies create application authorization on the server. Signing in does not
 * automatically grant this permission.
 */
function createApplicationAuthorization(
    firestoreProvider,
) {
  const getFirestore =
    firestoreProvider ||
    getAdminFirestore;


  /**
   * Loads the authoritative current user profile and
   * verifies that the caller is active and currently
   * holds the required application permission.
   *
   * Client-side cached permissions are never trusted
   * for authoritative backend mutations.
   *
   * @param {string} authenticatedUid Firebase UID.
   * @param {string} requiredPermission Permission.
   * @return {Promise<object>} Authorised context.
   */
  /**
   * Contains the require active user step, keeping that decision separate from the
   * HTTP handler or test setup.
   */
  async function requireActiveUser(authenticatedUid) {
    const uid = requireText(authenticatedUid, "Authenticated user ID");
    const db = getFirestore();
    const snapshot = await db.collection("users").doc(uid).get();

    if (!snapshot.exists) {
      throw new ApplicationHttpError(403, "USER_PROFILE_NOT_FOUND",
          "Your account could not be verified.");
    }

    const profile = snapshot.data() || {};
    if (profile.uid !== undefined && profile.uid !== uid) {
      throw new ApplicationHttpError(403, "USER_PROFILE_INVALID",
          "Your account profile is invalid.");
    }
    if (profile.isActive !== true) {
      throw new ApplicationHttpError(403, "ACCOUNT_INACTIVE",
          "Your account is inactive.");
    }
    return {db, uid, profile, permissions: permissionsForUser(profile)};
  }

  /**
   * Applies require permission on the server. Signing in does not automatically
   * grant this permission.
   */
  async function requirePermission(authenticatedUid, requiredPermission) {
    const permission = requireText(requiredPermission, "Required permission");
    const context = await requireActiveUser(authenticatedUid);
    if (!context.permissions.includes(permission)) {
      throw new ApplicationHttpError(403, "PERMISSION_DENIED",
          "You do not have permission to perform this action.");
    }
    return context;
  }

  return {
    requireApplicationPermission: requirePermission,
    requireActiveUser,
  };
}


const defaultAuthorization =
  createApplicationAuthorization(
      getAdminFirestore,
  );


/**
 * Production application-permission check.
 *
 * @param {string} authenticatedUid Firebase UID.
 * @param {string} requiredPermission Permission.
 * @return {Promise<object>} Authorised context.
 */
/**
 * Applies require application permission on the server. Signing in does not
 * automatically grant this permission.
 */
async function requireApplicationPermission(
    authenticatedUid,
    requiredPermission,
) {
  return defaultAuthorization
    .requireApplicationPermission(
        authenticatedUid,
        requiredPermission,
    );
}


/** Checks that the current Firebase UID owns an active Firestore user profile. */
async function requireActiveUser(authenticatedUid) {
  return defaultAuthorization.requireActiveUser(authenticatedUid);
}


/**
 * Normalises the server-managed flattened
 * permission list.
 *
 * @param {Object} profile User profile.
 * @return {Array<string>} Permissions.
 */
/**
 * Resolves the employee permissions used to authorize this operation; a valid
 * login alone is not enough.
 */
function permissionsForUser(
    profile,
) {
  if (
    !profile ||
    !Array.isArray(
        profile.permissions,
    )
  ) {
    return [];
  }


  return profile.permissions
    .filter(
        (permission) =>
          typeof permission ===
          "string",
    )
    .map(
        (permission) =>
          permission.trim(),
    )
    .filter(
        (permission) =>
          permission !== "",
    );
}


/**
 * Requires a non-empty string.
 *
 * @param {*} value Candidate value.
 * @param {string} fieldName Field label.
 * @return {string} Normalised value.
 */
/**
 * Contains the require text step, keeping that decision separate from the HTTP
 * handler or test setup.
 */
function requireText(
    value,
    fieldName,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new ApplicationHttpError(
        400,
        "INVALID_REQUEST",
        `${fieldName} is required.`,
    );
  }


  return value.trim();
}


module.exports = {
  requireApplicationPermission,
  requireActiveUser,
  ApplicationHttpError,
  createApplicationAuthorization,
};