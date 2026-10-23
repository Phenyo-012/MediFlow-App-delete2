package com.medicationinventory.service;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Checks identifier case and leading-zero preservation and rejects forged or unsupported shared barcode values.
 */
public class SharedBarcodePolicyTest {
    @Test public void preservesLeadingZeros() {
        assertEquals("767d18f567157eddc8d5660d6821ba27ba618e10fe217633cdbb091f89dcfd71",
                SharedBarcodePolicy.documentId("00123450"));
    }
    @Test public void upperAndLowercaseAreDistinctIdentifiers() {
        assertNotEquals(SharedBarcodePolicy.documentId("abc"),
                SharedBarcodePolicy.documentId("ABC"));
    }
    @Test public void rejectsForgeryAndUnsupportedCodes() {
        String id = SharedBarcodePolicy.documentId("00123450");
        assertTrue(SharedBarcodePolicy.validCloudMapping(id, "00123450", "medicine-1"));
        assertFalse(SharedBarcodePolicy.validCloudMapping(id, "0012345X", "medicine-1"));
        assertFalse(SharedBarcodePolicy.validCloudMapping(id, "00123450", "../other"));
        assertFalse(SharedBarcodePolicy.validCloudMapping("invalid", "http://example.com", "medicine-1"));
    }
}
