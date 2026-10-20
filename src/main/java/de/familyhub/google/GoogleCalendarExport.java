package de.familyhub.google;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

// Trägt die FamilyHub-Termine einer Person in ihren Google-Kalender „FamilyHub“ ein (Entscheidung vom 19.10.2026):
// alle freigegebenen Termine, an denen sie beteiligt ist, auch gemeinsame und private; keine offenen Vorschläge und
// keine Termine, die selbst aus Google kommen. Abgeglichen wird wie beim Import das ganze Zeitfenster: fehlende
// Einträge anlegen, abweichende überschreiben, überzählige löschen. Einträge ohne FamilyHub-Id (in Google von Hand
// angelegt) bleiben stehen.
@Component
public class GoogleCalendarExport {

    static final String CALENDAR_NAME = "FamilyHub";
    static final String CALENDAR_DESCRIPTION = "Termine aus FamilyHub. Bitte in FamilyHub ändern: Änderungen hier "
            + "werden beim nächsten Abgleich überschrieben.";

    private final GoogleApi google;
    private final GoogleConnectionRepository connections;
    private final CalendarEventRepository events;
    private final FamilyMemberRepository members;
    private final CalendarAccess access;
    private final GoogleEventMapper mapper;
    private final Clock clock;

    public GoogleCalendarExport(GoogleApi google, GoogleConnectionRepository connections,
            CalendarEventRepository events, FamilyMemberRepository members, CalendarAccess access,
            GoogleEventMapper mapper, Clock clock) {
        this.google = google;
        this.connections = connections;
        this.events = events;
        this.members = members;
        this.access = access;
        this.mapper = mapper;
        this.clock = clock;
    }

    GoogleConnection createCalendar(GoogleConnection connection, String accessToken) {
        GoogleApi.CalendarInfo calendar = google.createCalendar(accessToken, CALENDAR_NAME, CALENDAR_DESCRIPTION,
                clock.getZone().getId());
        return connections.save(connection.withExportCalendar(calendar.id()));
    }

    // Liefert die Verbindung zurück; sie ändert sich, wenn der Kalender „FamilyHub“ neu angelegt werden musste.
    GoogleConnection export(GoogleConnection connection, String accessToken, Instant from, Instant to) {
        if (!connection.exportEnabled()) {
            return connection;
        }
        FamilyMember member = members.findById(connection.memberId()).orElse(null);
        if (member == null) {
            return connection;
        }
        List<GoogleEvent> inGoogle;
        try {
            inGoogle = google.listEvents(accessToken, connection.exportCalendarId(), from, to);
        } catch (GoogleException e) {
            if (!e.notFound()) {
                throw e;
            }
            // Jemand hat den Kalender in Google gelöscht; solange das Eintragen eingeschaltet ist, neu anlegen
            connection = createCalendar(connection, accessToken);
            inGoogle = List.of();
        }
        String calendarId = connection.exportCalendarId();

        Map<String, GoogleEvent> byFamilyhubId = new HashMap<>();
        List<GoogleEvent> stale = new ArrayList<>();
        for (GoogleEvent entry : inGoogle) {
            String familyhubId = entry.familyhubId();
            if (familyhubId != null && byFamilyhubId.putIfAbsent(familyhubId, entry) != null) {
                stale.add(entry);
            }
        }
        for (CalendarEvent event : wanted(member, from, to)) {
            GoogleEvent entry = byFamilyhubId.remove(event.id());
            if (entry == null) {
                google.insertEvent(accessToken, calendarId, mapper.toGoogle(event));
            } else if (!mapper.isUpToDate(entry, event)) {
                google.updateEvent(accessToken, calendarId, entry.id(), mapper.toGoogle(event));
            }
        }
        stale.addAll(byFamilyhubId.values());
        for (GoogleEvent entry : stale) {
            google.deleteEvent(accessToken, calendarId, entry.id());
        }
        return connection;
    }

    // Die Termine, die im Zeitfenster in Google stehen sollen. Die normalen Sichtbarkeitsregeln gelten auch hier.
    private List<CalendarEvent> wanted(FamilyMember member, Instant from, Instant to) {
        LocalDateTime windowStart = LocalDateTime.ofInstant(from, clock.getZone());
        LocalDateTime windowEnd = LocalDateTime.ofInstant(to, clock.getZone());
        Predicate<CalendarEvent> visible = access.visibilityFor(member);
        return events.findOwnByMember(member.id()).stream()
                .filter(event -> !event.isProposal())
                .filter(visible)
                .filter(event -> event.start().isBefore(windowEnd) && event.end().isAfter(windowStart))
                .toList();
    }
}
