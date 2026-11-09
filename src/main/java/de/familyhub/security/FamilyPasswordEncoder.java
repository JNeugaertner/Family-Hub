package de.familyhub.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

// Passwort-Hashes (Issues #1 und #2): BCrypt verarbeitet höchstens 72 Bytes und lehnt längere Passwörter ab, erlaubt
// sind aber bis zu 100 Zeichen, und Umlaute oder Emojis brauchen mehrere Bytes. Längere Passwörter werden deshalb
// zuerst mit SHA-256 verdichtet (Base64, 44 Zeichen) und dann wie gewohnt mit BCrypt gehasht, gespeichert mit der
// Kennung {bcrypt-sha256}. Kürzere bleiben unverändert {bcrypt}, damit alle bestehenden Passwörter weiter passen.
public class FamilyPasswordEncoder implements PasswordEncoder {

    static final int BCRYPT_MAX_BYTES = 72;
    static final String PRE_HASHED = "{bcrypt-sha256}";

    private final PasswordEncoder standard = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword.toString().getBytes(UTF_8).length <= BCRYPT_MAX_BYTES) {
            return standard.encode(rawPassword);
        }
        return PRE_HASHED + bcrypt.encode(preHash(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword != null && encodedPassword.startsWith(PRE_HASHED)) {
            return bcrypt.matches(preHash(rawPassword), encodedPassword.substring(PRE_HASHED.length()));
        }
        return standard.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith(PRE_HASHED)
                && standard.upgradeEncoding(encodedPassword);
    }

    private static String preHash(CharSequence rawPassword) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawPassword.toString().getBytes(UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ist in jeder Java-Laufzeit vorhanden", e);
        }
    }
}
