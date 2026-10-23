/**
 * Defines how public admin URL paths map to a small, explicit set of internal
 * handler names.
 *
 * Route order matters where static and parameterized paths overlap.
 */
"use strict";

const AVAILABLE_ROUTES = new Set([
  "roles",
  "permissions",
  "users",
  "users-create",
  "users-detail",
  "users-permissions",
  "users-role",
  "users-totp-recovery",
  "settings-inventory",
  "medicines-classification",
  "medicines-barcodes",
  "medicines-deactivate",
  "medicines-create",
  "medicines-update",
]);

const DYNAMIC_IDS = Object.freeze({
  "users-detail": "uid",
  "users-permissions": "uid",
  "users-role": "uid",
  "users-totp-recovery": "uid",
  "medicines-classification": "medicineId",
  "medicines-update": "medicineId",
  "medicines-barcodes": "medicineId",
  "medicines-deactivate": "medicineId",
});

/**
 * Match the private rewrite selector to one of the existing HTTP handlers.
 * Rewrites pass the original dynamic path value as a request query property.
 * Do not trust this selector for authorization: every original handler still
 * authenticates and checks its own application permissions.
 *
 * @param {Object} request Vercel request.
 * @param {Object} response Vercel response.
 * @param {Object} handlers Route implementation map, injectable for tests.
 * @return {*} Result of the selected handler or the error response.
 */
/**
 * Rejects unknown rewrite selectors, validates a route's dynamic ID segment, and
 * dispatches only to the handler registered for the allow-listed route. This
 * router is a mapping layer rather than a security check: the selected handler
 * still authenticates and authorizes the request.
 */
function dispatchAdminRequest(request, response, handlers) {
  const query = request && request.query;
  const route = query && query.__mediflowRoute;
  if (typeof route !== "string" ||
      !AVAILABLE_ROUTES.has(route)) {
    return response.status(404).json({
      code: "NOT_FOUND", message: "This admin route does not exist.",
    });
  }

  const paramName = DYNAMIC_IDS[route];

if (paramName) {
  const paramValue = query[paramName];

  if (typeof paramValue !== "string" ||
      paramValue.length === 0 || paramValue.length > 256 ||
      paramValue === "." || paramValue === ".." ||
      paramValue.includes("/") ||
      paramValue.includes("\\") ||
      [...paramValue].some((char) => char.charCodeAt(0) <= 0x1F)) {
    return response.status(400).json({
      code: "INVALID_ROUTE_PARAMETER",
      message: "The requested record ID is invalid.",
    });
  }
}

  return handlers[route](request, response);
}

module.exports = {dispatchAdminRequest};
