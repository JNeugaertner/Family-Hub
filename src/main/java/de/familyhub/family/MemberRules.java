package de.familyhub.family;

import de.familyhub.web.ApiException;

// Gemeinsame Eingaberegeln für Anlegen, Ändern und Ersteinrichtung.
public final class MemberRules {

    public static final String COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    public static final String USERNAME_PATTERN = "^[a-z0-9._-]{3,30}$";
    public static final String USERNAME_MESSAGE =
            "Benutzername: 3 bis 30 Zeichen, nur Kleinbuchstaben, Ziffern, Punkt, Unterstrich oder Bindestrich";

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 100;
    public static final String PASSWORD_MESSAGE = "Passwort muss 8 bis 100 Zeichen lang sein";

    public static final String PHONE_MESSAGE = "Handynummer mit Ländervorwahl angeben, z. B. +49 170 1234567";
    // Ohne Ländervorwahl (führende 0) gilt die Nummer als deutsche
    private static final String DEFAULT_COUNTRY_CODE = "49";

    private MemberRules() {
    }

    // Bringt eine Handynummer in die Form, in der WhatsApp den Absender liefert: nur Ziffern mit Ländervorwahl,
    // ohne + oder 00, z. B. "+49 170 123-4567" und "0170 1234567" zu "491701234567". Leer ergibt null.
    public static String normalizePhone(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String phone = input.replaceAll("[\\s()./-]", "");
        if (!phone.matches("\\+?\\d+")) {
            throw ApiException.invalidField("phone", PHONE_MESSAGE);
        }
        if (phone.startsWith("+")) {
            phone = phone.substring(1);
        } else if (phone.startsWith("00")) {
            phone = phone.substring(2);
        } else if (phone.startsWith("0")) {
            phone = DEFAULT_COUNTRY_CODE + phone.substring(1);
        }
        // E.164: höchstens 15 Ziffern; kürzere als 8 sind keine Handynummern
        if (phone.length() < 8 || phone.length() > 15) {
            throw ApiException.invalidField("phone", PHONE_MESSAGE);
        }
        return phone;
    }
}
