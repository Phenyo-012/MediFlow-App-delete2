
/**
 * Exercises concurrent receipt claims, legacy duplicate detection, and explicit existing-batch top-ups.
 */
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const Module = require("node:module");


let activeDb;
const processorPath = require.resolve("../lib/inventoryProcessor");
const originalLoad = Module._load;
Module._load = function(request, parent, isMain) {
  if (parent && parent.filename === processorPath) {
    if (request === "firebase-admin/firestore") {
      return {
        FieldValue: {serverTimestamp: () => "SERVER_NOW"},
        Timestamp: {fromMillis: (value) => value},
      };
    }
    if (request === "./firebaseAdmin") {
      return {getAdminFirestore: () => activeDb};
    }
    if (request === "./audit") {
      return {
        AUDIT_ACTIONS: {},
        inventoryAuditId: () => "audit-placeholder",
        writeAuditRecord: () => {},
      };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};
let processInventoryOperation;
try {
  ({processInventoryOperation} = require("../lib/inventoryProcessor"));
} finally {
  Module._load = originalLoad;
}


function fakeFirestore(seed = {}) {
  const documents = new Map(Object.entries(seed));
  const versions = new Map();
  for (const path of documents.keys()) versions.set(path, 1);
  const db = {
    collection(name) {
      return {
        doc(id) {return {path: `${name}/${id}`};},
        where(field, op, expected) {
          assert.equal(op, "==");
          return {queryCollection: name, field, expected};
        },
      };
    },

    async runTransaction(action) {
      for (let retry = 0; retry < 10; retry++) {
        const reads = new Map();
        const changes = [];
        const transaction = {
          async get(ref) {
            await Promise.resolve(); // Let concurrent calls interleave.
            if (ref.queryCollection) {
              return {docs: [...documents.entries()]
                  .filter(([path, row]) => path.startsWith(`${ref.queryCollection}/`) &&
                      row[ref.field] === ref.expected)
                  .map(([path, row]) => ({id: path.split("/")[1],
                    data: () => structuredClone(row)}))};
            }
            const path = ref.path;
            const data = documents.get(path);
            reads.set(path, versions.get(path) || 0);
            return {exists: data !== undefined,
              data: () => structuredClone(data)};
          },
          create(ref, data) {changes.push({kind: "create", path: ref.path, data});},
          update(ref, data) {changes.push({kind: "update", path: ref.path, data});},
        };
        const result = await action(transaction);
        const stale = [...reads].some(([path, version]) =>
          (versions.get(path) || 0) !== version);
        if (stale) continue;
        for (const change of changes) {
          if (change.kind === "create") {
            assert.equal(documents.has(change.path), false,
                `Create collided: ${change.path}`);
            documents.set(change.path, change.data);
          } else {
            assert.ok(documents.has(change.path),
                `Missing update: ${change.path}`);
            documents.set(change.path,
                {...documents.get(change.path), ...change.data});
          }
          versions.set(change.path, (versions.get(change.path) || 0) + 1);
        }
        return result;
      }
      throw new Error("Fake transaction retry budget exceeded");
    },
  };
  return {db, documents};
}


function operation(id, medicineId, batchId, batchNumber, quantity = 12) {
  const now = Date.now();
  return {
    operationId: id,
    operationType: "RECEIPT",
    medicineId,
    requestedQuantity: quantity,
    performedBy: "user-1",
    deviceId: `device-${id}`,
    clientCreatedAt: now,
    status: "SUBMITTED",
    allocations: [{batchId, batchNumber, quantity,
      receivedDate: now - 86400000,
      expiryDate: now + 365 * 86400000}],
  };
}


function setup(operations, extra = {}) {
  const seed = {
    "users/user-1": {isActive: true, permissions: ["RECEIVE_STOCK"]},
    "medicines/med-1": {medicineId: "med-1", isActive: true},
    "medicines/med-2": {medicineId: "med-2", isActive: true},
    ...extra,
  };
  for (const op of operations) seed[`inventoryOperations/${op.operationId}`] = op;
  const fake = fakeFirestore(seed);
  activeDb = fake.db;
  return fake.documents;
}


function rows(documents, collection) {
  return [...documents.entries()]
      .filter(([path]) => path.startsWith(`${collection}/`))
      .map(([, data]) => data);
}

test("competing receipts with different UUIDs reserve only one batch", async () => {
  const docs = setup([
    operation("op-1", "med-1", "uuid-a", " Lot-001 ", 10),
    operation("op-2", "med-1", "uuid-b", "LOT-001", 20),
  ]);
  const outcomes = await Promise.all([
    processInventoryOperation("op-1", "user-1"),
    processInventoryOperation("op-2", "user-1"),
  ]);
  assert.deepEqual(outcomes.map((r) => r.status).sort(),
      ["CONFIRMED", "CONFLICT"]);
  assert.equal(rows(docs, "batches").length, 1);
  assert.equal(rows(docs, "medicineBatchClaims").length, 1);
  assert.equal(rows(docs, "inventoryTransactions").length, 1);
  assert.ok([10, 20].includes(rows(docs, "batches")[0].quantityAvailable));
  const conflict = rows(docs, "inventoryOperations")
      .find((row) => row.status === "CONFLICT");
  assert.equal(conflict.resultCode, "BATCH_ALREADY_EXISTS");
});

test("legacy batch without a claim prevents duplicate creation", async () => {
  const docs = setup([
    operation("op-1", "med-1", "new-uuid", "LOT-001"),
  ], {
    "batches/old-uuid": {batchId: "old-uuid", medicineId: "med-1",
      batchNumber: " lot-001 " , quantityAvailable: 18},
  });
  const result = await processInventoryOperation("op-1", "user-1");
  assert.equal(result.status, "CONFLICT");
  assert.equal(result.resultCode, "BATCH_ALREADY_EXISTS");
  assert.equal(rows(docs, "batches").length, 1);
  assert.equal(rows(docs, "inventoryTransactions").length, 0);
  assert.equal(rows(docs, "medicineBatchClaims").length, 0);
});

test("distinct batch numbers for one medicine remain permitted", async () => {
  const docs = setup([
    operation("op-1", "med-1", "uuid-a", "LOT-001"),
    operation("op-2", "med-1", "uuid-b", "LOT-002"),
  ]);
  assert.equal((await processInventoryOperation("op-1", "user-1")).status,
      "CONFIRMED");
  assert.equal((await processInventoryOperation("op-2", "user-1")).status,
      "CONFIRMED");
  assert.equal(rows(docs, "batches").length, 2);
  assert.equal(rows(docs, "medicineBatchClaims").length, 2);
});

test("same batch number on different medicines is permitted", async () => {
  const docs = setup([
    operation("op-1", "med-1", "uuid-a", "LOT-001"),
    operation("op-2", "med-2", "uuid-b", "LOT-001"),
  ]);
  assert.equal((await processInventoryOperation("op-1", "user-1")).status,
      "CONFIRMED");
  assert.equal((await processInventoryOperation("op-2", "user-1")).status,
      "CONFIRMED");
  assert.equal(rows(docs, "batches").length, 2);
});

test("retrying an already confirmed operation does not duplicate its batch", async () => {
  const docs = setup([
    operation("op-1", "med-1", "uuid-a", "LOT-001"),
  ]);
  assert.equal((await processInventoryOperation("op-1", "user-1")).status,
      "CONFIRMED");
  const second = await processInventoryOperation("op-1", "user-1");
  assert.equal(second.status, "CONFIRMED");
  assert.equal(second.processedNow, false);
  assert.equal(rows(docs, "inventoryTransactions").length, 1);
  assert.equal(rows(docs, "medicineBatchClaims").length, 1);
});

test("unauthorized receipt cannot claim a batch number", async () => {
  const docs = setup([
    operation("op-1", "med-1", "uuid-a", "LOT-001"),
  ], {"users/user-1": {isActive: true, permissions: []}});
  const result = await processInventoryOperation("op-1", "user-1");
  assert.equal(result.status, "REJECTED");
  assert.equal(rows(docs, "batches").length, 0);
  assert.equal(rows(docs, "medicineBatchClaims").length, 0);
});

test("explicit existing-batch top-up increments only the selected batch", async () => {
  const first = operation("op-new", "med-1", "lot-a", "LOT-A", 100);
  const topup = operation("op-top", "med-1", "lot-a", "LOT-A", 50);
  topup.receiptMode = "EXISTING_BATCH";
  topup.allocations[0].expiryDate = first.allocations[0].expiryDate;
  const docs = setup([first, topup]);
  const created = await processInventoryOperation("op-new", "user-1");
  assert.equal(created.status, "CONFIRMED");
  const topped = await processInventoryOperation("op-top", "user-1");
  assert.equal(topped.status, "CONFIRMED");
  assert.equal(topped.resultCode, "EXISTING_BATCH_RECEIPT_CONFIRMED");
  assert.equal(rows(docs, "batches").length, 1);
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 150);
  assert.equal(rows(docs, "batches")[0].initialQuantity, 150);
  assert.equal(rows(docs, "inventoryTransactions").length, 2);
  assert.equal(rows(docs, "medicineBatchClaims").length, 1);
  const repeated = await processInventoryOperation("op-top", "user-1");
  assert.equal(repeated.processedNow, false);
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 150);
});

