package com.medicationinventory.service;

import java.util.Locale;


/**
 * Validates text identifiers before they are linked to shared medicine records. Codes remain
 * strings so leading zeroes and embedded spaces are preserved; control characters and link-like
 * payloads are rejected instead of being interpreted as web addresses.
 */
public final class BarcodeCodePolicy {

    private BarcodeCodePolicy() { }


    /**
     * Trims surrounding whitespace and returns a printable code within the supported length.
     * The value is not normalized numerically because leading zeroes can be part of an identifier.
     *
     * @param raw manually entered or scanned identifier
     * @return validated identifier text
     * @throws IllegalArgumentException if the input is absent, too long, contains unsupported
     *                                  characters, or resembles a link
     */
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
