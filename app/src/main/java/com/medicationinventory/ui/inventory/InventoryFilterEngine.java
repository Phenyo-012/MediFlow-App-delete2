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
 * Contains Inventory Filter Engine behavior used by the Android interface.
 */
public final class InventoryFilterEngine {

    private InventoryFilterEngine() {}


    public static List<InventoryMedicineItem> apply(
            List<InventoryMedicineItem> source, InventoryFilterState state) {
        return apply(source, state, Collections.emptyMap(), false);
    }


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
