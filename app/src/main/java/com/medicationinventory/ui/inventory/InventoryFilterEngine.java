package com.medicationinventory.ui.inventory;

import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.MedicineClassification;
import java.util.Map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;




/**
 * Applies the inventory screen's search, category, stock, expiry, and optional ABC/VED filters,
 * then sorts a new result list without changing the repository-provided source. Classification
 * matching is performed only when the caller supplies authorization and a classification map;
 * missing classification rows do not match active ABC/VED criteria. Deterministic medicine-name
 * and ID tie-breakers keep rows stable when primary sort values are equal.
 */
public final class InventoryFilterEngine {

    private InventoryFilterEngine() {}


    public static List<InventoryMedicineItem> apply(
            List<InventoryMedicineItem> source, InventoryFilterState state) {
        return apply(source, state, Collections.emptyMap(), false);
    }


    /**
     * Filters and sorts the inventory snapshot for the current UI state. Search checks name, code,
     * generic name, and category case-insensitively; expiry filters use the precomputed unit
     * buckets on each item, while ABC/VED criteria are honored only for an authorized caller.
     *
     * @param source unmodified inventory items to display
     * @param state current filter and sort selections, defaulted when absent
     * @param classifications classification rows keyed by medicine ID
     * @param mayViewClassifications whether classification data may be considered
     * @return a new, sorted list containing matching non-null items
     */
    public static List<InventoryMedicineItem> apply(
            List<InventoryMedicineItem> source, InventoryFilterState state,
            Map<String, MedicineClassification> classifications, boolean mayViewClassifications) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        if (state == null) state = InventoryFilterState.defaults();
        String search = state.getSearchQuery().toLowerCase(Locale.ROOT);
        List<InventoryMedicineItem> result = new ArrayList<>();
        for (InventoryMedicineItem item : source) {
            if (item == null) continue;
            Medicine medicine = item.getMedicine();
            if (!search.isEmpty()
                    && !contains(medicine.getMedicineName(), search)
                    && !contains(medicine.getMedicineCode(), search)
                    && !contains(medicine.getGenericName(), search)
                    && !contains(medicine.getCategory(), search)) continue;
            if (!state.getCategory().isEmpty()
                    && !state.getCategory().equalsIgnoreCase(medicine.getCategory())) continue;
            if (state.getStockStatus() != InventoryFilterState.StockStatus.ALL
                    && state.getStockStatus() != item.getStockStatus()) continue;
            switch (state.getExpiryRange()) {
                case EXPIRED:
                    if (item.getExpiredUnits() <= 0L) continue;
                    break;
                case DAYS_0_TO_14:
                    if (item.getExpiry0To14Units() <= 0L) continue;
                    break;
                case DAYS_15_TO_30:
                    if (item.getExpiry15To30Units() <= 0L) continue;
                    break;
                case DAYS_31_TO_90:
                    if (item.getExpiry31To90Units() <= 0L) continue;
                    break;
                case AFTER_90_DAYS:
                    if (item.getExpiryAfter90Units() <= 0L) continue;
                    break;
                default: break;
            }
            if (mayViewClassifications && state.hasClassifiedCriteria()) {
                MedicineClassification classification = classifications == null
                        ? null : classifications.get(medicine.getMedicineId());

                if (classification == null) continue;
                if (state.getAbcClass() != InventoryFilterState.AbcClass.ALL
                        && !state.getAbcClass().name().equalsIgnoreCase(
                        classification.getAbcClassification())) continue;
                if (state.getVedClass() != InventoryFilterState.VedClass.ALL
                        && !state.getVedClass().name().equalsIgnoreCase(
                        classification.getVedClassification())) continue;
            }
            result.add(item);
        }
        Comparator<InventoryMedicineItem> nameComparator = (a, b) -> {
            int comparison = safe(a.getMedicine().getMedicineName())
                    .compareToIgnoreCase(safe(b.getMedicine().getMedicineName()));
            return comparison != 0 ? comparison
                    : safe(a.getMedicine().getMedicineId())
                    .compareToIgnoreCase(safe(b.getMedicine().getMedicineId()));
        };
        Comparator<InventoryMedicineItem> comparator;
        switch (state.getSortOrder()) {
            case NAME_DESC:
                comparator = nameComparator.reversed(); break;
            case STOCK_ASC:
                comparator = Comparator.comparingLong(InventoryMedicineItem::getUsableUnits)
                        .thenComparing(nameComparator); break;
            case STOCK_DESC:
                comparator = Comparator.comparingLong(InventoryMedicineItem::getUsableUnits)
                        .reversed().thenComparing(nameComparator); break;
            case NEAREST_EXPIRY:
                comparator = Comparator.comparing(InventoryMedicineItem::getNearestUsableExpiry,
                        Comparator.nullsLast(Long::compareTo)).thenComparing(nameComparator); break;
            default:
                comparator = nameComparator;
        }
        result.sort(comparator);
        return result;
    }


    private static boolean contains(String text, String lowerNeedle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static String safe(String text) { return text == null ? "" : text; }
}
