/**
 * Covers guarded TOTP recovery, including stale authentication, replay protection, and partial failure.
 */

"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  createTotpRecoveryService,
} = require("../lib/userTotpRecoveryService");

const UID = "employee-123";
const ACTOR = "manager-456";
const REQUEST_ID = "92b51658-a084-447e-9ba2-119217ce751c";
const NOW = 1760461200000;


function setup({factors = [{factorId: "totp", uid: "factor-1"}],
  missing = false, profileUid = UID, auditCreateError = null,
  failStep = ""} = {}) {
  const calls = [];
  const auditRef = {
    async create(data) {
      calls.push(["audit-create", data]);
      if (auditCreateError) throw auditCreateError;
    },
    async update(data) {
      calls.push(["audit-update", data]);
      if (failStep === "audit-update") throw Error("audit unavailable");
    },
  };
  const db = {
    collection(name) {
      return {
        doc(id) {
          if (name === "auditLogs") {
            calls.push(["audit-id", id]);
            return auditRef;
          }
          assert.equal(name, "users");
          assert.equal(id, UID);
          return {
            async get() {
              calls.push(["profile-read"]);
              return {exists: !missing, data: () =>
                ({uid: profileUid, isActive: true})};
            },
          };
        },
      };
    },
  };
  const auth = {

    async getUser(uid) {
      assert.equal(uid, UID);
      calls.push(["auth-get"]);
      return {multiFactor: {enrolledFactors: factors}};
    },

    async updateUser(uid, update) {
      assert.equal(uid, UID);
      assert.deepEqual(update, {multiFactor: {enrolledFactors: null}});
      calls.push(["auth-update"]);
      if (failStep === "auth-update") throw Error("update failed");
    },

    async revokeRefreshTokens(uid) {
      assert.equal(uid, UID);
      calls.push(["revoke"]);
      if (failStep === "revoke") throw Error("revoke failed");
    },
  };
  const service = createTotpRecoveryService({
    authorize: async (uid, permission) => {
      calls.push(["authorize", uid, permission]);
      return {db};
    },
    getAuthClient: () => auth,
    now: () => NOW,
    serverTimestamp: () => "SERVER_TIME",
  });
  const payload = {requestId: REQUEST_ID, reason: "Lost authenticator device"};
  return {service, calls, payload};
}

async function errorCode(promise, expected) {
  await assert.rejects(promise, (error) => error.code === expected);
}

test("authorized staff can check enrolled TOTP status without exposing secrets", async () => {
  const {service, calls} = setup();
  assert.deepEqual(await service.getTotpStatus(ACTOR, UID), {
    uid: UID, totpEnrolled: true,
  });
  assert.equal(calls[0][2], "VIEW_USERS");
});

test("audits before factor removal then revokes tokens", async () => {
  const {service, calls, payload} = setup();
  const result = await service.resetTotp(ACTOR, UID, payload, NOW / 1000);
  assert.deepEqual(result, {uid: UID, reset: true, requestId: REQUEST_ID});
  assert.equal(calls[0][2], "MANAGE_USER_PERMISSIONS");
  const sequence = calls.map((x) => x[0]);
  assert.ok(sequence.indexOf("audit-create") < sequence.indexOf("auth-update"));
  assert.ok(sequence.indexOf("auth-update") < sequence.indexOf("revoke"));
  assert.ok(sequence.indexOf("revoke") < sequence.indexOf("audit-update"));
  assert.equal(calls.find((x) => x[0] === "audit-create")[1].status,
      "REQUESTED");
  assert.equal(calls.at(-1)[1].status, "COMPLETED");
});

test("rejects self recovery before any external mutation", async () => {
  const {service, calls, payload} = setup();
  await errorCode(service.resetTotp(UID, UID, payload, NOW / 1000),
      "SELF_RECOVERY_NOT_ALLOWED");
  assert.equal(calls.length, 0);
});

test("denies stale sign-in before audit or changes", async () => {
  const {service, calls, payload} = setup();
  await errorCode(service.resetTotp(ACTOR, UID, payload, NOW / 1000 - 3600),
      "RECENT_SIGN_IN_REQUIRED");
  assert.equal(calls.some((x) => x[0] === "audit-create"), false);
});

test("rejects missing user, no enrolled totp, and mixed factors", async () => {
  for (const args of [
    {missing: true, code: "USER_NOT_FOUND"},
    {factors: [], code: "TOTP_NOT_ENROLLED"},
    {factors: [{factorId: "totp"}, {factorId: "phone"}],
      code: "OTHER_FACTORS_PRESENT"},
  ]) {
    const {service, calls, payload} = setup(args);
    await errorCode(service.resetTotp(ACTOR, UID, payload, NOW / 1000),
        args.code);
    assert.equal(calls.some((x) => x[0] === "auth-update"), false);
  }
});

test("replayed requestId is rejected before Firebase Auth mutation", async () => {
  const {service, calls, payload} = setup({auditCreateError: {code: 6}});
  await errorCode(service.resetTotp(ACTOR, UID, payload, NOW / 1000),
      "DUPLICATE_RECOVERY_REQUEST");
  assert.equal(calls.some((x) => x[0] === "auth-update"), false);
});

test("partial Firebase Auth failure marks audit for manual review", async () => {
  for (const failStep of ["auth-update", "revoke"]) {
    const {service, calls, payload} = setup({failStep});
    await errorCode(service.resetTotp(ACTOR, UID, payload, NOW / 1000),
        "RECOVERY_REQUIRES_REVIEW");
    assert.equal(calls.at(-1)[1].status, "REQUIRES_REVIEW");
  }
});

test("rejects malformed recovery request and reason", async () => {
  const {service, payload} = setup();
  await errorCode(service.resetTotp(ACTOR, UID,
      {...payload, requestId: "guess"}, NOW / 1000), "INVALID_REQUEST_ID");
  await errorCode(service.resetTotp(ACTOR, UID,
      {...payload, reason: "bad"}, NOW / 1000), "INVALID_REASON");
});
