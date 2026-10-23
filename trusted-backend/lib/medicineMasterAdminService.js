
/**
 * Trusted service for creating, version-checking, editing, and deactivating shared medicine master
 * records. Every mutation checks the current application permission, validates a strict field
 * allowlist, and runs its collision/version checks with the medicine and code-claim writes in a
 * Firestore transaction. Audit records are written in that same transaction. Medicine codes are
 * normalized to uppercase and cannot collide with another medicine or an existing barcode mapping;
 * update requests carry the version observed by the client so concurrent edits fail with a conflict.
 */
"use strict";

const {createHash} = require("node:crypto");
const {FieldValue} = require("firebase-admin/firestore");
const {
  requireApplicationPermission,
  ApplicationHttpError,
} = require("./applicationAuthorization");
const {writeAuditRecord, AUDIT_ACTIONS} = require("./audit");

const MAX_ANDROID_INT = 2147483647;
const MASTER_FIELDS = [
  "medicineCode", "medicineName", "genericName", "category",
  "strength", "dosageForm", "unitOfMeasure", "reorderLevel",
  "targetStockLevel",
];


function fail(status, code, message) {
  throw new ApplicationHttpError(status, code, message);
}


function requireMedicineId(value) {
  if (typeof value !== "string" ||
      !/^[a-zA-Z0-9_-]{1,128}$/.test(value)) {
    fail(400, "INVALID_MEDICINE_ID", "Invalid medicine ID.");
  }
  return value;
}


function requireText(value, field) {
  if (typeof value !== "string") {
    fail(400, "INVALID_MEDICINE_FIELD", `${field} must be text.`);
  }
  const clean = value.trim();
  if (!clean || clean.length > 250) {
    fail(400, "INVALID_MEDICINE_FIELD", `${field} must be 1–250 characters.`);
  }
  return clean;
}


function optionalText(value, field) {
  if (value === null || value === undefined) return null;
  if (typeof value !== "string" || value.trim().length > 250) {
    fail(400, "INVALID_MEDICINE_FIELD", `${field} must be text or null.`);
  }
  return value.trim() || null;
}

function requireStockLevel(value, field) {
  if (!Number.isInteger(value) || value < 0 || value > MAX_ANDROID_INT) {
    fail(400, "INVALID_MEDICINE_FIELD", `${field} must be a nonnegative integer.`);
  }
  return value;
}


/**
 * Validates the writable medicine fields and rejects unknown keys, including attempts to set
 * server-managed identity, lifecycle, timestamps, or version data. Update requests may carry only
 * the expected version; create requests must provide the medicine ID separately.
 *
 * @param {object} body request payload
 * @param {boolean} editing whether this is an update rather than creation
 * @return {object} normalized medicine master fields
 * @throws {ApplicationHttpError} when a field is unsupported or invalid
 */
function validatePayload(body, editing) {
  if (body === null || typeof body !== "object" || Array.isArray(body)) {
    fail(400, "INVALID_REQUEST_BODY", "A JSON object is required.");
  }
  const permitted = new Set([
    ...MASTER_FIELDS,
    ...(editing ? ["expectedSyncVersion"] : ["medicineId"]),
  ]);
  for (const key of Object.keys(body)) {
    if (!permitted.has(key)) {
      fail(400, "UNSUPPORTED_MEDICINE_FIELD", `Cannot change ${key}.`);
    }
  }
  const category = requireText(body.category, "category")
      .replace(/\s+/g, " ");
  if (category.toLowerCase() === "other...") {
    fail(400, "INVALID_MEDICINE_FIELD", "Select an actual category.");
  }
  const code = requireText(body.medicineCode, "medicineCode")
      .toUpperCase();
  if (code.length > 250) {
    fail(400, "INVALID_MEDICINE_FIELD", "Medicine code is too long.");
  }
  const fields = {
    medicineCode: code,
    medicineName: requireText(body.medicineName, "medicineName"),
    genericName: optionalText(body.genericName, "genericName"),
    category,
    strength: optionalText(body.strength, "strength"),
    dosageForm: requireText(body.dosageForm, "dosageForm"),
    unitOfMeasure: requireText(body.unitOfMeasure, "unitOfMeasure"),
    reorderLevel: requireStockLevel(body.reorderLevel, "reorderLevel"),
    targetStockLevel: requireStockLevel(body.targetStockLevel, "targetStockLevel"),
  };
  if (fields.targetStockLevel < fields.reorderLevel) {
    fail(400, "INVALID_STOCK_TARGET", "Target must be at least the reorder level.");
  }
  if (editing) {
    if (!Number.isSafeInteger(body.expectedSyncVersion) ||
        body.expectedSyncVersion < 0) {
      fail(400, "SYNC_VERSION_REQUIRED", "Expected sync version is required.");
    }
  } else {
    requireMedicineId(body.medicineId);
  }
  return fields;
}


