package de.familyhub.notifications;

import static de.familyhub.notifications.NotificationTexts.day;
import static de.familyhub.notifications.NotificationTexts.dayAndTime;
import static de.familyhub.notifications.NotificationTexts.quoted;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.meals.MealAccess;
import de.familyhub.meals.MealEntryRepository;
import de.familyhub.meals.MealStatus;
import de.familyhub.rewards.RedemptionRepository;
import de.familyhub.rewards.RedemptionStatus;
import de.familyhub.rewards.RewardAccess;
import de.familyhub.shopping.ShoppingAccess;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;

// Lange ausstehende Bestätigungen (Entscheidung vom 21.10.2026): Wartet etwas seit 24 Stunden
// (familyhub.notifications.pending-after) auf eine Bestätigung, erinnert FamilyHub einmal alle, die es bestätigen
// dürfen. Offen sein können erledigte Aufgaben mit Punkten, Terminvorschläge, Einkaufsvorschläge, Essenswünsche und
// eingelöste Belohnungen.
// Die Wartezeit zählt ab dem Moment, in dem FamilyHub den offenen Punkt zum ersten Mal sieht (Abgleich alle
// 15 Minuten, familyhub.notifications.pending-scan), weil erledigte Aufgaben und Terminvorschläge keinen Zeitstempel
// haben. Was beim ersten Start schon offen ist, löst deshalb erst nach der Wartezeit eine Erinnerung aus.
// Wird ein Punkt erledigt und später wieder offen (z. B. zurückgegebene Aufgabe), beginnt die Wartezeit neu.
@Component
public class PendingConfirmations {

    // Ein offener Punkt: wie er in der Erinnerung heißt und wer ihn bestätigen darf
    private record Open(String key, String label, Predicate<FamilyMember> decides) {
    }

    private final Notifications notifications;
    private final PendingConfirmationRepository pending;
    private final FamilyMemberRepository members;
    private final TaskRepository tasks;
    private final CalendarEventRepository events;
    private final ShoppingItemRepository shopping;
    private final MealEntryRepository meals;
    private final RedemptionRepository redemptions;
    private final TaskAccess taskAccess;
    private final CalendarAccess calendarAccess;
    private final ShoppingAccess shoppingAccess;
    private final MealAccess mealAccess;
    private final RewardAccess rewardAccess;
    private final NotificationProperties properties;
    private final Clock clock;

    public PendingConfirmations(Notifications notifications, PendingConfirmationRepository pending,
            FamilyMemberRepository members, TaskRepository tasks, CalendarEventRepository events,
            ShoppingItemRepository shopping, MealEntryRepository meals, RedemptionRepository redemptions,
            TaskAccess taskAccess, CalendarAccess calendarAccess, ShoppingAccess shoppingAccess, MealAccess mealAccess,
            RewardAccess rewardAccess, NotificationProperties properties, Clock clock) {
        this.notifications = notifications;
        this.pending = pending;
        this.members = members;
        this.tasks = tasks;
        this.events = events;
        this.shopping = shopping;
        this.meals = meals;
        this.redemptions = redemptions;
        this.taskAccess = taskAccess;
        this.calendarAccess = calendarAccess;
        this.shoppingAccess = shoppingAccess;
        this.mealAccess = mealAccess;
        this.rewardAccess = rewardAccess;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${familyhub.notifications.pending-scan:PT15M}",
            initialDelayString = "${familyhub.notifications.pending-scan:PT15M}")
    public synchronized void remind() {
        Instant now = clock.instant();
        List<FamilyMember> family = members.findAll();
        Map<String, String> names = new HashMap<>();
        family.forEach(member -> names.put(member.id(), member.name()));
        List<Open> open = openItems(names);
        Set<String> openKeys = open.stream().map(Open::key).collect(Collectors.toSet());
        Map<String, PendingConfirmation> known = pending.findAll().stream()
                .collect(Collectors.toMap(PendingConfirmation::key, Function.identity()));
        pending.deleteAll(known.values().stream().filter(p -> !openKeys.contains(p.key())).toList());

        List<Open> due = new ArrayList<>();
        for (Open item : open) {
            PendingConfirmation seen = known.get(item.key());
            if (seen == null) {
                pending.save(new PendingConfirmation(item.key(), now, false));
            } else if (!seen.reminded() && !seen.since().plus(properties.pendingAfter()).isAfter(now)) {
                due.add(item);
                pending.save(seen.remindedNow());
            }
        }
        if (due.isEmpty()) {
            return;
        }
        String heading = "⏳ Seit über " + NotificationTexts.duration(properties.pendingAfter())
                + " offen, bitte bestätigen oder ablehnen:";
        for (FamilyMember member : family) {
            List<String> lines = due.stream().filter(item -> item.decides().test(member)).map(Open::label).toList();
            if (!lines.isEmpty()) {
                notifications.send(member, NotificationKind.CONFIRMATIONS, NotificationTexts.list(heading, lines));
            }
        }
    }

    private List<Open> openItems(Map<String, String> names) {
        LocalDate today = LocalDate.now(clock);
        List<Open> open = new ArrayList<>();
        tasks.findByStatus(TaskStatus.DONE).stream().filter(Task::isAwaitingConfirmation)
                .forEach(task -> open.add(new Open("task:" + task.id(),
                        "Aufgabe " + quoted(task.title()) + ", erledigt" + by(task.assigneeId(), names),
                        taskAccess::mayConfirm)));
        events.findByStatus(EventStatus.PROPOSED)
                .forEach(event -> open.add(new Open("event:" + event.id(),
                        "Terminvorschlag " + quoted(event.title()) + " " + dayAndTime(event.start(), today)
                                + by(event.createdBy(), names),
                        calendarAccess::mayDecide)));
        shopping.findByStatus(ShoppingItemStatus.PROPOSED)
                .forEach(item -> open.add(new Open("shopping:" + item.id(),
                        "Einkaufsvorschlag " + quoted(item.name()) + by(item.createdBy(), names),
                        shoppingAccess::mayDecide)));
        meals.findByStatus(MealStatus.PROPOSED)
                .forEach(meal -> open.add(new Open("meal:" + meal.id(),
                        "Essenswunsch " + quoted(meal.name()) + " " + day(meal.date(), today)
                                + by(meal.createdBy(), names),
                        mealAccess::mayDecide)));
        redemptions.findByStatus(RedemptionStatus.PENDING)
                .forEach(redemption -> open.add(new Open("redemption:" + redemption.id(),
                        "Belohnung " + quoted(redemption.rewardName()) + " für "
                                + names.getOrDefault(redemption.memberId(), "ein ehemaliges Mitglied"),
                        rewardAccess::mayDecide)));
        return open;
    }

    private static String by(String memberId, Map<String, String> names) {
        String name = memberId == null ? null : names.get(memberId);
        return name == null ? "" : " von " + name;
    }
}
