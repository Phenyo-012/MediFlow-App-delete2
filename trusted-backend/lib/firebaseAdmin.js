/**
 * Initializes and exposes the Firebase Admin SDK for trusted server-side work.
 *
 * Admin credentials belong on the server, never in the Android application.
 */
const {
  cert,
  getApps,
  initializeApp,
} = require("firebase-admin/app");

const {
  getFirestore,
} = require("firebase-admin/firestore");

const {
  getAuth,
} = require("firebase-admin/auth");


/**
 * Reads a required Firebase Admin credential from the server environment. Keeping this check at
 * initialization gives missing deployment configuration a clear failure before privileged work
 * begins; the value is never returned in an HTTP response or logged by this helper.
 *
 * @param {string} name environment variable name
 * @return {string} non-empty configured value
 * @throws {Error} when the variable is absent or blank
 */
function requiredEnvironmentVariable(name) {
  const value =
    process.env[name];

  if (!value ||
      value.trim().length === 0) {
    throw new Error(
        `Missing required environment variable: ${name}`,
    );
  }

  return value;
}


/**
 * Reuses the process-wide Admin app when one exists; otherwise it initializes one from server-only
 * project and service-account credentials. The escaped-newline conversion supports private keys
 * supplied through environment variables without placing credentials in the client application.
 *
 * @return {import('firebase-admin/app').App} initialized Firebase Admin app
 */
function getFirebaseAdminApp() {
  const existingApps =
    getApps();

  if (existingApps.length > 0) {
    return existingApps[0];
  }

  const projectId =
    requiredEnvironmentVariable(
        "FIREBASE_PROJECT_ID",
    );

  const clientEmail =
    requiredEnvironmentVariable(
        "FIREBASE_CLIENT_EMAIL",
    );

  const rawPrivateKey =
    requiredEnvironmentVariable(
        "FIREBASE_PRIVATE_KEY",
    );

  const privateKey =
    rawPrivateKey.replace(
        /\\n/g,
        "\n",
    );

  return initializeApp({
    credential: cert({
      projectId,
      clientEmail,
      privateKey,
    }),
    projectId,
  });
}


/** Returns Firestore bound to the shared Admin app for trusted server-side reads and writes. */
function getAdminFirestore() {
  const app =
    getFirebaseAdminApp();

  return getFirestore(app);
}


/** Returns Firebase Admin Auth for ID-token verification and account administration. */
function getAdminAuth() {
  const app =
    getFirebaseAdminApp();

  return getAuth(app);
}


module.exports = {
  getFirebaseAdminApp,
  getAdminFirestore,
  getAdminAuth,
};