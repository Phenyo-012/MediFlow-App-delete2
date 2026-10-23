/**
 * Receives administrative API requests and delegates them through the explicit
 * route-dispatch layer.
 *
 * The routing boundary makes it easier to audit which handlers are reachable and
 * which HTTP methods they accept.
 */
"use strict";

// Only this entry point is deployed as an admin serverless function.
// All legacy handlers remain separate CommonJS modules in lib/adminHandlers.
const {dispatchAdminRequest} = require("../lib/adminRouteDispatch");

module.exports = dispatchAdminRequest;
