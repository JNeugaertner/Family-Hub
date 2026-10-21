package de.familyhub.notifications;

import static de.familyhub.notifications.NotificationTexts.dayAndTime;
import static de.familyhub.notifications.NotificationTexts.quoted;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

// Kurzfristige Termine (Entscheidung vom 21.10.2026): Ein Termin, der noch nicht vorbei ist und innerhalb der
// nächsten 24 Stunden beginnt (familyhub.notifications.short-notice), wird angelegt, freigegeben oder verschoben:
// Dann erfahren es alle Beteiligten. Verschoben zählt auch, wenn der Termin aus diesem Zeitraum heraus verschoben
// wird. Kommt jemand zu einem kurzfristigen Termin dazu, erfährt es nur diese Person.
// Vorschläge zählen erst ab der Freigabe, aus Google übernommene Termine gar nicht (die kennt die Person schon).
// Wer die Änderung selbst gemacht hat, bekommt keine Nachricht.
@Component
public class EventNotifications {

    private final Notifications notifications;
    private final FamilyMemberRepository members;
    private final CalendarAccess access;
    private final NotificationProperties properties;
    private final Clock clock;

    public EventNotifications(Notifications notifications, FamilyMemberRepository members, CalendarAccess access,
            NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.members = members;
        this.access = access;
        this.properties = properties;
        this.clock = clock;
    }

    // before: Stand vor dem Speichern, null bei neuen Terminen
    public void saved(CalendarEvent before, CalendarEvent after, FamilyMember actor) {
        if (after.isProposal() || after.isExternal()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (before == null || before.isProposal()) {
            if (isSoon(after, now)) {
                send(after.memberIds(), newEvent(after, now), after, actor);
            }
        } else if (!after.start().equals(before.start())) {
            if (isSoon(after, now) || isSoon(before, now)) {
                send(after.memberIds(), "📅 Termin verschoben: " + quoted(after.title()) + " ist jetzt "
                        + dayAndTime(after.start(), now.toLocalDate()) + " statt "
                        + dayAndTime(before.start(), now.toLocalDate()) + location(after), after, actor);
            }
        } else if (isSoon(after, now)) {
            List<String> added = after.memberIds().stream().filter(id -> !before.involves(id)).toList();
            send(added, newEvent(after, now), after, actor);
        }
    }

    private void send(List<String> memberIds, String text, CalendarEvent event, FamilyMember actor) {
        for (String id : memberIds) {
            if (actor == null || !actor.id().equals(id)) {
                members.findById(id)
                        .filter(member -> access.canSee(member, event))
                        .ifPresent(member -> notifications.send(member, NotificationKind.EVENTS, text));
            }
        }
    }

    private boolean isSoon(CalendarEvent event, LocalDateTime now) {
        return event.end().isAfter(now) && event.start().isBefore(now.plus(properties.shortNotice()));
    }

    private static String newEvent(CalendarEvent event, LocalDateTime now) {
        return "📅 Neuer Termin " + dayAndTime(event.start(), now.toLocalDate()) + ": " + quoted(event.title())
                + location(event);
    }

    private static String location(CalendarEvent event) {
        return event.location() == null || event.location().isBlank() ? "" : " (" + event.location().strip() + ")";
    }
}
