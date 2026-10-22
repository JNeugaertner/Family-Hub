package de.familyhub.assistant.tools;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

// Zeitraum aus den Argumenten "von" und "bis" (beide einschließlich, JJJJ-MM-TT). Ohne Angaben ab heute.
record DateRange(LocalDate first, LocalDate last) {

    static DateRange of(ToolArguments args, LocalDate today, int defaultDays, int maxDays) {
        LocalDate first = args.date("von").orElse(today);
        LocalDate last = args.date("bis").orElse(first.plusDays(defaultDays - 1L));
        if (last.isBefore(first)) {
            throw new ToolException("bis darf nicht vor von liegen.");
        }
        if (ChronoUnit.DAYS.between(first, last) >= maxDays) {
            throw new ToolException("Höchstens " + maxDays + " Tage auf einmal; bitte den Zeitraum aufteilen.");
        }
        return new DateRange(first, last);
    }

    LocalDateTime start() {
        return first.atStartOfDay();
    }

    // exklusiv: Beginn des Tages nach "bis"
    LocalDateTime end() {
        return last.plusDays(1).atStartOfDay();
    }

    Map<String, Object> describe() {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("von", first.toString());
        range.put("bis", last.toString());
        return range;
    }

    // JSON-Schema-Eigenschaften für "von" und "bis"
    static Map<String, Object> schemaProperties() {
        return Map.of(
                "von", Map.of("type", "string", "description", "Erster Tag im Format JJJJ-MM-TT; ohne Angabe heute"),
                "bis", Map.of("type", "string", "description", "Letzter Tag (einschließlich) im Format JJJJ-MM-TT"));
    }
}
