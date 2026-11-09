package de.familyhub.whatsapp;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Prüft, ob ein Aufruf des Webhooks wirklich von Meta kommt: Meta signiert den unveränderten Body mit dem
// App-Secret (HMAC-SHA256) und schickt das Ergebnis im Header X-Hub-Signature-256 als "sha256=<hex>". Ohne diese
// Prüfung könnte jeder, der die öffentliche Adresse kennt, Nachrichten einschleusen (Entscheidung vom 20.10.2026).
@Component
public class WhatsAppSignature {

    static final String HEADER = "X-Hub-Signature-256";
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final WhatsAppProperties properties;

    public WhatsAppSignature(WhatsAppProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(byte[] body, String header) {
        if (!StringUtils.hasText(properties.appSecret()) || body == null || header == null
                || !header.startsWith(PREFIX)) {
            return false;
        }
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Zeitkonstanter Vergleich, damit die Antwortzeit nichts über die richtige Signatur verrät
        return MessageDigest.isEqual(sign(properties.appSecret(), body), given);
    }

    static byte[] sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), ALGORITHM));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 ist nicht verfügbar", e);
        }
    }
}
