package com.agent.user.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * AES-256 encryptor for sensitive config values (e.g. API keys).
 * Uses a configurable password + salt via application properties.
 * Default salt is "deadbeef" (must be hex).
 */
@Component
public class ApiKeyEncryptor {

    private final TextEncryptor encryptor;

    public ApiKeyEncryptor(
            @Value("${app.encryption.password:change-me-in-production}") String password,
            @Value("${app.encryption.salt:deadbeef}") String salt) {
        this.encryptor = Encryptors.text(password, salt);
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return "";
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) return "";
        try {
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            // Return empty on corrupt/legacy unencrypted data
            return "";
        }
    }
}
