package com.medicationinventory.service;

import java.util.Locale;


/**
 * Applies Barcode Code Policy rules and calculations used by inventory operations or reports.
 */
public final class BarcodeCodePolicy {

    private BarcodeCodePolicy() { }


    public static String requireCode(String raw) {
        if (raw == null) throw new IllegalArgumentException("Enter or scan a barcode.");
        String code = raw.trim();
        if (code.length() < 1 || code.length() > 128)
            throw new IllegalArgumentException("Use a code between 1 and 128 characters.");
        // Non-printable payloads are not suitable for manual linking. Spaces within
        // the string and leading zeroes are preserved; nothing is parsed as a number.
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if ((c < 32 && c != 29) || c > 126)
                throw new IllegalArgumentException("Barcode contains unsupported characters.");
        }
        String lower = code.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https:") || lower.startsWith("http:")
                || lower.startsWith("javascript:") || lower.startsWith("mailto:"))
            throw new IllegalArgumentException("Scan the product identifier, not a web link.");
        return code;
    }
}
