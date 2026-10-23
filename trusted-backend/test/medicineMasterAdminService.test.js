/**
 * Covers medicine master writes, normalization, authorization, and rejection of server-owned field changes.
 */

"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {createMedicineMasterAdminService} =
  require("../lib/medicineMasterAdminService");
const {ApplicationHttpError} =
  require("../lib/applicationAuthorization");

const SAMPLE = {
  medicineId: "medicine-1",
  medicineCode: "med-001",
  medicineName: "Test medicine",
  genericName: null,
  category: "Antibiotics",
  strength: "500 mg",
  dosageForm: "Tablet",
  unitOfMeasure: "Tablet",
  reorderLevel: 5,
  targetStockLevel: 20,
};

function fakeDatabase(seed = {}) {
  const documents = new Map(Object.entries(seed));
  const writes = [];
  let sequence = 0;
  const db = {
    collection(name) {
      return {
        doc(givenId) {
          const id = givenId || `auto-${++sequence}`;
          return {id, path: `${name}/${id}`};
        },
        where(field, operator, value) {
          assert.equal(operator, "==");
          return {
            limit(max) {
              return {collection: name, field, value, max};
            },
          };
        },
      };
    },

    async runTransaction(fn) {
      const pending = [];
      const tx = {
        async get(ref) {
          if (ref.collection) {
            const matching = Array.from(documents.entries())
                .filter(([path, data]) => path.startsWith(`${ref.collection}/`) &&
                    data[ref.field] === ref.value)
                .slice(0, ref.max)
                .map(([path, data]) => ({id: path.split("/")[1], data: () => data}));
            return {docs: matching};
          }
          const existing = documents.get(ref.path);
          return {exists: existing !== undefined,
            data: () => existing};
        },
        create(ref, data) {
          pending.push({type: "create", ref, data});
        },
        update(ref, data) {
          pending.push({type: "update", ref, data});
        },
        delete(ref) {
          pending.push({type: "delete", ref});
        },
      };
      const result = await fn(tx);
      for (const operation of pending) {
        const {type, ref, data} = operation;
        if (type === "create") {
          assert.equal(documents.has(ref.path), false);
          documents.set(ref.path, data);
        } else if (type === "update") {
          assert.equal(documents.has(ref.path), true);
          documents.set(ref.path, {...documents.get(ref.path), ...data});
        } else {
          documents.delete(ref.path);
        }
        writes.push(operation);
      }
      return result;
    },
  };
  return {db, documents, writes};
}


function harness(seed = {}, permissionError = false) {
  const fake = fakeDatabase(seed);
  const calls = [];
  const audits = [];
  const service = createMedicineMasterAdminService({

    async requireApplicationPermission(uid, permission) {
      calls.push({uid, permission});
      if (permissionError) {
        throw new ApplicationHttpError(403, "PERMISSION_DENIED", "Denied.");
      }
      return {db: fake.db, uid};
    },

    writeAuditRecord(tx, db, record) {
      audits.push(record);
    },
    serverTimestamp: () => "SERVER_NOW",
  });
  return {...fake, calls, audits, service};
}


async function expectError(promise, code, status) {
  await assert.rejects(promise, (error) => {
    assert.ok(error instanceof ApplicationHttpError);
    assert.equal(error.code, code);
    assert.equal(error.statusCode, status);
    return true;
  });
}

test("creates a cloud medicine and normalises category and code", async () => {
  const h = harness();
  const result = await h.service.createMedicine("user-1", {
    ...SAMPLE, category: " Emergency   Medicines ",
  });
  assert.deepEqual(h.calls, [{uid: "user-1", permission: "CREATE_MEDICINE"}]);
  assert.equal(result.created, true);
  assert.equal(result.syncVersion, 1);
  const row = h.documents.get("medicines/medicine-1");
  assert.equal(row.medicineCode, "MED-001");
  assert.equal(row.category, "Emergency Medicines");
  assert.equal(row.isActive, true);
  assert.equal(row.updatedAt, "SERVER_NOW");
  assert.equal(h.audits.length, 1);
});

test("denies unauthorised creates without writing", async () => {
  const h = harness({}, true);
  await expectError(h.service.createMedicine("user-1", SAMPLE),
      "PERMISSION_DENIED", 403);
  assert.equal(h.writes.length, 0);
});

