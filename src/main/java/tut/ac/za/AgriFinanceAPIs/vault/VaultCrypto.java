package tut.ac.za.AgriFinanceAPIs.vault;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for files in the document vault (IDs, certificates, bank statements).
 * Stored format: 12-byte random IV followed by ciphertext and authentication tag. Tampering is detected on read.
 */
public final class VaultCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKey key;

    /**
     * @param base64Key a base64-encoded 32-byte key (APP_VAULT_KEY). When blank, a key is derived from the fallback
     *                  secret so development works without extra setup; production should always set a real key.
     */
    public VaultCrypto(String base64Key, String fallbackSecret) {
        byte[] raw;
        if (base64Key != null && !base64Key.isBlank()) {
            raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) throw new IllegalArgumentException("APP_VAULT_KEY must be 32 bytes, base64-encoded");
        } else {
            raw = sha256(("vault:" + (fallbackSecret == null ? "" : fallbackSecret)).getBytes(StandardCharsets.UTF_8));
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(plain);
            byte[] out = Arrays.copyOf(iv, iv.length + enc.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt document", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        try {
            byte[] iv = Arrays.copyOfRange(stored, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return c.doFinal(stored, 12, stored.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Document could not be decrypted (wrong key or the file was altered)", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(sha256(data));
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
