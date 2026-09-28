package de.familyhub.google;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

// Verschlüsselt die Refresh-Tokens (AES-GCM), damit ein Blick in die Datenbank keinen Zugriff auf Google erlaubt.
@Component
public class TokenCipher {

    // Hex-Salt für die Schlüsselableitung; geheim ist nur der Token-Schlüssel aus local.properties.
    private static final String SALT = "46616d696c79487562";

    private final GoogleProperties properties;

    public TokenCipher(GoogleProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String token) {
        return encryptor().encrypt(token);
    }

    public String decrypt(String encrypted) {
        return encryptor().decrypt(encrypted);
    }

    private TextEncryptor encryptor() {
        return Encryptors.delux(properties.tokenKey(), SALT);
    }
}
