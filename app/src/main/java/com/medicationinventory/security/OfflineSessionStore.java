package com.medicationinventory.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.annotation.SuppressLint;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;




/**
 * Persists the UID and timestamp of the last server-validated offline enrollment as authenticated ciphertext in private preferences. AES-GCM encryption uses an Android Keystore key; malformed, undecryptable, future-dated, or expired records fail closed.
 */
public final class OfflineSessionStore {
    public static final long MAX_OFFLINE_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final String KEY_ALIAS = "mediflow_offline_enrollment_v1";
    private static final String PREF_FILE = "mediflow_offline_session";
    private static final String PREF_ENROLLED = "enrolled";
    private final SharedPreferences prefs;


    public OfflineSessionStore(Context context) {
        this(context, PREF_FILE);
    }

    // Test-only alternate preference file; never share production enrollment state.
    OfflineSessionStore(Context context, String preferenceFile) {
        prefs = context.getApplicationContext().getSharedPreferences(preferenceFile, Context.MODE_PRIVATE);
    }


    /**
     * Saves an enrollment only when its server timestamp is recent, positive, and not in the future.
     * The UID and validation time are encrypted together so the record cannot be replayed for another account.
     * @param uid account whose profile was validated
     * @param serverValidatedAt server-provided validation time in epoch milliseconds
     * @return false when validation is stale or encryption/storage fails; failures clear any prior enrollment
     */
    public synchronized boolean enroll(String uid, long serverValidatedAt) {
        long now = System.currentTimeMillis();
        if (uid == null || uid.trim().isEmpty() || serverValidatedAt <= 0
                || serverValidatedAt > now || now - serverValidatedAt > 60_000L) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != 12) {
                throw new IllegalStateException("Android Keystore did not provide a 12-byte GCM IV");
            }
            byte[] bytes = (uid + "\n" + serverValidatedAt).getBytes(StandardCharsets.UTF_8);
            String value = Base64.encodeToString(iv, Base64.NO_WRAP) + "."
                    + Base64.encodeToString(cipher.doFinal(bytes), Base64.NO_WRAP);
            return prefs.edit().putString(PREF_ENROLLED, value).commit();
        } catch (Exception e) {
            clear();
            return false;
        }
    }

    /**
     * Decrypts the saved enrollment and checks account binding and the maximum offline age.
     * A clock rollback or any parse, key, or authentication failure is treated as ineligible.
     * @param uid account requesting offline access
     * @return true only for a matching, unexpired enrollment
     */
    public synchronized boolean isEligible(String uid) {
        if (uid == null || uid.isEmpty()) return false;
        try {
            String value = prefs.getString(PREF_ENROLLED, null);
            if (value == null) return false;
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2) return false;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            if (iv.length != 12) return false;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            String text = new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
            int separator = text.lastIndexOf('\n');
            if (separator <= 0 || !uid.equals(text.substring(0, separator))) return false;
            long validatedAt = Long.parseLong(text.substring(separator + 1));
            long now = System.currentTimeMillis();
            // Fail closed on a backwards clock or expired validation.
            return validatedAt > 0 && now >= validatedAt
                    && now - validatedAt <= MAX_OFFLINE_AGE_MS;
        } catch (Exception e) {
            return false;
        }
    }


    @SuppressLint("ApplySharedPref")
    public synchronized void clear() {

        prefs.edit().remove(PREF_ENROLLED).commit();
    }

    /** Loads the app-specific Android Keystore key, creating a randomized AES-GCM key on first use. */
    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
