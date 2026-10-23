package com.medicationinventory.ui.scanner;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * Verifies that scan text preserves identifiers while rejecting empty, multiline, or oversized payloads.
 */
public class ScanTextPolicyTest {
    @Test public void preservesLeadingZeros() {
        assertEquals("00012345", ScanTextPolicy.searchableValue(" 00012345 "));
    }
    @Test public void refusesEmptyInput() {
        assertNull(ScanTextPolicy.searchableValue(null));
        assertNull(ScanTextPolicy.searchableValue("   "));
    }
    @Test public void refusesMultiLineQrPayload() {
        assertNull(ScanTextPolicy.searchableValue("code\ncommand"));
    }
    @Test public void refusesOversizedData() {
        assertNull(ScanTextPolicy.searchableValue("X".repeat(257)));
    }
    @Test public void preservesCaseAndSeparator() {
        assertEquals("ABC|123", ScanTextPolicy.searchableValue("ABC|123"));
    }
}
