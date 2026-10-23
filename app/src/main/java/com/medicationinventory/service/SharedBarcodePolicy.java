package com.medicationinventory.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Applies Shared Barcode Policy business rules used by inventory operations and reports.
 */
public final class SharedBarcodePolicy {

    private SharedBarcodePolicy() { }

    public static String documentId(String barcode) {
        String value = BarcodeCodePolicy.requireCode(barcode);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) result.append(String.format("%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required by Android.", unavailable);
        }
    }

    public static boolean validCloudMapping(String docId, String barcode, String medicineId) {
        if (medicineId == null || !medicineId.matches("[a-zA-Z0-9_-]{1,128}"))
            return false;
        try { return documentId(barcode).equals(docId); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
