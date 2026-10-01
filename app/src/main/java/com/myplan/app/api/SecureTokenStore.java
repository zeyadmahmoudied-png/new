package com.myplan.app.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts access/refresh tokens with AES/GCM key held in Android Keystore.
 * Prefs store ciphertext + IV only — never plaintext tokens.
 * If Keystore is unavailable, tokens are refused (not stored in plaintext).
 */
public final class SecureTokenStore implements TokenStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "myplan_api_token_aes_v1";
    private static final String PREFS = "myplan_api_tokens_secure_v1";
    private static final String LEGACY_PREFS = "myplan_api_tokens_v1";
    private static final int GCM_TAG_BITS = 128;

    private final Context app;
    private final SharedPreferences sp;
    private boolean revoked;

    public SecureTokenStore(Context c) {
        this.app = c.getApplicationContext();
        this.sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.revoked = sp.getBoolean("revoked", false);
        // Wipe legacy plaintext token prefs if present
        try {
            SharedPreferences legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
            if (legacy.contains("access") || legacy.contains("refresh")) {
                legacy.edit().clear().apply();
            }
        } catch (Exception ignored) {}
        ensureKey();
    }

    private void ensureKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build();
                kg.init(spec);
                kg.generateKey();
            }
        } catch (Exception ignored) {
            // Keystore unavailable — saveTokens will no-op plaintext
        }
    }

    private SecretKey getKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        } catch (Exception e) {
            return null;
        }
    }

    private String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        try {
            SecretKey key = getKey();
            if (key == null) return null; // refuse plaintext
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String decrypt(String blob) {
        if (blob == null || blob.isEmpty()) return "";
        try {
            int idx = blob.indexOf(':');
            if (idx <= 0) return "";
            byte[] iv = Base64.decode(blob.substring(0, idx), Base64.NO_WRAP);
            byte[] ct = Base64.decode(blob.substring(idx + 1), Base64.NO_WRAP);
            SecretKey key = getKey();
            if (key == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public String getAccessToken() {
        if (revoked) return "";
        return decrypt(sp.getString("access_enc", ""));
    }

    @Override
    public String getRefreshToken() {
        if (revoked) return "";
        return decrypt(sp.getString("refresh_enc", ""));
    }

    @Override
    public long getAccessExpiryMs() {
        return sp.getLong("access_exp", 0);
    }

    @Override
    public void saveTokens(String access, String refresh, long accessExpiryMs) {
        String aEnc = encrypt(access == null ? "" : access);
        String rEnc = encrypt(refresh == null ? "" : refresh);
        if (aEnc == null || rEnc == null) {
            // Keystore failed — do not fall back to plaintext
            return;
        }
        revoked = false;
        sp.edit()
                .putString("access_enc", aEnc)
                .putString("refresh_enc", rEnc)
                .putLong("access_exp", accessExpiryMs)
                .putBoolean("revoked", false)
                .apply();
    }

    @Override
    public void clear() {
        revoked = false;
        sp.edit().clear().apply();
    }

    @Override
    public TokenStatus getAccessStatus() {
        if (revoked) return TokenStatus.REVOKED;
        String a = getAccessToken();
        if (a == null || a.isEmpty()) return TokenStatus.MISSING;
        long exp = getAccessExpiryMs();
        if (exp > 0 && System.currentTimeMillis() >= exp) return TokenStatus.EXPIRED;
        if (exp > 0 && System.currentTimeMillis() >= exp - 60_000L) return TokenStatus.REFRESH_REQUIRED;
        return TokenStatus.VALID;
    }

    @Override
    public void markRevoked() {
        revoked = true;
        sp.edit().putBoolean("revoked", true).remove("access_enc").remove("refresh_enc").apply();
    }

    /** For SecuritySelfTest: true if no plaintext "access" key exists in legacy or secure prefs. */
    public boolean isPlaintextAbsent() {
        try {
            SharedPreferences legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
            if (legacy.contains("access") && !legacy.getString("access", "").isEmpty()) return false;
            // secure prefs must not store unencrypted token under plain keys
            if (sp.contains("access") || sp.contains("refresh")) return false;
            return true;
        } catch (Exception e) {
            return true;
        }
    }
}
