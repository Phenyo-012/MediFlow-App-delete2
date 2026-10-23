package com.medicationinventory.service;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;



/**
 * Covers supported GS1 encodings, variable-length lot fields, expiry-date conventions, GTIN check digits, and malformed input.
 */
public class Gs1ScanParserTest {
    private static final String GTIN = "09506000134352";

    @Test public void plainRetailCodeDoesNotPretendToHaveBatchData() {
        assertNull(Gs1ScanParser.parse("9506000134352"));
        assertNull(Gs1ScanParser.parse("000000000123"));
        assertNull(Gs1ScanParser.parse("some arbitrary QR content"));
    }

    @Test public void bracketedGs1ParsesGtinBatchAndExpiry() {
        Gs1ScanParser.Data parsed = Gs1ScanParser.parse(
                "(01)" + GTIN + "(17)271231(10)BATCH-04");
        assertEquals(GTIN, parsed.getGtin());
        assertEquals("BATCH-04", parsed.getLot());
        assertEquals(LocalDate.of(2027, 12, 31), parsed.getExpiryDate());
    }

    @Test public void bracketedVariableLengthBatchCanPrecedeExpiry() {
        Gs1ScanParser.Data parsed = Gs1ScanParser.parse(
                "(01)" + GTIN + "(10)LOT-A(17)270131");
        assertEquals("LOT-A", parsed.getLot());
        assertEquals(LocalDate.of(2027, 1, 31), parsed.getExpiryDate());
    }

    @Test public void rawGsSeparatedFieldsAndSymbologyIdentifier() {
        Gs1ScanParser.Data parsed = Gs1ScanParser.parse(
                "]d201" + GTIN + "10ABC123" + (char) 29 + "17271231");
        assertEquals(GTIN, parsed.getGtin());
        assertEquals("ABC123", parsed.getLot());
        assertEquals(LocalDate.of(2027, 12, 31), parsed.getExpiryDate());
    }

    @Test public void gs1DayZeroMeansLastDayOfMonth() {
        Gs1ScanParser.Data parsed = Gs1ScanParser.parse(
                "(01)" + GTIN + "(17)280200");
        assertEquals(LocalDate.of(2028, 2, 29), parsed.getExpiryDate());
    }

    @Test public void gs1GtinOnlyContainsNoBatchOrExpiry() {
        Gs1ScanParser.Data parsed = Gs1ScanParser.parse("01" + GTIN);
        assertEquals(GTIN, parsed.getGtin());
        assertNull(parsed.getLot());
        assertNull(parsed.getExpiryDate());
    }

    @Test public void gtinCheckDigitIsValidated() {
        assertTrue(Gs1ScanParser.validGtinCheckDigit(GTIN));
        assertFalse(Gs1ScanParser.validGtinCheckDigit("09506000134353"));
        assertFalse(Gs1ScanParser.validGtinCheckDigit("0950600013435a"));
    }

    @Test public void rejectsMalformedGs1Date() {
        assertInvalid("(01)" + GTIN + "(17)271332");
        assertInvalid("(01)" + GTIN + "(17)270230");
    }

    @Test public void rejectsMissingGtinAndUnboundedOrUnknownFields() {
        assertInvalid("(10)LOT123");
        assertInvalid("(01)" + GTIN + "(11)250101");
        assertInvalid("(01)" + GTIN + "(10)123456789012345678901");
    }


    private static void assertInvalid(String input) {
        try { Gs1ScanParser.parse(input); fail("Should reject " + input); }
        catch (IllegalArgumentException expected) { /* fail closed */ }
    }
}
