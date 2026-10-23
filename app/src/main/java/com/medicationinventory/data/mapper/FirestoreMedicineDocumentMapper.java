package com.medicationinventory.data.mapper;

import com.google.firebase.Timestamp;
import com.medicationinventory.data.local.entity.MedicineEntity;

import java.util.Date;
import java.util.Map;


/**
 * Validates Firestore medicine document fields and maps them to a local MedicineEntity.
 */
public final class FirestoreMedicineDocumentMapper {

    private FirestoreMedicineDocumentMapper() { }

    public static MedicineEntity fromDocument(String documentId, Map<String, Object> fields) {
        if (documentId == null || documentId.trim().isEmpty() || fields == null) {
            throw new IllegalArgumentException("Medicine document requires a nonempty ID and fields.");
        }
        String id = documentId.trim();
        Object declaredId = fields.get("medicineId");
        if (declaredId != null && (! (declaredId instanceof String)
                || !id.equals(declaredId))) {
            throw new IllegalArgumentException("Medicine ID differs from the Firestore document ID.");
        }
        String code = text(fields, "medicineCode", true);
        String name = text(fields, "medicineName", true);
        String category = text(fields, "category", false);
        if (category == null) category = "Uncategorised";
        String dosage = text(fields, "dosageForm", true);
        String unit = text(fields, "unitOfMeasure", true);
        String generic = text(fields, "genericName", false);
        String strength = text(fields, "strength", false);
        int reorder = requireNonnegativeInt(fields, "reorderLevel");
        int target = requireNonnegativeInt(fields, "targetStockLevel");
        if (target < reorder) {
            throw new IllegalArgumentException("Target stock level is below reorder level.");
        }
        if (!(fields.get("isActive") instanceof Boolean)) {
            throw new IllegalArgumentException("isActive must be a Boolean.");
        }
        boolean active = (Boolean) fields.get("isActive");
        long updatedAt = timeMillis(fields.get("updatedAt"));
        long version = optionalNonnegativeLong(fields.get("syncVersion"));

        return new MedicineEntity(id, code, name, generic, category, strength,
                dosage, unit, reorder, target, active, updatedAt, version);
    }

    private static String text(Map<String, Object> fields, String field, boolean required) {
        Object raw = fields.get(field);
        if (raw == null && !required) return null;
        if (!(raw instanceof String))
            throw new IllegalArgumentException(field + " must be text.");
        String result = ((String) raw).trim();
        if (result.isEmpty()) {
            if (!required) return null;
            throw new IllegalArgumentException(field + " must not be empty.");
        }
        if (result.length() > 250) throw new IllegalArgumentException(field + " is too long.");
        return result;
    }

    private static int requireNonnegativeInt(Map<String, Object> fields, String field) {
        Object value = fields.get(field);
        if (!(value instanceof Number))
            throw new IllegalArgumentException(field + " must be a number.");
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number < 0 || number > Integer.MAX_VALUE
                || number != Math.rint(number)) {
            throw new IllegalArgumentException(field + " must be a nonnegative integer.");
        }
        return (int) number;
    }

    private static long optionalNonnegativeLong(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Number))
            throw new IllegalArgumentException("syncVersion must be a number.");
        double n = ((Number) value).doubleValue();
        if (!Double.isFinite(n) || n < 0 || n > Long.MAX_VALUE || n != Math.rint(n))
            throw new IllegalArgumentException("Invalid syncVersion.");
        return ((Number) value).longValue();
    }

    private static long timeMillis(Object value) {
        if (value == null) return 0;
        if (value instanceof Timestamp) return ((Timestamp) value).toDate().getTime();
        if (value instanceof Date) return ((Date) value).getTime();
        if (value instanceof Number) {

            double n = ((Number) value).doubleValue();
            if (!Double.isFinite(n) || n < 0 || n > Long.MAX_VALUE || n != Math.rint(n))
                throw new IllegalArgumentException("Invalid updatedAt value.");
            return ((Number) value).longValue();
        }
        throw new IllegalArgumentException("Unsupported updatedAt type.");
    }
}
