package com.medicationinventory.ui.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.Medicine;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;



/**
 * Checks combined search and filter behavior, expiry buckets, quantity-based sorting, and clearing filters without losing search text.
 */
public class InventoryFilterEngineTest {

    private InventoryMedicineItem item(String id, String name, String category,
                                       int reorderLevel, long usable, long expired, long expiry14, long expiry30,
                                       long expiry90, long expiryAfter, Long nearest) {
        Medicine medicine = new Medicine(id, id + "-code", name, name + " generic",
                category, "500 mg", "Tablet", "Units", reorderLevel, 50,
                true, 1L, 0L);
        return new InventoryMedicineItem(medicine, usable, expired,
                expiry14, expiry30, expiry90, expiryAfter, nearest);
    }


    private List<InventoryMedicineItem> inventory() {
        return Arrays.asList(
                item("a", "Amoxicillin", "Antibiotics", 10, 5, 0, 5, 0, 0, 0, 100L),
                item("b", "Ibuprofen", "Analgesics & Antipyretics", 10,
                        60, 8, 0, 20, 40, 0, 200L),
                item("c", "Ceftriaxone", "Antibiotics", 10,
                        0, 50, 0, 0, 0, 0, null));
    }

    @Test public void searchAndCategoryAndStockStatusAreCombined() {
        InventoryFilterState state = InventoryFilterState.defaults()
                .withSearchQuery("moxi")
                .withSelections("Antibiotics", InventoryFilterState.StockStatus.LOW_STOCK,
                        InventoryFilterState.ExpiryRange.ALL,
                        InventoryFilterState.SortOrder.NAME_ASC);
        List<InventoryMedicineItem> results = InventoryFilterEngine.apply(inventory(), state);
        assertEquals(1, results.size());
        assertEquals("a", results.get(0).getMedicine().getMedicineId());
    }

    @Test public void expiryRangesUsePositiveBatchUnitsAndCanIncludeExpiredWithoutUsable() {
        InventoryFilterState state = InventoryFilterState.defaults()
                .withSelections("", InventoryFilterState.StockStatus.OUT_OF_STOCK,
                        InventoryFilterState.ExpiryRange.EXPIRED,
                        InventoryFilterState.SortOrder.NAME_ASC);
        List<InventoryMedicineItem> results = InventoryFilterEngine.apply(inventory(), state);
        assertEquals(1, results.size());
        assertEquals("c", results.get(0).getMedicine().getMedicineId());
    }

    @Test public void stockSortAndNearestExpirySortUseRealQuantities() {
        InventoryFilterState descending = InventoryFilterState.defaults()
                .withSelections("", InventoryFilterState.StockStatus.ALL,
                        InventoryFilterState.ExpiryRange.ALL,
                        InventoryFilterState.SortOrder.STOCK_DESC);
        List<InventoryMedicineItem> byStock = InventoryFilterEngine.apply(inventory(), descending);
        assertEquals("b", byStock.get(0).getMedicine().getMedicineId());
        assertEquals("c", byStock.get(2).getMedicine().getMedicineId());

        InventoryFilterState expiry = descending.withSelections("",
                InventoryFilterState.StockStatus.ALL, InventoryFilterState.ExpiryRange.ALL,
                InventoryFilterState.SortOrder.NEAREST_EXPIRY);
        List<InventoryMedicineItem> byExpiry = InventoryFilterEngine.apply(inventory(), expiry);
        assertEquals("a", byExpiry.get(0).getMedicine().getMedicineId());
        assertEquals("c", byExpiry.get(2).getMedicine().getMedicineId());
    }

    @Test public void clearSelectionsKeepsSearchText() {
        InventoryFilterState state = InventoryFilterState.defaults()
                .withSearchQuery("Amox")
                .withSelections("Antibiotics", InventoryFilterState.StockStatus.LOW_STOCK,
                        InventoryFilterState.ExpiryRange.DAYS_0_TO_14,
                        InventoryFilterState.SortOrder.STOCK_ASC)
                .clearSelections();
        assertEquals("Amox", state.getSearchQuery());
        assertEquals(0, state.getActiveSelectionCount());
        assertTrue(state.hasAnyCriteria());
    }
}
