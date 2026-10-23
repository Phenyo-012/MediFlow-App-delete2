/**
 * Connects the admin router to its individual handlers without relying on client
 * input to choose arbitrary code.
 *
 * Explicit dispatch keeps unknown and malformed routes from reaching privileged
 * operations.
 */
"use strict";

// Static requires guarantee Vercel's Node file tracer bundles all original
// handlers; moving them outside api/ avoids counting each as a function.
const HANDLERS = Object.freeze({
  "roles": require("./adminHandlers/roles"),
  "permissions": require("./adminHandlers/permissions"),
  "users": require("./adminHandlers/users"),
  "users-create": require("./adminHandlers/users-create"),
  "users-detail": require("./adminHandlers/users-detail"),
  "users-permissions": require("./adminHandlers/users-permissions"),
  "users-role": require("./adminHandlers/users-role"),
  "users-totp-recovery": require("./adminHandlers/users-totp-recovery"),
  "settings-inventory": require("./adminHandlers/settings-inventory"),
  "medicines-classification":
    require("./adminHandlers/medicines-classification"),
  "medicines-barcodes": require("./adminHandlers/medicines-barcodes"),
  "medicines-deactivate": require("./adminHandlers/medicines-deactivate"),
  "medicines-create": require("./adminHandlers/medicines-create"),
  "medicines-update": require("./adminHandlers/medicines-update"),
});

const {dispatchAdminRequest} = require("./adminRouting");

module.exports = {
  dispatchAdminRequest: (request, response) =>
    dispatchAdminRequest(request, response, HANDLERS),
};
