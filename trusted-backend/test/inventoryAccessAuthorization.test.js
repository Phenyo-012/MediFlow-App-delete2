/**
 * Checks terminal operation access for active owners and rejects deleted or inconsistent profiles.
 */

"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");


const state = {
  profileExists: true,
  isActive: true,
  storedUid: "test-user",
};
const db = {
  collection(name) {
    return {
      doc(_id) {
        return {
          async get() {
            if (name === "users") {
              return {exists: state.profileExists,
                data: () => ({uid: state.storedUid,
                  isActive: state.isActive, permissions: []})};
            }
            if (name === "inventoryOperations") {
              return {exists: true,
                data: () => ({performedBy: "test-user",
                  status: "CONFIRMED", resultCode: "OK"})};
            }
            return {exists: false};
          },
        };
      },
      where() {
        return {get: async () => ({forEach() {}})};
      },
    };
  },

  runTransaction(callback) {
    return callback({get: (reference) => reference.get()});
  },
};

const firebaseAdminPath = require.resolve("../lib/firebaseAdmin");
require.cache[firebaseAdminPath] = {
  id: firebaseAdminPath,
  filename: firebaseAdminPath,
  loaded: true,
  exports: {getAdminFirestore: () => db},
};

const {processInventoryOperation} = require("../lib/inventoryProcessor");
const {getInventoryReconciliation} =
    require("../lib/inventoryReconciliation");


function resetProfile() {
  state.profileExists = true;
  state.isActive = true;
  state.storedUid = "test-user";
}

test("terminal processor response is still available to an active owner", async () => {
  resetProfile();
  const result = await processInventoryOperation("test-op", "test-user");
  assert.equal(result.status, "CONFIRMED");
});

test("reconciliation still returns an active owner's completed operation", async () => {
  resetProfile();
  const result = await getInventoryReconciliation("test-op", "test-user");
  assert.equal(result.status, "CONFIRMED");
  assert.deepEqual(result.batches, []);
  assert.deepEqual(result.transactions, []);
});

test("inactive owner cannot retrieve reconciliation or terminal result", async () => {
  resetProfile();
  state.isActive = false;
  for (const action of [
    () => processInventoryOperation("test-op", "test-user"),
    () => getInventoryReconciliation("test-op", "test-user"),
  ]) {
    await assert.rejects(action, (error) =>
      error.statusCode === 403 && error.code === "ACCOUNT_INACTIVE");
  }
});

test("deleted and mismatched profiles cannot retrieve terminal result", async () => {
  resetProfile();
  state.profileExists = false;
  await assert.rejects(
      () => processInventoryOperation("test-op", "test-user"),
      (error) => error.statusCode === 403 &&
          error.code === "USER_PROFILE_NOT_FOUND");
  resetProfile();
  state.storedUid = "another-user";
  await assert.rejects(
      () => processInventoryOperation("test-op", "test-user"),
      (error) => error.statusCode === 403 &&
          error.code === "USER_PROFILE_INVALID");
});
