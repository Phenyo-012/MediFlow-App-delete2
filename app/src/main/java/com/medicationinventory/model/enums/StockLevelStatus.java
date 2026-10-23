package com.medicationinventory.model.enums;



/**
 * Report classification of usable stock relative to its reorder threshold, including zero-stock state.
 */
public enum StockLevelStatus
{
    NORMAL,
    LOW_STOCK,
    OUT_OF_STOCK
}