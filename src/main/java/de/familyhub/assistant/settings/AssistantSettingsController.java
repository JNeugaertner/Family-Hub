package de.familyhub.assistant.settings;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.assistant.AssistantCall;
import de.familyhub.assistant.AssistantCallRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// Admin-Karte des KI-Assistenten (Phase 5): Einstellungen und Verbrauch. Nur für Administratoren.
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "KI-Assistent")
public class AssistantSettingsController {

    // So viele Tage (mit heute) zeigt die Admin-Karte
    static final int USAGE_DAYS = 7;

    private final AssistantSettingsService settings;
    private final AssistantQuota quota;
    private final AssistantCallRepository calls;
    private final FamilyMemberRepository members;
    private final AssistantAccess access;
    private final CurrentMember currentMember;
    private final Clock clock;

    public AssistantSettingsController(AssistantSettingsService settings, AssistantQuota quota,
            AssistantCallRepository calls, FamilyMemberRepository members, AssistantAccess access,
            CurrentMember currentMember, Clock clock) {
        this.settings = settings;
        this.quota = quota;
        this.calls = calls;
        this.members = members;
        this.access = access;
        this.currentMember = currentMember;
        this.clock = clock;
    }

    // calls: Aufrufe beim Anbieter (eine Frage braucht oft mehrere), failedCalls: davon fehlgeschlagen
    public record MemberUsage(String memberId, String name, int questions, int calls, int failedCalls,
            long inputTokens, long outputTokens) {
    }

    public record Usage(LocalDate from, LocalDate to, List<MemberUsage> members) {
    }

    @GetMapping("/settings")
    @Operation(summary = "Einstellungen des KI-Assistenten", description = "Nur für Administratoren.")
    public AssistantSettings get() {
        access.requireManage(currentMember.get());
        return settings.current();
    }

    @PutMapping("/settings")
    @Operation(summary = "Einstellungen des KI-Assistenten ändern", description = "Nur für Administratoren. Ein/Aus "
            + "für alle, Bereiche, automatische Auswertung von WhatsApp und Telegram, Tageslimit je Rolle.")
    public AssistantSettings update(@Valid @RequestBody AssistantSettings changed) {
        access.requireManage(currentMember.get());
        return settings.update(changed);
    }

    @GetMapping("/usage")
    @Operation(summary = "Verbrauch der letzten 7 Tage", description = "Nur für Administratoren. Fragen, Aufrufe "
            + "beim Anbieter und Tokens je Person, ohne Inhalte.")
    public Usage usage() {
        access.requireManage(currentMember.get());
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(USAGE_DAYS - 1L);
        Map<String, Integer> questions = quota.since(from).stream()
                .collect(Collectors.groupingBy(AssistantUsage::memberId,
                        Collectors.summingInt(AssistantUsage::questions)));
        Map<String, List<AssistantCall>> callsByMember = calls
                .findByAtGreaterThanEqual(from.atStartOfDay(clock.getZone()).toInstant()).stream()
                .collect(Collectors.groupingBy(AssistantCall::memberId));
        List<MemberUsage> rows = members.findAll().stream()
                .filter(m -> questions.containsKey(m.id()) || callsByMember.containsKey(m.id()))
                .map(m -> row(m, questions.getOrDefault(m.id(), 0), callsByMember.getOrDefault(m.id(), List.of())))
                .sorted(Comparator.comparing(MemberUsage::questions).reversed().thenComparing(MemberUsage::name))
                .toList();
        return new Usage(from, today, rows);
    }

    private static MemberUsage row(FamilyMember member, int questions, List<AssistantCall> calls) {
        return new MemberUsage(member.id(), member.name(), questions, calls.size(),
                (int) calls.stream().filter(c -> !c.ok()).count(),
                calls.stream().mapToLong(AssistantCall::inputTokens).sum(),
                calls.stream().mapToLong(AssistantCall::outputTokens).sum());
    }
}
