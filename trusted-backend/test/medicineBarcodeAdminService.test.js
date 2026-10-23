
/**
 * Covers barcode normalization, exact identifier ownership, idempotent linking, and permission checks.
 */
"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {createMedicineBarcodeAdminService, barcodeDocumentId} =
  require("../lib/medicineBarcodeAdminService");
const {ApplicationHttpError} = require("../lib/applicationAuthorization");


function harness(seed = {}, denied = false) {
  const documents = new Map(Object.entries(seed));
  const writes = [];
  const permissions = [];
  const audits = [];
  let seq = 0;
  const db = {
    collection(collection) {
      return {
        doc(id) { return {id: id || `auto-${++seq}`, path:
          `${collection}/${id || `auto-${seq}`}`}; },
        where(field, operator, value) {
          assert.equal(operator, "==");
          return {limit(max) { return {collection, field, value, max}; }};
        },
      };
    },

    async runTransaction(fn) {
      const pending = [];
      const tx = {
        async get(ref) {
          if (ref.collection) {
            return {docs: [...documents.entries()]
                .filter(([path, value]) => path.startsWith(ref.collection + "/") &&
                  value[ref.field] === ref.value)
                .slice(0, ref.max).map(([path]) => ({id: path.split("/")[1]}))};
          }
          const data = documents.get(ref.path);
          return {exists: data !== undefined, data: () => data};
        },
        create(ref, value) {pending.push({op: "create", ref, value});},
        delete(ref) {pending.push({op: "delete", ref});},
      };
      const result = await fn(tx);
      for (const {op, ref, value} of pending) {
        if (op === "create") {
          assert.equal(documents.has(ref.path), false);
          documents.set(ref.path, value);
        } else documents.delete(ref.path);
        writes.push({op, path: ref.path});
      }
      return result;
    },
  };
  const service = createMedicineBarcodeAdminService({

    async requireApplicationPermission(uid, permission) {
      permissions.push({uid, permission});
      if (denied) throw new ApplicationHttpError(403, "PERMISSION_DENIED", "Denied.");
      return {db, uid};
    },

    writeAuditRecord(tx, database, value) {audits.push(value);},
    serverTimestamp: () => "SERVER_TIME",
  });
  return {documents, writes, permissions, audits, service};
}


async function fails(promise, code, status) {
  await assert.rejects(promise, (error) => error.code === code &&
      error.statusCode === status, `${code}/${status}`);
}

const med = {"medicines/m1": {medicineId: "m1", medicineCode: "MED001",
  isActive: true}};

const key = (code) => `medicineBarcodes/${barcodeDocumentId(code)}`;

test("links exact barcode with leading zeroes and audit", async () => {
  const h = harness(med);
  const out = await h.service.linkBarcode("uid", "m1", {barcode: "00123450"});
  assert.deepEqual(out, {changed: true, barcode: "00123450", medicineId: "m1"});
  assert.equal(h.documents.get(key("00123450")).medicineId, "m1");
  assert.equal(h.permissions[0].permission, "UPDATE_MEDICINE");
  assert.equal(h.audits[0].actionType, "BARCODE_LINKED");
});

test("same barcode assigned twice to same medicine is idempotent", async () => {
  const h = harness(med);
  await h.service.linkBarcode("uid", "m1", {barcode: "ABC"});
  const out = await h.service.linkBarcode("uid", "m1", {barcode: "ABC"});
  assert.equal(out.changed, false);
  assert.equal(h.audits.length, 1);
  assert.equal(h.writes.filter((x) => x.path === key("ABC")).length, 1);
});

test("barcode cannot be stolen across medicines", async () => {
  const h = harness({...med, "medicines/m2": {medicineId: "m2", isActive: true}});
  await h.service.linkBarcode("uid", "m1", {barcode: "A2"});
  await fails(h.service.linkBarcode("uid", "m2", {barcode: "A2"}),
      "BARCODE_IN_USE", 409);
  assert.equal(h.documents.get(key("A2")).medicineId, "m1");
});

test("rejects registered internal code claim belonging to another medicine", async () => {
  const h = harness({...med, [`medicineCodeClaims/${barcodeDocumentId("INTERNAL")}`]:
    {medicineId: "other", medicineCode: "INTERNAL"}});
  await fails(h.service.linkBarcode("uid", "m1", {barcode: "INTERNAL"}),
      "MEDICINE_CODE_IN_USE", 409);
});

test("rejects legacy internal medicine code without a claim", async () => {
  const h = harness({...med, "medicines/m2": {medicineId: "m2",
    medicineCode: "LEGACY", isActive: true}});
  await fails(h.service.linkBarcode("uid", "m1", {barcode: "legacy"}),
      "MEDICINE_CODE_IN_USE", 409);
});

test("cannot link to inactive or non-cloud medicine", async () => {
  const h = harness({"medicines/m1": {isActive: false}});
  await fails(h.service.linkBarcode("uid", "m1", {barcode: "X"}),
      "MEDICINE_INACTIVE", 409);
  await fails(h.service.linkBarcode("uid", "missing", {barcode: "X"}),
      "MEDICINE_NOT_FOUND", 404);
});

test("requires current permission before writing", async () => {
  const h = harness(med, true);
  await fails(h.service.linkBarcode("uid", "m1", {barcode: "X"}),
      "PERMISSION_DENIED", 403);
  assert.equal(h.writes.length, 0);
});

test("unlinks only from correct medicine and is idempotent", async () => {
  const h = harness({...med, "medicines/m2": {isActive: true}});
  await h.service.linkBarcode("uid", "m1", {barcode: "A"});
  await fails(h.service.unlinkBarcode("uid", "m2", {barcode: "A"}),
      "BARCODE_IN_USE", 409);
  assert.equal((await h.service.unlinkBarcode("uid", "m1", {barcode: "A"})).changed, true);
  assert.equal(h.documents.has(key("A")), false);
  assert.equal((await h.service.unlinkBarcode("uid", "m1", {barcode: "A"})).changed, false);
  assert.equal(h.audits.length, 2);
});

test("rejects invalid barcode values and unexpected fields", async () => {
  const h = harness(med);
  for (const code of ["", "http://url", "javascript:alert(1)", "é",
    "a".repeat(129), "a\nfoo"]) {
    await fails(h.service.linkBarcode("uid", "m1", {barcode: code}),
        "INVALID_BARCODE", 400);
  }
  await fails(h.service.linkBarcode("uid", "m1", {barcode: "A", admin: true}),
      "INVALID_REQUEST_BODY", 400);
  assert.equal(h.writes.length, 0);
});

test("accepts group separator and preserves case for exact scanner lookup", async () => {
  const h = harness(med);
  await h.service.linkBarcode("uid", "m1", {barcode: "01\u001dABC"});
  assert.equal(h.documents.get(key("01\u001dABC")).barcode, "01\u001dABC");
  assert.notEqual(key("Abc"), key("ABC"));
});
