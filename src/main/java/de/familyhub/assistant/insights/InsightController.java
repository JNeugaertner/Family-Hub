package de.familyhub.assistant.insights;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// Hinweise ohne KI (Phase 2): für den Streifen in der Übersicht und die Konflikt-Markierungen im Kalender
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "KI-Assistent")
public class InsightController {

    private final InsightService insights;
    private final AssistantAccess access;
    private final CurrentMember currentMember;
    private final Clock clock;

    public InsightController(InsightService insights, AssistantAccess access, CurrentMember currentMember,
            Clock clock) {
        this.insights = insights;
        this.access = access;
        this.currentMember = currentMember;
        this.clock = clock;
    }

    @GetMapping("/insights")
    @Operation(summary = "Hinweise für die Übersicht", description = "Terminkonflikte der nächsten 14 Tage, "
            + "überfällige Aufgaben und dringende Aufgaben, die heute fällig sind; ohne KI ermittelt. Jede Person "
            + "sieht, was sie selbst betrifft, Administratoren alles.")
    public List<Insight> insights() {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        return insights.insightsFor(viewer);
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Terminkonflikte", description = "Überschneidungen für dieselbe Person im Zeitraum, auch "
            + "mit offenen Vorschlägen; ganztägige Termine zählen nicht. Ohne from und to ab heute für ein Jahr.")
    public List<EventConflict> conflicts(
            @Parameter(description = "Beginn des Zeitraums, z. B. 2026-10-01T00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Ende des Zeitraums (exklusiv)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        if ((from == null) != (to == null)) {
            throw ApiException.invalidField(from == null ? "from" : "to", "from und to müssen zusammen angegeben werden");
        }
        if (from == null) {
            from = LocalDate.now(clock).atStartOfDay();
            to = from.plusYears(1);
        } else if (!to.isAfter(from)) {
            throw ApiException.invalidField("to", "to muss nach from liegen");
        }
        return insights.conflictsFor(viewer, from, to);
    }
}