test("rejects attempts to change stock, lifecycle or sync fields", async () => {
  const h = harness();
  for (const unsafe of ["isActive", "quantityAvailable", "syncVersion",
    "batchId", "updatedAt", "barcode"]) {
    await expectError(h.service.createMedicine("user-1",
        {...SAMPLE, [unsafe]: "forbidden"}),
    "UNSUPPORTED_MEDICINE_FIELD", 400);
  }
  assert.equal(h.writes.length, 0);
});

test("validates target level and category", async () => {
  const h = harness();
  await expectError(h.service.createMedicine("u", {
    ...SAMPLE, targetStockLevel: 1,
  }), "INVALID_STOCK_TARGET", 400);
  await expectError(h.service.createMedicine("u", {
    ...SAMPLE, category: "Other...",
  }), "INVALID_MEDICINE_FIELD", 400);
  assert.equal(h.writes.length, 0);
});

test("detects legacy medicine-code collision without a code claim", async () => {
  const h = harness({"medicines/legacy": {
    medicineId: "legacy", medicineCode: "MED-001",
  }});
  await expectError(h.service.createMedicine("u", SAMPLE),
      "MEDICINE_CODE_IN_USE", 409);
  assert.equal(h.writes.length, 0);
});

test("blocks duplicate creates with the same medicine ID", async () => {
  const h = harness({"medicines/medicine-1": {medicineId: "medicine-1"}});
  await expectError(h.service.createMedicine("u", SAMPLE),
      "MEDICINE_ALREADY_EXISTS", 409);
});

test("updates existing medicine category and preserves unrelated fields", async () => {
  const h = harness({"medicines/medicine-1": {
    ...SAMPLE, medicineCode: "MED-001", isActive: true,
    syncVersion: 4, serverExtension: "keep", category: "Antibiotics",
  }});
 const fields = {...SAMPLE};
 delete fields.medicineId;
  const result = await h.service.updateMedicine("u", "medicine-1", {
    ...fields, expectedSyncVersion: 4,
    category: "Cardiovascular",
  });
  assert.equal(result.changed, true);
  const row = h.documents.get("medicines/medicine-1");
  assert.equal(row.category, "Cardiovascular");
  assert.equal(row.syncVersion, 5);
  assert.equal(row.serverExtension, "keep");
  assert.equal(row.isActive, true);
  assert.equal(h.calls[0].permission, "UPDATE_MEDICINE");
});

test("rejects stale version without changing shared medicine", async () => {
  const h = harness({"medicines/medicine-1": {
    ...SAMPLE, medicineCode: "MED-001", isActive: true, syncVersion: 7,
  }});
 const fields = {...SAMPLE};
 delete fields.medicineId;
  await expectError(h.service.updateMedicine("u", "medicine-1", {
    ...fields, expectedSyncVersion: 6,
  }), "MEDICINE_VERSION_CONFLICT", 409);
  assert.equal(h.writes.length, 0);
});

test("leaves existing records untouched when inactive", async () => {
  const h = harness({"medicines/medicine-1": {
    ...SAMPLE, medicineCode: "MED-001", isActive: false, syncVersion: 2,
  }});
  const fields = {...SAMPLE};
  delete fields.medicineId;
  await expectError(h.service.updateMedicine("u", "medicine-1", {
    ...fields, expectedSyncVersion: 2,
  }), "MEDICINE_INACTIVE", 409);
});

test("repeat identical update is idempotent once claim exists", async () => {
  const h = harness();
  await h.service.createMedicine("u", SAMPLE);
  const fields = {...SAMPLE};
  delete fields.medicineId;
  const result = await h.service.updateMedicine("u", "medicine-1", {
    ...fields, expectedSyncVersion: 1,
  });
  assert.equal(result.changed, false);
  assert.equal(result.syncVersion, 1);
  assert.equal(h.audits.length, 1);
});

test("rejects a code already claimed by another medicine", async () => {
  const h = harness();
  await h.service.createMedicine("u", SAMPLE);
  await expectError(h.service.createMedicine("u", {
    ...SAMPLE, medicineId: "medicine-2",
  }), "MEDICINE_CODE_IN_USE", 409);
  assert.equal(h.documents.has("medicines/medicine-2"), false);
});

