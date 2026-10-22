package de.familyhub.assistant.insights;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

// Ein Hinweis für die Übersicht, ohne KI ermittelt. text ist fertig formuliert und spricht die angemeldete Person
// mit "du" an, wenn es um sie selbst geht. eventIds bzw. taskId sind Sprungziele für die Oberfläche.
public record Insight(Type type, LocalDate date, String text, List<String> memberIds, List<String> eventIds,
        String taskId) {

    public enum Type {
        // zwei Termine derselben Person überschneiden sich
        @JsonProperty("conflict") CONFLICT,
        // ein offener Vorschlag überschneidet sich mit einem Termin
        @JsonProperty("proposal_conflict") PROPOSAL_CONFLICT,
        // eine Aufgabe ist nicht erledigt und ihre Frist vorbei
        @JsonProperty("task_overdue") TASK_OVERDUE,
        // eine dringende Aufgabe ist heute fällig
        @JsonProperty("task_due_today") TASK_DUE_TODAY
    }

    public Insight {
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        eventIds = eventIds == null ? List.of() : List.copyOf(eventIds);
    }
}
