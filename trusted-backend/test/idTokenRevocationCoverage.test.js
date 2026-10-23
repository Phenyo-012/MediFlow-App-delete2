/**
 * Guards protected API entry points against accepting revoked Firebase ID tokens.
 */

"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync} = require("node:fs");
const {join} = require("node:path");

const root = join(__dirname, "..");
const entrypoints = [
  "api/process-inventory-operation.js",
  "api/reconcile-inventory-operation.js",
  "lib/medicineMasterHttp.js",
  "lib/adminHandlers/medicines-classification.js",
  "lib/adminHandlers/permissions.js",
  "lib/adminHandlers/roles.js",
  "lib/adminHandlers/settings-inventory.js",
  "lib/adminHandlers/users-create.js",
  "lib/adminHandlers/users-detail.js",
  "lib/adminHandlers/users-permissions.js",
  "lib/adminHandlers/users-role.js",
  "lib/adminHandlers/users.js",
];

test("all Firebase token verifier callsites check account revocation", () => {
  for (const filename of entrypoints) {
    const source = readFileSync(join(root, filename), "utf8");
    assert.match(source, /\.verifyIdToken\(\s*(?:token|idToken)\s*,\s*true\s*,?\s*\)/,
        filename);
  }
});

test("reconciliation enforces active profile and processor checks it transactionally", () => {
  const reconciliation = readFileSync(join(root, "lib/inventoryReconciliation.js"),
      "utf8");
  const processor = readFileSync(join(root, "lib/inventoryProcessor.js"), "utf8");
  assert.match(reconciliation, /await requireActiveUser\(authenticatedUid\)/);
  const checkPosition = processor.indexOf("const sessionSnapshot =");
  const terminalPosition = processor.indexOf("if (TERMINAL_STATUSES.has(");
  assert.ok(checkPosition >= 0 && terminalPosition > checkPosition);
});