test("editing a code releases only the old medicine-owned claim", async () => {
  const h = harness();
  await h.service.createMedicine("u", SAMPLE);
  const previousClaim = h.writes.find((row) =>
    row.type === "create" && row.ref.path.startsWith("medicineCodeClaims/"));
 const fields = {...SAMPLE};
 delete fields.medicineId;
  await h.service.updateMedicine("u", "medicine-1", {
    ...fields, medicineCode: "MED-002", expectedSyncVersion: 1,
  });
  assert.equal(h.documents.has(previousClaim.ref.path), false);
  assert.equal(h.documents.get("medicines/medicine-1").medicineCode, "MED-002");
  assert.equal(h.documents.get("medicines/medicine-1").syncVersion, 2);
});


test("medicine master code cannot claim another medicine's existing barcode", async () => {
  const {createHash} = require("node:crypto");
  const docId = createHash("sha256").update("MED-001").digest("hex");
  const h = harness({[`medicineBarcodes/${docId}`]:
    {barcode: "MED-001", medicineId: "different-medicine"}});
  await expectError(h.service.createMedicine("admin", SAMPLE), "BARCODE_IN_USE", 409);
  assert.equal(h.documents.has("medicines/medicine-1"), false);
});


test("deactivation is authoritative, versioned, audited and preserves data", async () => {
  const original = {...SAMPLE, medicineCode: "MED-001", isActive: true,
    syncVersion: 3, category: "Antibiotics", extraField: "keep"};
  const h = harness({"medicines/medicine-1": original});
  const result = await h.service.deactivateMedicine("admin", "medicine-1",
      {expectedSyncVersion: 3});
  assert.deepEqual(h.calls, [{uid: "admin", permission: "DEACTIVATE_MEDICINE"}]);
  assert.deepEqual(result, {changed: true, medicineId: "medicine-1", syncVersion: 4});
  const row = h.documents.get("medicines/medicine-1");
  assert.equal(row.isActive, false);
  assert.equal(row.syncVersion, 4);
  assert.equal(row.category, "Antibiotics");
  assert.equal(row.extraField, "keep");
  assert.equal(h.writes.length, 1);
  assert.equal(h.audits.length, 1);
  assert.equal(h.audits[0].actionType, "MEDICINE_DEACTIVATED");
});

test("repeat deactivation is idempotent and not re-audited", async () => {
  const h = harness({"medicines/medicine-1": {...SAMPLE,
    isActive: false, syncVersion: 4}});
  const result = await h.service.deactivateMedicine("u", "medicine-1",
      {expectedSyncVersion: 3});
  assert.deepEqual(result, {changed: false, medicineId: "medicine-1", syncVersion: 4});
  assert.equal(h.writes.length, 0);
  assert.equal(h.audits.length, 0);
});

test("deactivation rejects stale versions without writes", async () => {
  const h = harness({"medicines/medicine-1": {...SAMPLE,
    isActive: true, syncVersion: 3}});
  await expectError(h.service.deactivateMedicine("u", "medicine-1",
      {expectedSyncVersion: 2}), "MEDICINE_VERSION_CONFLICT", 409);
  assert.equal(h.writes.length, 0);
  assert.equal(h.audits.length, 0);
});

test("deactivation rejects unauthorised callers and missing cloud medicine", async () => {
  const unauthorised = harness({"medicines/medicine-1": {...SAMPLE,
    isActive: true, syncVersion: 3}}, true);
  await expectError(unauthorised.service.deactivateMedicine("u", "medicine-1",
      {expectedSyncVersion: 3}), "PERMISSION_DENIED", 403);
  assert.equal(unauthorised.writes.length, 0);
  const missing = harness();
  await expectError(missing.service.deactivateMedicine("u", "medicine-1",
      {expectedSyncVersion: 3}), "MEDICINE_NOT_FOUND", 404);
});

test("deactivation rejects unsupported lifecycle payloads", async () => {
  const h = harness({"medicines/medicine-1": {...SAMPLE,
    isActive: true, syncVersion: 3}});
  for (const payload of [{expectedSyncVersion: 3, isActive: false},
    {expectedSyncVersion: -1}, {expectedSyncVersion: 3.5}, {}, null]) {
    await expectError(h.service.deactivateMedicine("u", "medicine-1", payload),
        "INVALID_DEACTIVATION_REQUEST", 400);
  }
  assert.equal(h.writes.length, 0);
});
