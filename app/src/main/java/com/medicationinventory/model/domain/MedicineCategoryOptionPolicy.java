package com.medicationinventory.model.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * Builds a stable category menu from the built-in catalog and categories seen
 * locally or on the server. It keeps the current medicine's legacy category
 * selectable, removes duplicates without regard to case, and appends the custom
 * category option after sorted dynamic values.
 */
public final class MedicineCategoryOptionPolicy {

    private MedicineCategoryOptionPolicy() { }


    public static List<String> build(List<String> local, List<String> server,
                                     String currentMedicineCategory) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String predefined : MedicineCategoryCatalog.predefinedCategories()) {
            add(unique, predefined);
        }
        addAllSorted(unique, local);
        addAllSorted(unique, server);

        add(unique, currentMedicineCategory);
        List<String> result = new ArrayList<>(unique.values());
        result.add(MedicineCategoryCatalog.OTHER);
        return Collections.unmodifiableList(result);
    }


    private static void addAllSorted(Map<String, String> unique, List<String> values) {
        if (values == null) return;
        List<String> copy = new ArrayList<>(values);
        copy.removeIf(value -> value == null);
        copy.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        for (String value : copy) add(unique, value);
    }


    private static void add(Map<String, String> unique, String value) {
        if (value == null) return;
        String clean = value.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty() || Medicine.DEFAULT_CATEGORY.equalsIgnoreCase(clean)
                || MedicineCategoryCatalog.OTHER.equalsIgnoreCase(clean)) return;
        unique.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
    }
}
