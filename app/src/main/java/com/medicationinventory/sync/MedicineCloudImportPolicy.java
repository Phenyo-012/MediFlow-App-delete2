package com.medicationinventory.sync;

import com.medicationinventory.data.local.entity.MedicineEntity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;


/**
 * Decides whether a Firestore medicine snapshot can initialize or replace a local master. Version and fingerprint checkpoints protect unsynchronized or legacy local changes, distinguish identical snapshots from changes, and prevent older or conflicting cloud state from silently overwriting local data.
 */
public final class MedicineCloudImportPolicy {
    public enum Decision {
        INSERT, UPDATE, BASELINE, UNCHANGED, DEFER_LOCAL_CHANGES, DEFER_LEGACY,
        IGNORE_OLDER, DEFER_INVALID
    }

    private MedicineCloudImportPolicy() { }


    public static Decision evaluate(MedicineEntity local, MedicineEntity remote,
                                    String lastAcceptedFingerprint) {
        if (remote == null || remote.getMedicineId() == null ||
                remote.getMedicineId().trim().isEmpty()) return Decision.DEFER_INVALID;
        if (local == null) return Decision.INSERT;
        if (!local.getMedicineId().equals(remote.getMedicineId()))
            return Decision.DEFER_INVALID;
        if (sameRecord(local, remote)) {
            return Objects.equals(lastAcceptedFingerprint, fingerprint(remote))
                    ? Decision.UNCHANGED : Decision.BASELINE;
        }
        if (remote.getSyncVersion() <= local.getSyncVersion()) {
            return Decision.IGNORE_OLDER;
        }
        // A positive version is required for remote-update ordering. Never
        // 'upgrade' a version-zero local medicine of unknown provenance.
        if (local.getSyncVersion() <= 0 || lastAcceptedFingerprint == null ||
                lastAcceptedFingerprint.isEmpty()) return Decision.DEFER_LEGACY;
        if (!lastAcceptedFingerprint.equals(fingerprint(local)))
            return Decision.DEFER_LOCAL_CHANGES;
        return Decision.UPDATE;
    }


    public static boolean sameRecord(MedicineEntity a, MedicineEntity b) {
        return a != null && b != null && fingerprint(a).equals(fingerprint(b));
    }


    public static String fingerprint(MedicineEntity medicine) {
        if (medicine == null) throw new IllegalArgumentException("Medicine is required.");
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(raw);
            writeText(data, medicine.getMedicineId());
            writeText(data, medicine.getMedicineCode());
            writeText(data, medicine.getMedicineName());
            writeText(data, medicine.getGenericName());
            writeText(data, medicine.getCategory());
            writeText(data, medicine.getStrength());
            writeText(data, medicine.getDosageForm());
            writeText(data, medicine.getUnitOfMeasure());
            data.writeInt(medicine.getReorderLevel());
            data.writeInt(medicine.getTargetStockLevel());
            data.writeBoolean(medicine.isActive());
            data.writeLong(medicine.getUpdatedAt());
            data.writeLong(medicine.getSyncVersion());
            data.flush();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray());
            StringBuilder value = new StringBuilder(digest.length * 2);
            for (byte element : digest) {
                int byteValue = element & 0xff;
                value.append(Character.forDigit(byteValue >>> 4, 16));
                value.append(Character.forDigit(byteValue & 0x0f, 16));
            }
            return value.toString();
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot compute medicine checkpoint.", error);
        }
    }


    private static void writeText(DataOutputStream data, String value) throws IOException {
        if (value == null) {
            data.writeInt(-1);
        } else {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            data.writeInt(bytes.length);
            data.write(bytes);
        }
    }
}
