package de.familyhub.family;

// Gemeinsame Eingaberegeln für Anlegen, Ändern und Ersteinrichtung.
public final class MemberRules {

    public static final String COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    public static final String USERNAME_PATTERN = "^[a-z0-9._-]{3,30}$";
    public static final String USERNAME_MESSAGE =
            "Benutzername: 3 bis 30 Zeichen, nur Kleinbuchstaben, Ziffern, Punkt, Unterstrich oder Bindestrich";

    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final String PASSWORD_MESSAGE = "Passwort muss mindestens 8 Zeichen lang sein";

    private MemberRules() {
    }
}