test("top-up requires explicit mode; omitted mode still rejects duplicate", async () => {
  const original = operation("op-new", "med-1", "lot-a", "LOT-A", 100);
  const accidental = operation("op-repeat", "med-1", "lot-b", "LOT-A", 50);
  const docs = setup([original, accidental]);
  assert.equal((await processInventoryOperation("op-new", "user-1")).status,
      "CONFIRMED");
  assert.equal((await processInventoryOperation("op-repeat", "user-1")).resultCode,
      "BATCH_ALREADY_EXISTS");
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 100);
});

test("top-up refuses missing batch and cross-medicine batch", async () => {
  const missing = operation("op-missing", "med-1", "lot-a", "LOT-A", 20);
  missing.receiptMode = "EXISTING_BATCH";
  const wrongMedicine = operation("op-wrong", "med-2", "lot-a", "LOT-A", 20);
  wrongMedicine.receiptMode = "EXISTING_BATCH";
  const docs = setup([missing, wrongMedicine], {
    "batches/lot-a": {batchId: "lot-a", medicineId: "med-3",
      batchNumber: "LOT-A", quantityAvailable: 30, initialQuantity: 30,
      status: "ACTIVE", expiryDate: missing.allocations[0].expiryDate},
  });
  assert.equal((await processInventoryOperation("op-missing", "user-1")).status,
      "CONFLICT");
  assert.equal((await processInventoryOperation("op-wrong", "user-1")).status,
      "CONFLICT");
  assert.equal(rows(docs, "inventoryTransactions").length, 0);
});

