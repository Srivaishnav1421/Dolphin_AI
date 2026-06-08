package com.chubby.dolphin.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Production-ready AES-GCM-256 JPA Attribute Converter.
 * Automatically encrypts sensitive database columns (like access tokens) using AES-GCM-256,
 * and transparently decrypts them on retrieval.
 * Supports zero-downtime key rotation and fallback decryption for older AES-CBC encrypted values.
 */
@Converter
@Component
public class EncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM_GCM = "AES/GCM/NoPadding";
    private static final String ALGORITHM_CBC = "AES/CBC/PKCS5Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // in bits

    private final byte[] keyBytes;
    private final byte[] oldKeyBytes;

    @Autowired
    public EncryptionConverter(@Value("${meta.encryption.key}") String key,
                               @Value("${meta.encryption.old-key:}") String oldKey) {
        if (key == null || key.length() < 32) {
            throw new IllegalArgumentException("Encryption key (meta.encryption.key) must be at least 32 characters (256 bits) long.");
        }
        this.keyBytes = key.substring(0, 32).getBytes();

        if (oldKey != null && oldKey.length() >= 32) {
            this.oldKeyBytes = oldKey.substring(0, 32).getBytes();
        } else {
            this.oldKeyBytes = null;
        }
    }

    public EncryptionConverter(String key) {
        this(key, null);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM_GCM);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] encryptedBytes = cipher.doFinal(attribute.getBytes());
            
            // Combine IV and encrypted bytes
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);
            
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting value using AES-GCM-256", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }

        // Try decrypting in order:
        // 1. Current key, GCM
        // 2. Old key, GCM (if configured)
        // 3. Current key, CBC
        // 4. Old key, CBC (if configured)

        try {
            return decryptGcm(dbData, keyBytes);
        } catch (Exception eGcmCurrent) {
            if (oldKeyBytes != null) {
                try {
                    return decryptGcm(dbData, oldKeyBytes);
                } catch (Exception eGcmOld) {
                    // Ignore, try CBC next
                }
            }

            try {
                return decryptCbc(dbData, keyBytes);
            } catch (Exception eCbcCurrent) {
                if (oldKeyBytes != null) {
                    try {
                        return decryptCbc(dbData, oldKeyBytes);
                    } catch (Exception eCbcOld) {
                        throw new RuntimeException("Failed to decrypt value. All keys and algorithms exhausted.", eCbcOld);
                    }
                }
                throw new RuntimeException("Failed to decrypt value. All keys and algorithms exhausted.", eCbcCurrent);
            }
        }
    }

    private String decryptGcm(String dbData, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(dbData);
        if (combined.length < GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Invalid GCM encrypted data length.");
        }
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        
        byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM_GCM);
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    private String decryptCbc(String dbData, byte[] key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(dbData);
        if (combined.length < 16) {
            throw new IllegalArgumentException("Invalid CBC encrypted data length.");
        }
        
        byte[] iv = new byte[16];
        System.arraycopy(combined, 0, iv, 0, 16);
        
        byte[] encryptedBytes = new byte[combined.length - 16];
        System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.length);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC);
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }
}
