package com.medicationinventory.data.local.projection;

import androidx.room.Embedded;
import com.medicationinventory.data.local.entity.MedicineEntity;


/**
 * A Room query result pairing a medicine with usable, expired, expiry-window, and nearest-expiry totals.
 */
public class MedicineStockProjection {
    @Embedded
    public MedicineEntity medicine;

    public long usableUnits;
    public long expiredUnits;
    public long expiry0To14Units;
    public long expiry15To30Units;
    public long expiry31To90Units;
    public long expiryAfter90Units;
    public Long nearestUsableExpiry;
}
