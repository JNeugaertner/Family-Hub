package de.familyhub.achievements;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.permission.RoleResolver;
import de.familyhub.points.PointsService;
import de.familyhub.task.Task;

// Erfolge (Entscheidungen vom 28.09.2026): automatisch nach Regeln, nur für Kinder und Jugendliche, gezählt ab
// jetzt (nicht rückwirkend). Beim Erreichen gibt es den Bonus als Punkte-Buchung "Erfolg: <Name>".
@Service
public class AchievementService {

    private final AchievementRepository achievements;
    private final AchievementProgressRepository progress;
    private final EarnedAchievementRepository earned;
    private final FamilyMemberRepository members;
    private final RoleResolver roles;
    private final PointsService points;
    private final Clock clock;

    public AchievementService(AchievementRepository achievements, AchievementProgressRepository progress,
            EarnedAchievementRepository earned, FamilyMemberRepository members, RoleResolver roles,
            PointsService points, Clock clock) {
        this.achievements = achievements;
        this.progress = progress;
        this.earned = earned;
        this.members = members;
        this.roles = roles;
        this.points = points;
        this.clock = clock;
    }

    // Erfolge sammeln nur Kinder und Jugendliche (wie beim Punktesystem).
    public boolean collects(FamilyMember member) {
        Role role = roles.effectiveRole(member);
        return role == Role.KIND || role == Role.JUGENDLICHER;
    }

    // Nach der Bestätigung einer Aufgabe: Zähler fortschreiben, neu erreichte Erfolge vergeben.
    public List<EarnedAchievement> onTaskConfirmed(Task task, FamilyMember confirmedBy) {
        FamilyMember assignee = members.findById(task.assigneeId()).orElse(null);
        if (assignee == null || !collects(assignee)) {
            return List.of();
        }
        AchievementProgress updated = progress.save(progressOf(assignee.id())
                .withConfirmedTask(task, LocalDate.now(clock)));
        return awardReached(updated, achievements.findByActiveTrue(), confirmedBy);
    }

    // Nach einer Änderung im Katalog (z. B. niedrigeres Ziel): alle Kinder erneut prüfen.
    public void recheck(Achievement achievement, FamilyMember changedBy) {
        if (!achievement.active()) {
            return;
        }
        progress.findAll().forEach(p -> awardReached(p, List.of(achievement), changedBy));
    }

    public AchievementProgress progressOf(String memberId) {
        return progress.findByMemberId(memberId).orElseGet(() -> AchievementProgress.empty(memberId));
    }

    public void forgetMember(String memberId) {
        progress.deleteByMemberId(memberId);
        earned.deleteByMemberId(memberId);
    }

    private List<EarnedAchievement> awardReached(AchievementProgress current, List<Achievement> candidates,
            FamilyMember awardedBy) {
        return candidates.stream()
                .filter(a -> current.valueFor(a) >= a.target())
                .filter(a -> !earned.existsByMemberIdAndAchievementId(current.memberId(), a.id()))
                .map(a -> award(current.memberId(), a, awardedBy))
                .filter(e -> e != null)
                .toList();
    }

    private EarnedAchievement award(String memberId, Achievement achievement, FamilyMember awardedBy) {
        EarnedAchievement result;
        try {
            result = earned.save(new EarnedAchievement(null, memberId, achievement.id(), achievement.name(),
                    achievement.icon(), achievement.bonus(), LocalDateTime.now(clock)));
        } catch (DuplicateKeyException e) {
            return null; // gleichzeitig schon vergeben
        }
        if (achievement.bonus() > 0) {
            points.book(memberId, achievement.bonus(), "Erfolg: " + achievement.name(), awardedBy.id());
        }
        return result;
    }
}
