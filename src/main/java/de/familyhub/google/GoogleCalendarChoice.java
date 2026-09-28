package de.familyhub.google;

import de.familyhub.calendar.EventCategory;

// Ein Kalender des verbundenen Google-Kontos: ob er übernommen wird und in welcher FamilyHub-Kategorie.
public record GoogleCalendarChoice(String calendarId, String name, boolean primary, boolean enabled,
        EventCategory category) {

    public GoogleCalendarChoice withName(String newName, boolean newPrimary) {
        return new GoogleCalendarChoice(calendarId, newName, newPrimary, enabled, category);
    }
}
