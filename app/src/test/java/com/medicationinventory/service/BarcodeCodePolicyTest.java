package com.medicationinventory.service;

import org.junit.Test;
import static org.junit.Assert.*;



/**
 * Verifies barcode text preservation and rejects URL, control-character, empty, or oversized identifiers.
 */
public class BarcodeCodePolicyTest {
    @Test public void leadingZeroesArePreserved() {
        assertEquals("000012345678", BarcodeCodePolicy.requireCode(" 000012345678 "));
    }
    @Test public void qrIdentifiersCanBeLinkedAsText() {
        assertEquals("MED-QR-001", BarcodeCodePolicy.requireCode("MED-QR-001"));
    }
    @Test public void gs1GroupSeparatorIsPreserved() {
        assertEquals("010123456789012817280101\u001d10LOT01",
                BarcodeCodePolicy.requireCode("010123456789012817280101\u001d10LOT01"));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUrls() {
        BarcodeCodePolicy.requireCode("https://example.org/qr");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsControlNewline() {
        BarcodeCodePolicy.requireCode("a\nb");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsTooLong() {
        BarcodeCodePolicy.requireCode("A".repeat(129));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsEmpty() {
        BarcodeCodePolicy.requireCode("   ");
    }
}
