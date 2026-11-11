package de.familyhub.preferences;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Persönliche Anzeige-Einstellungen einer Person (Entscheidung vom 11.11.2026: jede Person entscheidet selbst, ob sie
// Feiertage sieht). null heißt "nie eingestellt" und gilt als an, damit neue Einstellungen für alle zunächst an sind.
@Document("memberPreferences")
public record MemberPreferences(
        @Id String memberId,
        Boolean holidayCountdown,
        Boolean holidaysInCalendar) {

    static MemberPreferences defaults(String memberId) {
        return new MemberPreferences(memberId, null, null);
    }

    public boolean showsHolidayCountdown() {
        return !Boolean.FALSE.equals(holidayCountdown);
    }

    public boolean showsHolidaysInCalendar() {
        return !Boolean.FALSE.equals(holidaysInCalendar);
    }
}
