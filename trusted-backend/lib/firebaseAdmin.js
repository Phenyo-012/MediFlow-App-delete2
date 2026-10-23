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
 * Contains the required environment variable step, keeping that decision separate
 * from the HTTP handler or test setup.
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
 * Looks up get firebase admin app from trusted state and returns the result in the
 * format the caller expects.
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


/**
 * Looks up get admin firestore from trusted state and returns the result in the
 * format the caller expects.
 */
function getAdminFirestore() {
  const app =
    getFirebaseAdminApp();

  return getFirestore(app);
}


/**
 * Looks up get admin auth from trusted state and returns the result in the format
 * the caller expects.
 */
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