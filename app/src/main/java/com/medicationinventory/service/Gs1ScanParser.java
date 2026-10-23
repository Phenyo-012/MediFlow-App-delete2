package com.medicationinventory.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;




/**
 * Parses the GS1 subset used by stock receiving: GTIN (01), expiry (17), and lot (10). It accepts
 * scanner-prefixed, parenthesized, and supported raw element strings, validates the GTIN check
 * digit and expiry date, and rejects duplicate or unknown fields instead of returning partial
 * product data. A valid scan may omit lot or expiry; callers decide whether their workflow
 * requires those values.
 */
public final class Gs1ScanParser {
    private static final char GS = 29;
    private Gs1ScanParser() { }

    public static final class Data {
        private final String gtin;
        private final String lot;
        private final LocalDate expiryDate;

        private Data(String gtin, String lot, LocalDate expiryDate) {
            this.gtin = gtin;
            this.lot = lot;
            this.expiryDate = expiryDate;
        }
        public String getGtin() { return gtin; }
        public String getLot() { return lot; }
        public LocalDate getExpiryDate() { return expiryDate; }
    }


    /**
     * Parses a recognized GS1 payload. Unrecognized scanner text returns {@code null}; once the
     * input is identified as GS1, malformed or unsupported fields raise
     * {@link IllegalArgumentException} so callers can report invalid structured data. Missing lot
     * and expiry fields remain absent for the receiving workflow to validate if required.
     *
     * @param raw scanner text, optionally including a symbology prefix
     * @return GTIN and any supplied lot and expiry values
     * @throws IllegalArgumentException when a recognized GS1 element string is malformed
     */
    public static Data parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.length() > 256) return null;
        boolean marked = value.startsWith("]C1") || value.startsWith("]d2");
        if (marked) value = value.substring(3);
        boolean bracketed = value.startsWith("(01)")
                || value.startsWith("(17)") || value.startsWith("(10)");
        boolean rawAi01 = value.startsWith("01") && value.length() >= 16
                && digits(value.substring(2, 16));
        if (!marked && !bracketed && !rawAi01) return null;
        if (!bracketed && !rawAi01)
            throw new IllegalArgumentException("Unsupported GS1 element string.");

        String gtin = null;
        String lot = null;
        LocalDate expiry = null;
        int at = 0;
        while (at < value.length()) {
            if (value.charAt(at) == GS) { at++; continue; }
            String ai;
            if (bracketed) {
                if (at + 4 > value.length() || value.charAt(at) != '('
                        || value.charAt(at + 3) != ')')
                    throw new IllegalArgumentException("Malformed GS1 application identifier.");
                ai = value.substring(at + 1, at + 3);
                if (!digits(ai)) throw new IllegalArgumentException("Invalid GS1 identifier.");
                at += 4;
            } else {
                if (at + 2 > value.length())
                    throw new IllegalArgumentException("Incomplete GS1 identifier.");
                ai = value.substring(at, at + 2);
                at += 2;
            }
            switch (ai) {
                case "01": {
                    if (gtin != null || at + 14 > value.length())
                        throw new IllegalArgumentException("Invalid or repeated GS1 GTIN.");
                    gtin = value.substring(at, at + 14);
                    if (!digits(gtin) || !validGtinCheckDigit(gtin))
                        throw new IllegalArgumentException("Invalid GS1 GTIN check digit.");
                    at += 14;
                    break;
                }
                case "17": {
                    if (expiry != null || at + 6 > value.length())
                        throw new IllegalArgumentException("Invalid or repeated GS1 expiry.");
                    String dateCode = value.substring(at, at + 6);
                    if (!digits(dateCode))
                        throw new IllegalArgumentException("GS1 expiry must be YYMMDD.");
                    expiry = parseExpiry(dateCode);
                    at += 6;
                    break;
                }
                case "10": {
                    if (lot != null) throw new IllegalArgumentException("Repeated GS1 batch.");
                    int next = value.indexOf(bracketed ? '(' : GS, at);
                    int end = next < 0 ? value.length() : next;
                    lot = value.substring(at, end);
                    if (lot.isEmpty() || lot.length() > 20 || !printable(lot))
                        throw new IllegalArgumentException("GS1 batch must contain 1-20 printable characters.");
                    at = end;
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported GS1 field (" + ai + ").");
            }
        }
        if (gtin == null) throw new IllegalArgumentException("GS1 code has no (01) GTIN.");
        return new Data(gtin, lot, expiry);
    }

    /** Interprets YYMMDD using the GS1 month-end convention when the day is {@code 00}. */
    private static LocalDate parseExpiry(String code) {
        int year = 2000 + Integer.parseInt(code.substring(0, 2));
        int month = Integer.parseInt(code.substring(2, 4));
        int day = Integer.parseInt(code.substring(4, 6));
        try {
            // GS1 YYMM00 means the last calendar day of the indicated month.
            if (day == 0) day = YearMonth.of(year, month).lengthOfMonth();
            return LocalDate.of(year, month, day);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("Invalid GS1 expiry date.", error);
        }
    }

    private static boolean digits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') return false;
        }
        return true;
    }

    private static boolean printable(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    /** Applies the weighted modulo-10 check-digit rule to a 14-digit GTIN. */
    public static boolean validGtinCheckDigit(String code) {
        if (code == null || code.length() != 14 || !digits(code)) return false;
        int sum = 0;
        for (int i = 12; i >= 0; i--) {
            sum += (code.charAt(i) - '0') * (((12 - i) % 2 == 0) ? 3 : 1);
        }
        int expected = (10 - sum % 10) % 10;
        return expected == code.charAt(13) - '0';
    }
}
