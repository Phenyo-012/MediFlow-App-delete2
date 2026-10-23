package com.medicationinventory.data.mapper;

import com.medicationinventory.data.local.entity.MedicineEntity;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;



/**
 * Checks medicine document defaults and leading-zero preservation, plus rejection of invalid identifiers, required fields, and stock thresholds.
 */
public class FirestoreMedicineDocumentMapperTest {

    private Map<String, Object> valid() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("medicineId", "cloud-med-001");
        fields.put("medicineCode", "00123456789012");
        fields.put("medicineName", "Test Paracetamol");
        fields.put("genericName", "Paracetamol");
        fields.put("category", "Pain & Inflammation");
        fields.put("dosageForm", "Tablet");
        fields.put("unitOfMeasure", "tablet");
        fields.put("reorderLevel", 10L);
        fields.put("targetStockLevel", 30L);
        fields.put("isActive", true);
        fields.put("updatedAt", 1700000000000L);
        fields.put("syncVersion", 2L);
        return fields;
    }

    @Test public void validDocumentKeepsLeadingZerosAndCategory() {
        MedicineEntity e = FirestoreMedicineDocumentMapper.fromDocument(
                "cloud-med-001", valid());
        assertEquals("00123456789012", e.getMedicineCode());
        assertEquals("Pain & Inflammation", e.getCategory());
        assertEquals("cloud-med-001", e.getMedicineId());
        assertEquals(10, e.getReorderLevel());
        assertEquals(30, e.getTargetStockLevel());
        assertTrue(e.isActive());
    }

    @Test public void missingOptionalCategoryGetsSafeDefault() {
        Map<String,Object> f = valid(); f.remove("category");
        assertEquals("Uncategorised",
                FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f).getCategory());
    }

    @Test(expected = IllegalArgumentException.class)

    public void mismatchedDocumentIdRejected() {
        FirestoreMedicineDocumentMapper.fromDocument("another-id", valid());
    }

    @Test(expected = IllegalArgumentException.class)

    public void missingMedicineCodeRejected() {
        Map<String,Object> f = valid(); f.remove("medicineCode");
        FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void missingActiveStatusRejected() {
        Map<String,Object> f = valid(); f.remove("isActive");
        FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void negativeStockThresholdRejected() {
        Map<String,Object> f = valid(); f.put("reorderLevel", -1L);
        FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void fractionalThresholdRejected() {
        Map<String,Object> f = valid(); f.put("reorderLevel", 1.5d);
        FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f);
    }

    @Test(expected = IllegalArgumentException.class)

    public void nonsensicalThresholdOrderRejected() {
        Map<String,Object> f = valid(); f.put("reorderLevel", 31L);
        FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f);
    }

    @Test public void disabledMedicineRemainsDisabled() {
        Map<String,Object> f = valid(); f.put("isActive", false);
        assertFalse(FirestoreMedicineDocumentMapper.fromDocument("cloud-med-001", f).isActive());
    }
}