function codeClaimId(code) {
  return createHash("sha256").update(code, "utf8").digest("hex");
}


function matchesAll(previous, requested) {
  return MASTER_FIELDS.every((field) =>
    (previous[field] === undefined ? null : previous[field]) ===
    requested[field]);
}


/**
 * Creates the trusted master-data operations with injectable permission, audit, and timestamp
 * providers. Production defaults use Firebase Admin services; injected dependencies support
 * isolated tests without weakening the checks performed by the returned operations.
 *
 * @param {object} overrides optional trusted service providers
 * @return {{createMedicine: Function, updateMedicine: Function, deactivateMedicine: Function}}
 */
function createMedicineMasterAdminService(overrides = {}) {
  const authorize = overrides.requireApplicationPermission ||
      requireApplicationPermission;
  const auditWriter = overrides.writeAuditRecord || writeAuditRecord;
  const timestamp = overrides.serverTimestamp ||
      (() => FieldValue.serverTimestamp());


  /**
   * Applies create or update after permission and payload validation. The transaction checks the
   * existing sync version, medicine code claims, barcode collisions, and legacy exact-code records
   * before writing; matching writes and the audit event commit atomically. A no-op update returns
   * the existing version unless a missing code claim must be repaired.
   */
  async function mutate(uid, medicineId, body, editing) {
    const context = await authorize(
        uid, editing ? "UPDATE_MEDICINE" : "CREATE_MEDICINE");
    const id = requireMedicineId(medicineId);
    const data = validatePayload(body, editing);
    if (!editing && body.medicineId !== id) {
      fail(400, "MEDICINE_ID_MISMATCH", "Medicine IDs must match.");
    }
    const db = context.db;
    const medicineRef = db.collection("medicines").doc(id);
    const claims = db.collection("medicineCodeClaims");
    const newClaimRef = claims.doc(codeClaimId(data.medicineCode));
    const barcodeClaimRef = db.collection("medicineBarcodes")
        .doc(codeClaimId(data.medicineCode));
    const matchingCodeQuery = db.collection("medicines")
        .where("medicineCode", "==", data.medicineCode).limit(3);
    const auditId = db.collection("auditLogs").doc().id;

    return db.runTransaction(async (transaction) => {
      // IMPORTANT: all authoritative reads must precede transaction writes.
      const existingSnapshot = await transaction.get(medicineRef);
      const previous = existingSnapshot.exists ?
        existingSnapshot.data() || {} : null;
      if (editing && previous === null) {
        fail(404, "MEDICINE_NOT_FOUND", "Medicine was not found.");
      }
      if (!editing && previous !== null) {
        fail(409, "MEDICINE_ALREADY_EXISTS", "Medicine ID already exists.");
      }
      if (editing && previous.isActive !== true) {
        fail(409, "MEDICINE_INACTIVE", "Inactive medicines cannot be edited.");
      }
      if (editing && previous.medicineId !== undefined &&
          previous.medicineId !== id) {
        fail(409, "MEDICINE_ID_MISMATCH", "Stored medicine ID is inconsistent.");
      }
      const oldVersion = previous && Number.isSafeInteger(previous.syncVersion) ?
        previous.syncVersion : 0;
      if (editing && oldVersion !== body.expectedSyncVersion) {
        fail(409, "MEDICINE_VERSION_CONFLICT", "Medicine changed on another device.");
      }
      if (oldVersion >= Number.MAX_SAFE_INTEGER) {
        fail(409, "MEDICINE_VERSION_CONFLICT", "Medicine version limit reached.");
      }
      const oldCode = previous && typeof previous.medicineCode === "string" ?
        previous.medicineCode.trim().toUpperCase() : null;
      const oldClaimRef = oldCode && oldCode !== data.medicineCode ?
        claims.doc(codeClaimId(oldCode)) : null;
      const barcodeClaim = await transaction.get(barcodeClaimRef);
      const newClaim = await transaction.get(newClaimRef);
      const oldClaim = oldClaimRef ? await transaction.get(oldClaimRef) : null;
      const matching = await transaction.get(matchingCodeQuery);
      if (barcodeClaim.exists &&
          (barcodeClaim.data() || {}).medicineId !== id) {
        fail(409, "BARCODE_IN_USE", "Medicine code is assigned as another medicine barcode.");
      }
      if (newClaim.exists) {
        const registered = newClaim.data() || {};
        if (registered.medicineId !== id) {
          fail(409, "MEDICINE_CODE_IN_USE", "Medicine code is already assigned.");
        }
        if (registered.medicineCode !== data.medicineCode) {
          fail(409, "MEDICINE_CODE_CONFLICT", "Medicine code claim is inconsistent.");
        }
      }
      if (oldClaim && oldClaim.exists &&
          (oldClaim.data() || {}).medicineId !== id) {
        fail(409, "MEDICINE_CODE_CONFLICT", "Existing code claim is inconsistent.");
      }
      // Covers exact-case legacy medicines created before code claims existed.
      if (matching.docs.some((doc) => doc.id !== id)) {
        fail(409, "MEDICINE_CODE_IN_USE", "Medicine code is already assigned.");
      }

      const claimMissing = !newClaim.exists;
      const identical = editing && matchesAll(previous, data);
      if (identical && !claimMissing) {
        return {changed: false, created: false, medicineId: id,
          syncVersion: oldVersion};
      }
      const nextVersion = oldVersion + 1;
      const stamp = timestamp();
      if (claimMissing) {
        transaction.create(newClaimRef, {medicineId: id,
          medicineCode: data.medicineCode, createdAt: stamp});
      }
      if (oldClaim && oldClaim.exists) {
        transaction.delete(oldClaimRef);
      }
      if (editing) {

        transaction.update(medicineRef, {...data,
          updatedAt: stamp, syncVersion: nextVersion});
      } else {
        transaction.create(medicineRef, {...data, medicineId: id,
          isActive: true, updatedAt: stamp, syncVersion: nextVersion});
      }
      auditWriter(transaction, db, {
        auditId,
        actionType: editing ? AUDIT_ACTIONS.MEDICINE_UPDATED :
          AUDIT_ACTIONS.MEDICINE_CREATED,
        entityType: "MEDICINE", entityId: id, performedBy: uid,
        details: {medicineId: id, medicineCode: data.medicineCode,
          category: data.category, previousCode: oldCode,
          previousCategory: previous ? previous.category || null : null,
          syncVersion: nextVersion},
      });
      return {changed: true, created: !editing, medicineId: id,
        syncVersion: nextVersion};
    });
  }


  /**
   * Marks an active medicine inactive only when the caller's expected sync version still matches.
   * Deactivation is idempotent for an already inactive record, but a stale version or malformed
   * lifecycle state is rejected; the status change and audit event share one Firestore transaction.
   */
  async function deactivate(uid, medicineId, body) {
    const context = await authorize(uid, "DEACTIVATE_MEDICINE");
    const id = requireMedicineId(medicineId);
    if (!body || typeof body !== "object" || Array.isArray(body) ||
        Object.keys(body).length !== 1 ||
        !Number.isSafeInteger(body.expectedSyncVersion) ||
        body.expectedSyncVersion < 0) {
      fail(400, "INVALID_DEACTIVATION_REQUEST",
          "Only a nonnegative expectedSyncVersion is supported.");
    }
    const db = context.db;
    const medicineRef = db.collection("medicines").doc(id);
    const auditId = db.collection("auditLogs").doc().id;
    return db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(medicineRef);
      if (!snapshot.exists) {
        fail(404, "MEDICINE_NOT_FOUND", "Medicine was not found.");
      }
      const previous = snapshot.data() || {};
      if (previous.medicineId !== undefined && previous.medicineId !== id) {
        fail(409, "MEDICINE_ID_MISMATCH", "Stored medicine ID is inconsistent.");
      }
      const version = Number.isSafeInteger(previous.syncVersion) &&
          previous.syncVersion >= 0 ? previous.syncVersion : 0;
      if (previous.isActive === false) {
        return {changed: false, medicineId: id, syncVersion: version};
      }
      if (previous.isActive !== true) {
        fail(409, "INVALID_MEDICINE_STATE", "Stored lifecycle state is invalid.");
      }
      if (version !== body.expectedSyncVersion ||
          version >= Number.MAX_SAFE_INTEGER) {
        fail(409, "MEDICINE_VERSION_CONFLICT", "Medicine changed on another device.");
      }
      const nextVersion = version + 1;
      const stamp = timestamp();
      transaction.update(medicineRef, {
        isActive: false, updatedAt: stamp, syncVersion: nextVersion,
      });
      auditWriter(transaction, db, {
        auditId, actionType: AUDIT_ACTIONS.MEDICINE_DEACTIVATED,
        entityType: "MEDICINE", entityId: id, performedBy: uid,
        details: {medicineId: id, medicineCode: previous.medicineCode || null,
          previousSyncVersion: version, syncVersion: nextVersion},
      });
      return {changed: true, medicineId: id, syncVersion: nextVersion};
    });
  }

  return {
    createMedicine: (uid, body) => mutate(uid, body && body.medicineId,
        body, false),
    updateMedicine: (uid, id, body) => mutate(uid, id, body, true),
    deactivateMedicine: deactivate,
  };
}

const service = createMedicineMasterAdminService();
module.exports = {
  createMedicineMasterAdminService,
  createMedicine: service.createMedicine,
  updateMedicine: service.updateMedicine,
  deactivateMedicine: service.deactivateMedicine,
};
