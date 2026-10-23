package com.medicationinventory.model.domain;


/**
 * Inventory-list projection pairing a medicine master record with aggregate
 * usable, expired, and expiry-window quantities. It also carries the nearest
 * usable expiry for sorting; stock status is derived from usable units and the
 * medicine's reorder level rather than from total physical quantity.
 */
public final class InventoryMedicineItem {
    private final Medicine medicine;
    private final long usableUnits;
    private final long expiredUnits;
    private final long expiry0To14Units;
    private final long expiry15To30Units;
    private final long expiry31To90Units;
    private final long expiryAfter90Units;
    private final Long nearestUsableExpiry;


    public InventoryMedicineItem(Medicine medicine, long usableUnits,
                                 long expiredUnits, long expiry0To14Units, long expiry15To30Units,
                                 long expiry31To90Units, long expiryAfter90Units,
                                 Long nearestUsableExpiry) {
        if (medicine == null) throw new IllegalArgumentException("Medicine is required");
        this.medicine = medicine;
        this.usableUnits = Math.max(0L, usableUnits);
        this.expiredUnits = Math.max(0L, expiredUnits);
        this.expiry0To14Units = Math.max(0L, expiry0To14Units);
        this.expiry15To30Units = Math.max(0L, expiry15To30Units);
        this.expiry31To90Units = Math.max(0L, expiry31To90Units);
        this.expiryAfter90Units = Math.max(0L, expiryAfter90Units);
        this.nearestUsableExpiry = nearestUsableExpiry;
    }

    public Medicine getMedicine() { return medicine; }
    public long getUsableUnits() { return usableUnits; }
    public long getExpiredUnits() { return expiredUnits; }
    public long getExpiry0To14Units() { return expiry0To14Units; }
    public long getExpiry15To30Units() { return expiry15To30Units; }
    public long getExpiry31To90Units() { return expiry31To90Units; }
    public long getExpiryAfter90Units() { return expiryAfter90Units; }
    public Long getNearestUsableExpiry() { return nearestUsableExpiry; }

    public InventoryFilterState.StockStatus getStockStatus() {
        if (usableUnits == 0L) return InventoryFilterState.StockStatus.OUT_OF_STOCK;
        if (usableUnits <= medicine.getReorderLevel()) return InventoryFilterState.StockStatus.LOW_STOCK;
        return InventoryFilterState.StockStatus.IN_STOCK;
    }
}
