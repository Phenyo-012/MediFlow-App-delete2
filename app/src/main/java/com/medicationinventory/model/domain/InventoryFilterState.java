package com.medicationinventory.model.domain;


/**
 * Immutable selection state for the inventory list. It combines text and
 * category search with stock, expiry, and sort choices, and optionally ABC/VED
 * criteria that the ViewModel can clear when restricted classification access
 * is revoked. Constructor defaults keep missing selections usable by the UI.
 */
public final class InventoryFilterState {
    public enum StockStatus {
        ALL("All statuses"), IN_STOCK("In stock"),
        LOW_STOCK("Low stock"), OUT_OF_STOCK("Out of stock");
        private final String label;
        StockStatus(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static StockStatus fromLabel(String text) {
            for (StockStatus option : values()) if (option.label.equals(text)) return option;
            return ALL;
        }
    }

    public enum ExpiryRange {
        ALL("All expiry ranges"), EXPIRED("Expired stock"),
        DAYS_0_TO_14("0–14 days"), DAYS_15_TO_30("15–30 days"),
        DAYS_31_TO_90("31–90 days"), AFTER_90_DAYS("90+ days");
        private final String label;
        ExpiryRange(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static ExpiryRange fromLabel(String text) {
            for (ExpiryRange option : values()) if (option.label.equals(text)) return option;
            return ALL;
        }
    }

    public enum SortOrder {
        NAME_ASC("Name A–Z"), NAME_DESC("Name Z–A"),
        STOCK_ASC("Lowest usable stock"), STOCK_DESC("Highest usable stock"),
        NEAREST_EXPIRY("Nearest usable expiry");
        private final String label;
        SortOrder(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static SortOrder fromLabel(String text) {
            for (SortOrder option : values()) if (option.label.equals(text)) return option;
            return NAME_ASC;
        }
    }

    public enum AbcClass {
        ALL("All ABC classes"), A("A"), B("B"), C("C");
        private final String label;
        AbcClass(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static AbcClass fromLabel(String label) {
            for (AbcClass value : values()) if (value.label.equals(label)) return value;
            return ALL;
        }
    }

    public enum VedClass {
        ALL("All VED classes"), V("Vital (V)"), E("Essential (E)"), D("Desirable (D)");
        private final String label;
        VedClass(String label) { this.label = label; }
        public String getLabel() { return label; }

        public static VedClass fromLabel(String label) {
            for (VedClass value : values()) if (value.label.equals(label)) return value;
            return ALL;
        }
    }

    private final String searchQuery;
    private final String category;
    private final StockStatus stockStatus;
    private final ExpiryRange expiryRange;
    private final SortOrder sortOrder;
    private final AbcClass abcClass;
    private final VedClass vedClass;


    public InventoryFilterState(String searchQuery, String category,
                                StockStatus stockStatus, ExpiryRange expiryRange, SortOrder sortOrder) {
        this(searchQuery, category, stockStatus, expiryRange, sortOrder,
                AbcClass.ALL, VedClass.ALL);
    }

    public InventoryFilterState(String searchQuery, String category,
                                StockStatus stockStatus, ExpiryRange expiryRange, SortOrder sortOrder,
                                AbcClass abcClass, VedClass vedClass) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim();
        this.category = category == null || category.trim().isEmpty() ? "" : category.trim();
        this.stockStatus = stockStatus == null ? StockStatus.ALL : stockStatus;
        this.expiryRange = expiryRange == null ? ExpiryRange.ALL : expiryRange;
        this.sortOrder = sortOrder == null ? SortOrder.NAME_ASC : sortOrder;
        this.abcClass = abcClass == null ? AbcClass.ALL : abcClass;
        this.vedClass = vedClass == null ? VedClass.ALL : vedClass;
    }


    public static InventoryFilterState defaults() {
        return new InventoryFilterState("", "", StockStatus.ALL,
                ExpiryRange.ALL, SortOrder.NAME_ASC);
    }
    public String getSearchQuery() { return searchQuery; }
    public String getCategory() { return category; }
    public StockStatus getStockStatus() { return stockStatus; }
    public ExpiryRange getExpiryRange() { return expiryRange; }
    public SortOrder getSortOrder() { return sortOrder; }
    public AbcClass getAbcClass() { return abcClass; }
    public VedClass getVedClass() { return vedClass; }

    public boolean hasClassifiedCriteria() {
        return abcClass != AbcClass.ALL || vedClass != VedClass.ALL;
    }
    public int getActiveSelectionCount() {
        int result = 0;
        if (!category.isEmpty()) result++;
        if (stockStatus != StockStatus.ALL) result++;
        if (expiryRange != ExpiryRange.ALL) result++;
        if (sortOrder != SortOrder.NAME_ASC) result++;
        if (abcClass != AbcClass.ALL) result++;
        if (vedClass != VedClass.ALL) result++;
        return result;
    }

    public boolean hasAnyCriteria() { return !searchQuery.isEmpty() || getActiveSelectionCount() > 0; }

    public InventoryFilterState withSearchQuery(String query) {
        return new InventoryFilterState(query, category, stockStatus, expiryRange,
                sortOrder, abcClass, vedClass);
    }

    public InventoryFilterState withSelections(String category,
                                               StockStatus status, ExpiryRange expiry, SortOrder sort) {
        return new InventoryFilterState(searchQuery, category, status, expiry, sort,
                abcClass, vedClass);
    }

    public InventoryFilterState withAllSelections(String category, StockStatus status,
                                                  ExpiryRange expiry, SortOrder sort, AbcClass abc, VedClass ved) {
        return new InventoryFilterState(searchQuery, category, status, expiry, sort, abc, ved);
    }

    public InventoryFilterState withoutClassifiedCriteria() {
        return new InventoryFilterState(searchQuery, category, stockStatus, expiryRange,
                sortOrder, AbcClass.ALL, VedClass.ALL);
    }

    public InventoryFilterState clearSelections() {
        return defaults().withSearchQuery(searchQuery);
    }
}
