package com.cz.fitnessdiary.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.cz.fitnessdiary.utils.TextUtilsCompat;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only the refresh token, encrypted with an Android Keystore AES key. */
public final class TokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "fitnessdiary.cloudbase.refresh.v1";
    private static final String PREFS = "cloud_session";
    private static final String VALUE = "encrypted_refresh_token";
    private static final String ACCOUNT_EMAIL = "account_email";
    private static final String ACCOUNT_ID = "account_id";
    private static final int GCM_TAG_LENGTH = 128;
    private final SharedPreferences preferences;

    public TokenStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void saveRefreshToken(String refreshToken) throws Exception {
        if (TextUtilsCompat.isBlank(refreshToken)) {
            clear();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Android Keystore owns IV generation for this key. Some OEM keystore
        // providers reject an app-provided GCM IV with "Caller-provided IV not
        // permitted" even when the IV is random.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length == 0) {
            throw new IllegalStateException("Android Keystore did not generate a GCM IV");
        }
        byte[] encrypted = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        preferences.edit().putString(VALUE, Base64.encodeToString(payload, Base64.NO_WRAP)).apply();
    }

    public synchronized void saveSession(String email, String userId, String refreshToken) throws Exception {
        saveRefreshToken(refreshToken);
        preferences.edit()
                .putString(ACCOUNT_EMAIL, email == null ? "" : email)
                .putString(ACCOUNT_ID, userId == null ? "" : userId)
                .apply();
    }

    @Nullable
    public synchronized String getAccountEmail() {
        String value = preferences.getString(ACCOUNT_EMAIL, null);
        return TextUtilsCompat.isBlank(value) ? null : value;
    }

    @Nullable
    public synchronized String getAccountId() {
        String value = preferences.getString(ACCOUNT_ID, null);
        return TextUtilsCompat.isBlank(value) ? null : value;
    }

    @Nullable
    public synchronized String getRefreshToken() {
        String encoded = preferences.getString(VALUE, null);
        if (TextUtilsCompat.isBlank(encoded)) return null;
        try {
            byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
            if (payload.length <= 12) throw new IllegalStateException("Invalid encrypted refresh token");
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[payload.length - iv.length];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            clear();
            return null;
        }
    }

    public synchronized void clear() {
        preferences.edit().remove(VALUE).remove(ACCOUNT_EMAIL).remove(ACCOUNT_ID).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
