
"use strict";

const {createHash} = require("node:crypto");
const {FieldValue} = require("firebase-admin/firestore");
const {ApplicationHttpError, requireApplicationPermission} =
  require("./applicationAuthorization");
const {writeAuditRecord} = require("./audit");

function fail(status, code, message) {
  throw new ApplicationHttpError(status, code, message);
}


function requireMedicineId(value) {
  if (typeof value !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(value)) {
    fail(400, "INVALID_MEDICINE_ID", "Invalid medicine ID.");
  }
  return value;
}

function requireBarcode(value) {
  if (typeof value !== "string") {
    fail(400, "INVALID_BARCODE", "A barcode is required.");
  }
  const code = value.trim();
  if (code.length < 1 || code.length > 128) {
    fail(400, "INVALID_BARCODE", "Barcode must be 1–128 characters.");
  }
  for (let i = 0; i < code.length; ++i) {
    const point = code.charCodeAt(i);
    if ((point < 32 && point !== 29) || point > 126) {
      fail(400, "INVALID_BARCODE", "Barcode contains unsupported characters.");
    }
  }
  if (/^(https?:|javascript:|mailto:)/i.test(code)) {
    fail(400, "INVALID_BARCODE", "Use the product identifier, not a web link.");
  }
  return code;
}

function barcodeDocumentId(barcode) {
  return createHash("sha256").update(barcode, "utf8").digest("hex");
}


function requireBody(body) {
  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body).some((key) => key !== "barcode")) {
    fail(400, "INVALID_REQUEST_BODY", "Only a barcode field is supported.");
  }
  return requireBarcode(body.barcode);
}


function createMedicineBarcodeAdminService(overrides = {}) {
  const authorize = overrides.requireApplicationPermission ||
    requireApplicationPermission;
  const auditWriter = overrides.writeAuditRecord || writeAuditRecord;
  const timestamp = overrides.serverTimestamp ||
    (() => FieldValue.serverTimestamp());


  async function mutate(uid, medicineId, body, linking) {
    const context = await authorize(uid, "UPDATE_MEDICINE");
    const id = requireMedicineId(medicineId);
    const barcode = requireBody(body);
    const db = context.db;
    const medicineRef = db.collection("medicines").doc(id);
    const barcodeRef = db.collection("medicineBarcodes")
        .doc(barcodeDocumentId(barcode));
    const code = barcode.toUpperCase();
    const claimRef = db.collection("medicineCodeClaims")
        .doc(barcodeDocumentId(code));
    const matchingCodes = db.collection("medicines")
        .where("medicineCode", "==", code).limit(3);
    const auditId = db.collection("auditLogs").doc().id;

    return db.runTransaction(async (transaction) => {
      // All reads precede all writes: Firestore transaction requirement.
      const medicineSnap = await transaction.get(medicineRef);
      const existingBarcode = await transaction.get(barcodeRef);
      const codeClaim = linking ? await transaction.get(claimRef) : null;
      const legacyCodes = linking ? await transaction.get(matchingCodes) : null;

      if (!medicineSnap.exists) {
        fail(404, "MEDICINE_NOT_FOUND", "Medicine does not exist in the cloud.");
      }
      const medicine = medicineSnap.data() || {};
      if (linking && medicine.isActive !== true) {
        fail(409, "MEDICINE_INACTIVE", "Only active medicines can be linked.");
      }
      const previous = existingBarcode.exists ? existingBarcode.data() || {} : null;
      if (previous && (previous.barcode !== barcode ||
          typeof previous.medicineId !== "string")) {
        fail(409, "BARCODE_RECORD_CONFLICT", "Stored barcode record is inconsistent.");
      }
      if (previous && previous.medicineId !== id) {
        fail(409, "BARCODE_IN_USE", "Barcode is assigned to another medicine.");
      }
      if (linking) {
        if (codeClaim && codeClaim.exists &&
            (codeClaim.data() || {}).medicineId !== id) {
          fail(409, "MEDICINE_CODE_IN_USE", "Barcode is another medicine's internal code.");
        }
        if (legacyCodes.docs.some((doc) => doc.id !== id)) {
          fail(409, "MEDICINE_CODE_IN_USE", "Barcode is another medicine's internal code.");
        }
        if (previous) {
          return {changed: false, barcode, medicineId: id};
        }
        transaction.create(barcodeRef, {barcode, medicineId: id,
          createdAt: timestamp(), updatedAt: timestamp()});
      } else {
        if (!previous) {
          return {changed: false, barcode, medicineId: id};
        }
        transaction.delete(barcodeRef);
      }
      auditWriter(transaction, db, {
        auditId, actionType: linking ? "BARCODE_LINKED" : "BARCODE_UNLINKED",
        entityType: "MEDICINE_BARCODE", entityId: barcodeDocumentId(barcode),
        performedBy: uid, details: {barcode, medicineId: id},
      });
      return {changed: true, barcode, medicineId: id};
    });
  }

  return {
    linkBarcode: (uid, medicineId, body) => mutate(uid, medicineId, body, true),
    unlinkBarcode: (uid, medicineId, body) => mutate(uid, medicineId, body, false),
  };
}

const service = createMedicineBarcodeAdminService();
module.exports = {createMedicineBarcodeAdminService,
  linkBarcode: service.linkBarcode,
  unlinkBarcode: service.unlinkBarcode,
  requireBarcode, barcodeDocumentId};
