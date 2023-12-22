package com.taluttasgiran.rnsecurestorage.cipherStorage;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.Entity;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.react.bridge.AssertionException;
import com.facebook.react.bridge.ReactApplicationContext;
import com.taluttasgiran.rnsecurestorage.RNSecureStorageModule.KnownCiphers;
import com.taluttasgiran.rnsecurestorage.SecurityLevel;
import com.taluttasgiran.rnsecurestorage.exceptions.CryptoFailedException;

import java.security.GeneralSecurityException;
import java.security.Key;

@SuppressWarnings({"unused", "WeakerAccess"})
public class CipherStorageFacebookConceal extends CipherStorageBase {
    public static final String KEYCHAIN_DATA = "RN_SECURE_STORAGE";

    private final Crypto crypto;

    public CipherStorageFacebookConceal(@NonNull final ReactApplicationContext reactContext) {
        KeyChain keyChain = new SharedPrefsBackedKeyChain(reactContext, CryptoConfig.KEY_256);

        this.crypto = AndroidConceal.get().createDefaultCrypto(keyChain);
    }

    //region Configuration
    @Override
    public String getCipherStorageName() {
        return KnownCiphers.FB;
    }

    @Override
    public int getMinSupportedApiLevel() {
        return Build.VERSION_CODES.JELLY_BEAN;
    }

    @Override
    public SecurityLevel securityLevel() {
        return SecurityLevel.ANY;
    }

    @Override
    public boolean supportsSecureHardware() {
        return false;
    }

    @Override
    public boolean isBiometrySupported() {
        return false;
    }
    //endregion

    //region Overrides
    @Override
    @NonNull
    public EncryptionResult encrypt(@NonNull final String alias,
                                    @NonNull final String value,
                                    @NonNull final SecurityLevel level)
            throws CryptoFailedException {

        throwIfInsufficientLevel(level);
        throwIfNoCryptoAvailable();

        final Entity usernameEntity = createUsernameEntity(alias);
        final Entity valueEntity = createValueEntity(alias);

        try {
            final byte[] encryptedValue = crypto.encrypt(value.getBytes(UTF8), valueEntity);

            return new EncryptionResult(
                    encryptedValue,
                    this);
        } catch (Throwable fail) {
            throw new CryptoFailedException("Encryption failed for alias: " + alias, fail);
        }
    }

    @NonNull
    @Override
    public DecryptionResult decrypt(@NonNull final String alias,
                                    @NonNull final byte[] value,
                                    @NonNull final SecurityLevel level)
            throws CryptoFailedException {

        throwIfInsufficientLevel(level);
        throwIfNoCryptoAvailable();

        final Entity valueEntity = createValueEntity(alias);

        try {
            final byte[] decryptedValue = crypto.decrypt(value, valueEntity);

            return new DecryptionResult(
                    new String(decryptedValue, UTF8),
                    SecurityLevel.ANY);
        } catch (Throwable fail) {
            throw new CryptoFailedException("Decryption failed for alias: " + alias, fail);
        }
    }

    @Override
    public void decrypt(@NonNull DecryptionResultHandler handler,
                        @NonNull String service,
                        @NonNull byte[] value,
                        @NonNull final SecurityLevel level) {

        try {
            final DecryptionResult results = decrypt(service, value, level);

            handler.onDecrypt(results, null);
        } catch (Throwable fail) {
            handler.onDecrypt(null, fail);
        }
    }

    @Override
    public void removeKey(@NonNull final String alias) {
        // Facebook Conceal stores only one key across all services, so we cannot
        // delete the key (otherwise decryption will fail for encrypted data of other services).
        Log.w(LOG_TAG, "CipherStorageFacebookConceal removeKey called. alias: " + alias);
    }

    @NonNull
    @Override
    protected KeyGenParameterSpec.Builder getKeyGenSpecBuilder(@NonNull final String alias)
            throws GeneralSecurityException {
        throw new CryptoFailedException("Not designed for a call");
    }

    @NonNull
    @Override
    protected KeyInfo getKeyInfo(@NonNull final Key key) throws GeneralSecurityException {
        throw new CryptoFailedException("Not designed for a call");
    }

    @NonNull
    @Override
    protected Key generateKey(@NonNull final KeyGenParameterSpec spec) throws GeneralSecurityException {
        throw new CryptoFailedException("Not designed for a call");
    }

    @NonNull
    @Override
    protected String getEncryptionAlgorithm() {
        throw new AssertionException("Not designed for a call");
    }

    @NonNull
    @Override
    protected String getEncryptionTransformation() {
        throw new AssertionException("Not designed for a call");
    }

    /**
     * Verify availability of the Crypto API.
     */
    private void throwIfNoCryptoAvailable() throws CryptoFailedException {
        if (!crypto.isAvailable()) {
            throw new CryptoFailedException("Crypto is missing");
        }
    }
    //endregion

    //region Helper methods
    @NonNull
    private static Entity createUsernameEntity(@NonNull final String alias) {
        final String prefix = getEntityPrefix(alias);

        return Entity.create(prefix + "user");
    }

    @NonNull
    private static Entity createValueEntity(@NonNull final String alias) {
        final String prefix = getEntityPrefix(alias);

        return Entity.create(prefix + "pass");
    }

    @NonNull
    private static String getEntityPrefix(@NonNull final String alias) {
        return KEYCHAIN_DATA + ":" + alias;
    }
    //endregion
}
