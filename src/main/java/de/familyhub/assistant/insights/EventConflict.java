package de.familyhub.assistant.insights;

import java.time.LocalDateTime;
import java.util.List;

import de.familyhub.calendar.CalendarEvent;

// Zwei Termine überschneiden sich für dieselben Personen (memberIds). first beginnt nicht später als second.
// Ist einer davon ein offener Vorschlag, zeigt der Konflikt, was beim Freigeben passieren würde.
public record EventConflict(List<String> memberIds, EventRef first, EventRef second) {

    public EventConflict {
        memberIds = List.copyOf(memberIds);
    }

    // Das Nötigste eines Termins für Hinweise und Kalender; proposal: offener Vorschlag
    public record EventRef(String id, String title, LocalDateTime start, LocalDateTime end, boolean proposal,
            String createdBy) {

        static EventRef of(CalendarEvent event) {
            return new EventRef(event.id(), event.title(), event.start(), event.end(), event.isProposal(),
                    event.createdBy());
        }
    }

    public boolean involvesProposal() {
        return first.proposal() || second.proposal();
    }

    // Der Vorschlag in diesem Konflikt, falls es einen gibt
    public EventRef proposal() {
        return first.proposal() ? first : second.proposal() ? second : null;
    }
}
