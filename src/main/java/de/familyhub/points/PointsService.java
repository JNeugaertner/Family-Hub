package de.familyhub.points;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.task.Task;
import de.familyhub.web.ApiException;

@Service
public class PointsService {

    private final PointEntryRepository entries;
    private final Clock clock;

    public PointsService(PointEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    // Schreibt die Punkte einer bestätigten Aufgabe dem zugewiesenen Mitglied gut, höchstens einmal je Aufgabe.
    public PointEntry awardForTask(Task task, FamilyMember confirmedBy) {
        PointEntry entry = new PointEntry(null, task.assigneeId(), task.points(), "Aufgabe erledigt: " + task.title(),
                task.id(), LocalDateTime.now(clock), confirmedBy.id());
        try {
            return entries.save(entry);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Die Punkte für diese Aufgabe wurden bereits gutgeschrieben.");
        }
    }

    // Buchung ohne Aufgabe, z. B. Abzug für eine eingelöste Belohnung (negativ) oder die Rückbuchung (positiv).
    public PointEntry book(String memberId, int amount, String reason, String createdBy) {
        return entries.save(new PointEntry(null, memberId, amount, reason, null, LocalDateTime.now(clock), createdBy));
    }

    public int balance(String memberId) {
        return entries.findByMemberIdOrderByCreatedAtDesc(memberId).stream().mapToInt(PointEntry::amount).sum();
    }

    public Map<String, Integer> balances() {
        return entries.findAll().stream()
                .collect(Collectors.groupingBy(PointEntry::memberId, Collectors.summingInt(PointEntry::amount)));
    }

    public List<PointEntry> history(String memberId) {
        return memberId == null
                ? entries.findAllByOrderByCreatedAtDesc()
                : entries.findByMemberIdOrderByCreatedAtDesc(memberId);
    }
}
