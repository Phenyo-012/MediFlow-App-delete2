package com.medicationinventory.ui.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.MedicineClassification;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Checks combined ABC/VED and general filters, immediate clearing on access revocation, and no restricted matching without authorization or classification.
 */
public class InventoryRestrictedFilterTest {

    private InventoryMedicineItem item(String id, String category, long units) {
        Medicine medicine = new Medicine(id, id, id, null, category, "500 mg",
                "Tablet", "Units", 10, 50, true, 1L, 0L);
        return new InventoryMedicineItem(medicine, units, 0, units, 0, 0, 0, 10L);
    }

    @Test public void abcVedAndGeneralFiltersCombine() {
        List<InventoryMedicineItem> source = Arrays.asList(
                item("one", "Antibiotics", 5),
                item("two", "Antibiotics", 30),
                item("three", "Other", 5));
        Map<String, MedicineClassification> classifications = new HashMap<>();
        classifications.put("one", new MedicineClassification("one", "A", "V", 1L));
        classifications.put("two", new MedicineClassification("two", "B", "E", 1L));
        classifications.put("three", new MedicineClassification("three", "A", "V", 1L));
        InventoryFilterState state = InventoryFilterState.defaults()
                .withSelections("Antibiotics", InventoryFilterState.StockStatus.LOW_STOCK,
                        InventoryFilterState.ExpiryRange.ALL,
                        InventoryFilterState.SortOrder.NAME_ASC)
                .withAllSelections("Antibiotics", InventoryFilterState.StockStatus.LOW_STOCK,
                        InventoryFilterState.ExpiryRange.ALL,
                        InventoryFilterState.SortOrder.NAME_ASC,
                        InventoryFilterState.AbcClass.A, InventoryFilterState.VedClass.V);
        List<InventoryMedicineItem> actual = InventoryFilterEngine.apply(
                source, state, classifications, true);
        assertEquals(1, actual.size());
        assertEquals("one", actual.get(0).getMedicine().getMedicineId());
    }

    @Test public void restrictedSelectionsClearImmediatelyOnRevocation() {
        InventoryFilterState state = InventoryFilterState.defaults()
                .withSearchQuery("one")
                .withAllSelections("Antibiotics", InventoryFilterState.StockStatus.ALL,
                        InventoryFilterState.ExpiryRange.ALL,
                        InventoryFilterState.SortOrder.NAME_ASC,
                        InventoryFilterState.AbcClass.A, InventoryFilterState.VedClass.E);
        InventoryFilterState stripped = state.withoutClassifiedCriteria();
        assertFalse(stripped.hasClassifiedCriteria());
        assertEquals(1, stripped.getActiveSelectionCount());
        assertEquals("one", stripped.getSearchQuery());
        assertTrue(stripped.hasAnyCriteria());
    }

    @Test public void unauthorizedEngineDoesNotInspectOrApplyClassificationCriteria() {
        List<InventoryMedicineItem> source = Arrays.asList(item("one", "Antibiotics", 5));
        InventoryFilterState state = InventoryFilterState.defaults().withAllSelections(
                "", InventoryFilterState.StockStatus.ALL,
                InventoryFilterState.ExpiryRange.ALL, InventoryFilterState.SortOrder.NAME_ASC,
                InventoryFilterState.AbcClass.A, InventoryFilterState.VedClass.V);
        // Even if an upstream caller mistakenly passes restricted criteria, deny-gated
        // operation must behave as normal inventory filtering and ignore the map.
        List<InventoryMedicineItem> result = InventoryFilterEngine.apply(
                source, state, new HashMap<>(), false);
        assertEquals(1, result.size());
    }

    @Test public void missingClassificationIsNotTreatedAsMatching() {
        List<InventoryMedicineItem> source = Arrays.asList(item("one", "Antibiotics", 5));
        InventoryFilterState state = InventoryFilterState.defaults().withAllSelections(
                "", InventoryFilterState.StockStatus.ALL,
                InventoryFilterState.ExpiryRange.ALL, InventoryFilterState.SortOrder.NAME_ASC,
                InventoryFilterState.AbcClass.A, InventoryFilterState.VedClass.ALL);
        assertEquals(0, InventoryFilterEngine.apply(source, state,
                new HashMap<>(), true).size());
    }
}
