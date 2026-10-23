package com.medicationinventory.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Defines the stable identity and basic integrity check for shared barcode-to-medicine mappings.
 * Mapping document IDs are derived from the validated barcode text, so the same code converges on
 * one Firestore document across devices without storing the raw identifier in the document path.
 * This policy validates a mapping's shape; trusted authorization is enforced by the backend.
 */
public final class SharedBarcodePolicy {

    private SharedBarcodePolicy() { }

    /**
     * Creates a deterministic SHA-256 document key from a validated barcode.
     *
     * @param barcode shared identifier to validate and hash
     * @return lowercase hexadecimal Firestore document ID
     * @throws IllegalStateException if SHA-256 is unavailable in the runtime
     * @throws IllegalArgumentException if the barcode violates {@link BarcodeCodePolicy}
     */
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

    /**
     * Checks that the medicine ID is a supported identifier and that the document key matches
     * the barcode-derived key. This rejects malformed or mis-keyed cloud rows before import; it
     * does not prove that the caller may edit the mapping.
     *
     * @return {@code true} only when the mapping fields are internally consistent
     */
    public static boolean validCloudMapping(String docId, String barcode, String medicineId) {
        if (medicineId == null || !medicineId.matches("[a-zA-Z0-9_-]{1,128}"))
            return false;
        try { return documentId(barcode).equals(docId); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
