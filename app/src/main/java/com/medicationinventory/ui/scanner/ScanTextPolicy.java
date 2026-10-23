package com.medicationinventory.ui.scanner;


/**
 * Normalizes scanner or manually entered text into a value safe for a single
 * inventory search field. It trims surrounding whitespace and rejects empty,
 * overlong, multiline, or NUL-containing payloads while preserving identifiers
 * such as leading-zero barcodes and their case.
 */
public final class ScanTextPolicy {
    private static final int MAX_LENGTH = 256;


    private ScanTextPolicy() { }


    public static String searchableValue(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) return null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\u0000') return null;
        }
        return value;
    }
}