test("top-up rejects expiry or batch-number mismatches", async () => {
  const invalid = operation("op-wrong", "med-1", "lot-a", "OTHER", 20);
  invalid.receiptMode = "EXISTING_BATCH";
  const docs = setup([invalid], {
    "batches/lot-a": {batchId: "lot-a", medicineId: "med-1",
      batchNumber: "LOT-A", quantityAvailable: 30, initialQuantity: 30,
      status: "ACTIVE", expiryDate: invalid.allocations[0].expiryDate},
  });
  assert.equal((await processInventoryOperation("op-wrong", "user-1")).resultCode,
      "BATCH_DETAILS_CHANGED");
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 30);
});

test("top-up refuses expired batch and insufficient permissions", async () => {
  const expired = operation("op-expired", "med-1", "lot-a", "LOT-A", 20);
  expired.receiptMode = "EXISTING_BATCH";
  const docs = setup([expired], {
    "batches/lot-a": {batchId: "lot-a", medicineId: "med-1",
      batchNumber: "LOT-A", quantityAvailable: 30, initialQuantity: 30,
      status: "EXPIRED", expiryDate: expired.allocations[0].expiryDate},
  });
  assert.equal((await processInventoryOperation("op-expired", "user-1")).resultCode,
      "BATCH_EXPIRED");
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 30);
});

test("two legitimate top-ups on the same batch both count without lost updates", async () => {
  const initial = operation("op-new", "med-1", "batch-1", "LOT-1", 100);
  const a = operation("op-top-a", "med-1", "batch-1", "LOT-1", 20);
  const b = operation("op-top-b", "med-1", "batch-1", "LOT-1", 30);
  for (const op of [a, b]) {
    op.receiptMode = "EXISTING_BATCH";
    op.allocations[0].expiryDate = initial.allocations[0].expiryDate;
  }
  const docs = setup([initial, a, b]);
  assert.equal((await processInventoryOperation("op-new", "user-1")).status,
      "CONFIRMED");
  const results = await Promise.all([
    processInventoryOperation("op-top-a", "user-1"),
    processInventoryOperation("op-top-b", "user-1"),
  ]);
  assert.deepEqual(results.map((r) => r.status), ["CONFIRMED", "CONFIRMED"]);
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 150);
  assert.equal(rows(docs, "batches").length, 1);
  assert.equal(rows(docs, "inventoryTransactions").length, 3);
});

test("top-up cannot exceed supported local stock integer capacity", async () => {
  const op = operation("op-top", "med-1", "batch-1", "LOT-1", 50);
  op.receiptMode = "EXISTING_BATCH";
  const docs = setup([op], {
    "batches/batch-1": {batchId: "batch-1", medicineId: "med-1",
      batchNumber: "LOT-1", quantityAvailable: 2147483640,
      initialQuantity: 2147483640, status: "ACTIVE",
      expiryDate: op.allocations[0].expiryDate},
  });
  assert.equal((await processInventoryOperation("op-top", "user-1")).resultCode,
      "INVALID_STOCK_QUANTITY");
  assert.equal(rows(docs, "batches")[0].quantityAvailable, 2147483640);
});
