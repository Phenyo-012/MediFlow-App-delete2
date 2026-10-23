package com.medicationinventory.model.domain;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;



/**
 * Covers category option ordering, normalization, cloud-only and legacy values, and the policy not mutating caller lists.
 */
public class MedicineCategoryOptionPolicyTest {
    @Test public void picksUpCloudOnlyCustomCategory() {
        List<String> all = MedicineCategoryOptionPolicy.build(
                Collections.emptyList(), Arrays.asList("Staging Validation Updated"), null);
        assertTrue(all.contains("Staging Validation Updated"));
    }
    @Test public void predefinedFirstAndOtherLast() {
        List<String> all = MedicineCategoryOptionPolicy.build(
                Arrays.asList("Local Custom"), Arrays.asList("Cloud Custom"), null);
        assertEquals(MedicineCategoryCatalog.predefinedCategories().get(0), all.get(0));
        assertEquals(MedicineCategoryCatalog.OTHER, all.get(all.size() - 1));
    }
    @Test public void deduplicatesCaseAndWhitespacePreservingCanonicalPredefined() {
        List<String> all = MedicineCategoryOptionPolicy.build(
                Arrays.asList(" antibiotics ", "  Local    Custom  "),
                Arrays.asList("LOCAL CUSTOM", "Antibiotics"), null);
        assertEquals(1, Collections.frequency(all, "Antibiotics"));
        assertEquals(1, Collections.frequency(all, "Local Custom"));
        assertFalse(all.contains("LOCAL CUSTOM"));
    }
    @Test public void doesNotExposeUncategorisedOrOtherAsData() {
        List<String> all = MedicineCategoryOptionPolicy.build(
                Arrays.asList("Uncategorised", "Other...", "  "),
                Arrays.asList("other...", "uncategorised"), null);
        assertEquals(MedicineCategoryCatalog.predefinedCategories().size() + 1, all.size());
    }
    @Test public void preservesCurrentlyEditedLegacyCategory() {
        List<String> all = MedicineCategoryOptionPolicy.build(null, null, "Legacy Category");
        assertTrue(all.contains("Legacy Category"));
    }
    @Test public void doesNotModifyIncomingLists() {
        List<String> local = Collections.unmodifiableList(Arrays.asList("X", "Y"));
        List<String> cloud = Collections.unmodifiableList(Arrays.asList("Q", "Z"));
        MedicineCategoryOptionPolicy.build(local, cloud, null);
        assertEquals(Arrays.asList("X", "Y"), local);
        assertEquals(Arrays.asList("Q", "Z"), cloud);
    }
}
