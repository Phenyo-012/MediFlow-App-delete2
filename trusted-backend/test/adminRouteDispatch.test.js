
/**
 * Checks deployed admin route rewrites, handler ordering, and fail-closed handling of invalid route shapes.
 */
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {readFileSync, readdirSync} = require("node:fs");
const {join} = require("node:path");
const {dispatchAdminRequest} = require("../lib/adminRouting");

const ROOT = join(__dirname, "..");
const cfg = JSON.parse(readFileSync(join(ROOT, "vercel.json"), "utf8"));
const paths = [
  "/api/admin/roles",
  "/api/admin/permissions",
  "/api/admin/users",
  "/api/admin/users/create",
  "/api/admin/users/:uid",
  "/api/admin/users/:uid/role",
  "/api/admin/users/:uid/totp-recovery",
  "/api/admin/users/:uid/permissions",
  "/api/admin/settings/inventory",
  "/api/admin/medicines",
  "/api/admin/medicines/:medicineId",
  "/api/admin/medicines/:medicineId/classification",
  "/api/admin/medicines/:medicineId/barcodes",
  "/api/admin/medicines/:medicineId/deactivate",
];


function makeResponse() {
  return {
    statusCode: 200,
    payload: null,

    status(status) { this.statusCode = status; return this; },

    json(payload) { this.payload = payload; return this; },
  };
}

test("all existing admin URLs have unique rewrites", () => {
  const rules = cfg.rewrites;
  assert.equal(rules.length, paths.length);
  assert.deepEqual(new Set(rules.map((entry) => entry.source)), new Set(paths));
  for (const rule of rules) {
    assert.match(
        rule.destination,
        /^\/api\/admin-router\?__mediflowRoute=[a-z-]+/,
    );
  }
});

test("specific user and medicine routes precede generic dynamic routes", () => {

  const ix = (source) => cfg.rewrites.findIndex((r) => r.source === source);
  assert.ok(ix("/api/admin/users/create") < ix("/api/admin/users/:uid"));
  assert.ok(ix("/api/admin/users/:uid/role") < ix("/api/admin/users/:uid"));
  assert.ok(ix("/api/admin/users/:uid/totp-recovery") <
    ix("/api/admin/users/:uid"));
  assert.ok(ix("/api/admin/users/:uid/permissions") <
    ix("/api/admin/users/:uid"));
  assert.ok(ix("/api/admin/medicines/:medicineId/barcodes") <
    ix("/api/admin/medicines/:medicineId"));
  assert.ok(ix("/api/admin/medicines/:medicineId/deactivate") <
    ix("/api/admin/medicines/:medicineId"));
  assert.ok(ix("/api/admin/medicines/:medicineId/classification") <
    ix("/api/admin/medicines/:medicineId"));
});

test("only four Vercel API function entry points remain", () => {

  const walk = (directory) => readdirSync(directory, {withFileTypes: true})
      .flatMap((entry) => entry.isDirectory() ?
        walk(join(directory, entry.name)) : [join(directory, entry.name)]);
  const apiFiles = walk(join(ROOT, "api"))
      .filter((file) => file.endsWith(".js"));
  assert.equal(apiFiles.length, 4, apiFiles.join(", "));
  assert.ok(apiFiles.some((file) => file.endsWith("admin-router.js")));
});

test("route selectors preserve their handlers", () => {
  const entries = cfg.rewrites;
  for (const rule of entries) {
    const url = new URL(rule.destination, "https://example.com");
    const name = url.searchParams.get("__mediflowRoute");
    const query = {__mediflowRoute: name};
    if (url.searchParams.has("uid")) query.uid = "example-uid";
    if (url.searchParams.has("medicineId")) query.medicineId = "example-med";
    let calls = 0;
    const handlers = {[name]: (req, res) => {
      calls++;
      assert.equal(req.query, query);
      return res.status(207).json({from: name});
    }};
    const res = makeResponse();
    dispatchAdminRequest({query}, res, handlers);
    assert.equal(calls, 1, rule.source);
    assert.equal(res.statusCode, 207);
    assert.deepEqual(res.payload, {from: name});
  }
});

test("unrecognised route and unexpected selector shapes fail closed", () => {
  for (const selector of [undefined, "", "nope", ["roles", "users"]]) {
    const result = makeResponse();
    dispatchAdminRequest({query: {__mediflowRoute: selector}}, result);
    assert.equal(result.statusCode, 404);
  }
});

test("missing or ambiguous dynamic IDs are rejected", () => {
  const selectors = [
    {__mediflowRoute: "users-role", uid: ["a", "b"]},
    {__mediflowRoute: "users-detail"},
    {__mediflowRoute: "users-permissions", uid: "../admin"},
    {__mediflowRoute: "medicines-update", medicineId: ""},
    {__mediflowRoute: "medicines-deactivate", medicineId: "../unsafe"},
    {__mediflowRoute: "medicines-classification", medicineId: "foo/bar"},
  ];
  for (const query of selectors) {
    const result = makeResponse();
    dispatchAdminRequest({query}, result);
    assert.equal(result.statusCode, 400, JSON.stringify(query));
  }
});
