package de.familyhub.google;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;

// Merkt sich, dass sich FamilyHub-Termine geändert haben; GoogleCalendarSync.pushPending trägt sie kurz darauf in
// die Google-Kalender „FamilyHub“ ein. Hört auf die Datenbank statt auf die API, damit jeder Weg zählt (Termin
// anlegen, Vorschlag freigeben, Mitglied löschen, …). Termine aus dem Google-Import werden nicht eingetragen.
@Component
class CalendarEventChanges extends AbstractMongoEventListener<CalendarEvent> {

    private final GoogleCalendarSync sync;

    CalendarEventChanges(GoogleCalendarSync sync) {
        this.sync = sync;
    }

    @Override
    public void onAfterSave(AfterSaveEvent<CalendarEvent> event) {
        if (!event.getSource().isExternal()) {
            sync.markPending();
        }
    }

    // Beim Löschen ist nur noch die Id bekannt; ein unnötiger Durchgang ändert in Google nichts.
    @Override
    public void onAfterDelete(AfterDeleteEvent<CalendarEvent> event) {
        sync.markPending();
    }
}
