package com.medicationinventory.sync;

import com.medicationinventory.data.local.entity.MedicineEntity;
import org.junit.Test;

import static org.junit.Assert.*;



/**
 * Checks when cloud medicine snapshots may be imported, including version conflicts, unsynced local edits, fingerprints, and active-state changes.
 */
public class MedicineCloudImportPolicyTest {

    private static MedicineEntity medicine(String category, long version, long updatedAt) {
        return new MedicineEntity("med-1", "000012345678", "Example Medicine", null,
                category, "500mg", "Tablet", "tablets", 5, 10,
                true, updatedAt, version);
    }

    @Test public void missingMedicineIsImported() {
        assertEquals(MedicineCloudImportPolicy.Decision.INSERT,
                MedicineCloudImportPolicy.evaluate(null, medicine("Analgesics", 2, 200), null));
    }

    @Test public void identicalExistingRecordReceivesCheckpoint() {
        MedicineEntity existing = medicine("Analgesics", 1, 100);
        assertEquals(MedicineCloudImportPolicy.Decision.BASELINE,
                MedicineCloudImportPolicy.evaluate(existing,
                        medicine("Analgesics", 1, 100), null));
    }

    @Test public void repeatedMatchingSnapshotDoesNothing() {
        MedicineEntity local = medicine("Analgesics", 1, 100);
        assertEquals(MedicineCloudImportPolicy.Decision.UNCHANGED,
                MedicineCloudImportPolicy.evaluate(local,
                        medicine("Analgesics", 1, 100),
                        MedicineCloudImportPolicy.fingerprint(local)));
    }

    @Test public void newerCloudCategoryUpdatesTrustedLocal() {
        MedicineEntity local = medicine("Analgesics", 1, 100);
        MedicineEntity remote = medicine("Emergency Medicines", 2, 200);
        assertEquals(MedicineCloudImportPolicy.Decision.UPDATE,
                MedicineCloudImportPolicy.evaluate(local, remote,
                        MedicineCloudImportPolicy.fingerprint(local)));
    }

    @Test public void unknownHistoricalLocalRecordIsNeverOverwritten() {
        MedicineEntity local = medicine("Local Category", 1, 100);
        assertEquals(MedicineCloudImportPolicy.Decision.DEFER_LEGACY,
                MedicineCloudImportPolicy.evaluate(local, medicine("Cloud Category", 2, 200), null));
    }

    @Test public void versionZeroLocalRecordRequiresReconciliation() {
        MedicineEntity local = medicine("Local Category", 0, 0);
        assertEquals(MedicineCloudImportPolicy.Decision.DEFER_LEGACY,
                MedicineCloudImportPolicy.evaluate(local, medicine("Cloud Category", 2, 200),
                        MedicineCloudImportPolicy.fingerprint(local)));
    }

    @Test public void unsynchronisedLocalChangeIsProtected() {
        MedicineEntity imported = medicine("Analgesics", 1, 100);
        String checkpoint = MedicineCloudImportPolicy.fingerprint(imported);
        MedicineEntity locallyEdited = medicine("Unsaved Local Category", 1, 100);
        assertEquals(MedicineCloudImportPolicy.Decision.DEFER_LOCAL_CHANGES,
                MedicineCloudImportPolicy.evaluate(locallyEdited,
                        medicine("Cloud Category", 2, 200), checkpoint));
    }

    @Test public void olderServerVersionNeverReplacesLocal() {
        MedicineEntity local = medicine("New", 3, 300);
        assertEquals(MedicineCloudImportPolicy.Decision.IGNORE_OLDER,
                MedicineCloudImportPolicy.evaluate(local, medicine("Old", 2, 200),
                        MedicineCloudImportPolicy.fingerprint(local)));
    }

    @Test public void sameVersionButDifferentCategoryNeverOverwritesLocal() {
        MedicineEntity local = medicine("Local", 2, 200);
        assertEquals(MedicineCloudImportPolicy.Decision.IGNORE_OLDER,
                MedicineCloudImportPolicy.evaluate(local, medicine("Cloud", 2, 300),
                        MedicineCloudImportPolicy.fingerprint(local)));
    }

    @Test public void fingerprintIncludesAllMedicineMasterFields() {
        MedicineEntity local = medicine("Analgesics", 2, 200);
        MedicineEntity differentCode = medicine("Analgesics", 2, 200);
        differentCode.setMedicineCode("000012345679");
        assertNotEquals(MedicineCloudImportPolicy.fingerprint(local),
                MedicineCloudImportPolicy.fingerprint(differentCode));
        MedicineEntity differentThreshold = medicine("Analgesics", 2, 200);
        differentThreshold.setTargetStockLevel(20);
        assertNotEquals(MedicineCloudImportPolicy.fingerprint(local),
                MedicineCloudImportPolicy.fingerprint(differentThreshold));
    }

    @Test public void fingerprintKeepsNullDistinctFromEmpty() {
        MedicineEntity local = medicine("Analgesics", 2, 200);
        MedicineEntity second = medicine("Analgesics", 2, 200);
        second.setGenericName("");
        assertNotEquals(MedicineCloudImportPolicy.fingerprint(local),
                MedicineCloudImportPolicy.fingerprint(second));
    }

    @Test public void changedActiveStateIsNotSilentlyAccepted() {
        MedicineEntity local = medicine("Analgesics", 2, 200);
        MedicineEntity modified = medicine("Analgesics", 2, 200);
        modified.setActive(false);
        assertNotEquals(MedicineCloudImportPolicy.fingerprint(local),
                MedicineCloudImportPolicy.fingerprint(modified));
    }
}
