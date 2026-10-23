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
 * Builds an authorization boundary around an injectable Firestore provider. The returned checks
 * load the current user document on every request, reject missing, mismatched, or inactive
 * profiles, and derive permissions from that server-managed document rather than client state.
 * Tests can supply an isolated provider while production uses Firebase Admin Firestore.
 *
 * @param {Function} firestoreProvider Firestore provider, defaulting to the Admin SDK provider
 * @return {{requireApplicationPermission: Function, requireActiveUser: Function}}
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
   * Resolves an authenticated UID to its current active profile. This check deliberately reads
   * Firestore at request time so deactivation or profile corruption is observed immediately; it
   * returns the database, UID, profile, and normalized permissions for the next authorization step.
   *
   * @param {string} authenticatedUid UID taken from a verified Firebase ID token
   * @return {Promise<object>} active server profile context
   * @throws {ApplicationHttpError} when the profile is absent, invalid, or inactive
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
   * Requires both an active user profile and the requested server-side permission. A signed-in
   * identity without that permission receives a stable 403 application error; successful callers
   * receive the same profile context used by downstream service logic.
   *
   * @param {string} authenticatedUid UID from the verified token
   * @param {string} requiredPermission permission key required for the operation
   * @return {Promise<object>} active profile context authorized for the requested permission
   * @throws {ApplicationHttpError} when the permission is absent or the profile is not active
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
 * Applies the production authorization checker to one protected operation. Callers pass the UID
 * only after Firebase token verification; this function then reads current profile permissions
 * rather than trusting claims or cached Android profile data.
 *
 * @param {string} authenticatedUid verified Firebase UID
 * @param {string} requiredPermission application permission required by the endpoint
 * @return {Promise<object>} active authorized profile context
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
 * Requires a non-empty string and returns its trimmed value for authorization checks. This keeps
 * blank IDs and permission names from being interpreted differently by individual endpoints.
 *
 * @param {*} value candidate request or profile value
 * @param {string} fieldName safe label included in the validation response
 * @return {string} trimmed non-empty value
 * @throws {ApplicationHttpError} with status 400 when the value is missing or blank
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