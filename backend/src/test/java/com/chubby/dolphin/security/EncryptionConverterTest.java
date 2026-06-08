package com.chubby.dolphin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EncryptionConverterTest {

    private EncryptionConverter converter;
    private final String testKey = "ChubbyDolphinEncryptionKeySecret32!";

    @BeforeEach
    public void setUp() {
        converter = new EncryptionConverter(testKey);
    }

    @Test
    public void testEncryptionAndDecryptionSuccess() {
        String originalToken = "EAAGzDdh7m5oBOZBEZAaZCsDZCZA1234567890abcdefghijklmnopqrstuvwxyz";
        
        // Encrypt
        String encrypted = converter.convertToDatabaseColumn(originalToken);
        assertNotNull(encrypted);
        assertNotEquals(originalToken, encrypted);
        
        // Decrypt
        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(originalToken, decrypted);
    }

    @Test
    public void testEncryptionWithNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute(""));
    }

    @Test
    public void testInvalidKeyInitialization() {
        // Must fail if key is too short
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptionConverter("short_key");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new EncryptionConverter(null);
        });
    }

    @Test
    public void testDecryptionFailureWithCorruptedData() {
        assertThrows(RuntimeException.class, () -> {
            converter.convertToEntityAttribute("InvalidBase64Str!!!");
        });
        
        // Too short payload
        assertThrows(RuntimeException.class, () -> {
            converter.convertToEntityAttribute("YWJj"); // "abc"
        });
    }
}
