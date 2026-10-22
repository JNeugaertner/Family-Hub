package de.familyhub.assistant.insights;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;

// Terminkonflikte ohne KI (Entscheidung vom 22.10.2026): Zwei Termine überschneiden sich, wenn dieselbe Person an
// beiden beteiligt ist und sich die Zeiten überlappen; direkt aneinander anschließende Termine zählen nicht.
// Offene Vorschläge werden gegen die übrigen Termine geprüft, nicht gegeneinander. Ganztägige Termine (00:00 bis
// 00:00, z. B. Urlaub oder Geburtstage aus Google) zählen nicht, sonst stünde an solchen Tagen alles im Konflikt.
@Component
public class ConflictFinder {

    public List<EventConflict> find(Collection<CalendarEvent> events) {
        Map<String, List<CalendarEvent>> byMember = new LinkedHashMap<>();
        for (CalendarEvent event : events) {
            if (event.id() != null && !isAllDay(event)) {
                event.memberIds().forEach(id -> byMember.computeIfAbsent(id, k -> new ArrayList<>()).add(event));
            }
        }
        // Dasselbe Terminpaar für mehrere Personen ist ein Konflikt mit mehreren Beteiligten
        Map<String, Pair> pairs = new LinkedHashMap<>();
        byMember.forEach((memberId, own) -> {
            own.sort(Comparator.comparing(CalendarEvent::start).thenComparing(CalendarEvent::id));
            for (int i = 0; i < own.size(); i++) {
                CalendarEvent first = own.get(i);
                for (int j = i + 1; j < own.size() && own.get(j).start().isBefore(first.end()); j++) {
                    CalendarEvent second = own.get(j);
                    if (first.isProposal() && second.isProposal()) {
                        continue;
                    }
                    pairs.computeIfAbsent(first.id() + "|" + second.id(), k -> new Pair(first, second))
                            .memberIds.add(memberId);
                }
            }
        });
        return pairs.values().stream()
                .map(p -> new EventConflict(List.copyOf(p.memberIds), EventConflict.EventRef.of(p.first),
                        EventConflict.EventRef.of(p.second)))
                .sorted(Comparator.comparing((EventConflict c) -> c.first().start())
                        .thenComparing(c -> c.second().start()))
                .toList();
    }

    public static boolean isAllDay(CalendarEvent event) {
        return event.start().toLocalTime().equals(LocalTime.MIDNIGHT)
                && event.end().toLocalTime().equals(LocalTime.MIDNIGHT) && event.end().isAfter(event.start());
    }

    private static final class Pair {

        private final CalendarEvent first;
        private final CalendarEvent second;
        private final Set<String> memberIds = new LinkedHashSet<>();

        private Pair(CalendarEvent first, CalendarEvent second) {
            this.first = first;
            this.second = second;
        }
    }
}
