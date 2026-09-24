package de.familyhub.sampledata;

import static de.familyhub.calendar.EventCategory.APPOINTMENT;
import static de.familyhub.calendar.EventCategory.FAMILY;
import static de.familyhub.calendar.EventCategory.REMINDER;
import static de.familyhub.calendar.EventCategory.SCHOOL;
import static de.familyhub.calendar.EventCategory.SPORTS;
import static de.familyhub.calendar.EventCategory.WORK;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

// Beispielfamilie und -termine aus dem Figma-UI (FamilyHub-UI/src/components/data.ts).
@Component
@ConditionalOnProperty(name = "familyhub.sample-data.enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    // Im Figma-UI haben einige Termine keine Endzeit; das Backend verlangt eine.
    static final Duration DEFAULT_DURATION = Duration.ofHours(1);

    private static final List<FamilyMember> MEMBERS = List.of(
            new FamilyMember(null, "Sarah", "#2563EB"),
            new FamilyMember(null, "Mike", "#14B8A6"),
            new FamilyMember(null, "Emma", "#8B5CF6"),
            new FamilyMember(null, "Lucas", "#F97316"),
            new FamilyMember(null, "Lily", "#EC4899"));

    private record SampleEvent(String title, String date, String time, String endTime,
            String member, EventCategory category, String location) {
    }

    private static final List<SampleEvent> EVENTS = List.of(
            new SampleEvent("School pickup", "2026-09-21", "15:00", "15:30", "Lucas", SCHOOL, "Lincoln Middle School"),
            new SampleEvent("Soccer practice", "2026-09-21", "16:30", "18:00", "Emma", SPORTS, "City Sports Complex"),
            new SampleEvent("Team standup", "2026-09-22", "09:00", "09:30", "Mike", WORK, null),
            new SampleEvent("Piano lesson", "2026-09-22", "15:30", "16:30", "Lily", SCHOOL, "Music Academy"),
            new SampleEvent("Dentist – Lucas", "2026-09-23", "11:00", "12:00", "Lucas", APPOINTMENT, "Bright Smile Dental"),
            new SampleEvent("Family dinner", "2026-09-26", "18:00", "20:00", "Sarah", FAMILY, null),
            new SampleEvent("Parent-teacher conf.", "2026-09-24", "14:00", "15:00", "Sarah", SCHOOL, "Lincoln Elementary"),
            new SampleEvent("Basketball game", "2026-09-25", "10:00", "12:00", "Lucas", SPORTS, "Sports Center"),
            new SampleEvent("Doctor checkup", "2026-09-28", "10:00", "11:00", "Lily", APPOINTMENT, null),
            new SampleEvent("Work presentation", "2026-09-29", "14:00", null, "Mike", WORK, null),
            new SampleEvent("Gymnastics", "2026-09-24", "14:30", "15:30", "Lily", SPORTS, null),
            new SampleEvent("Book club", "2026-09-27", "19:00", null, "Sarah", FAMILY, null),
            new SampleEvent("🗑️ Gelber Sack", "2026-09-22", "07:00", null, "Mike", REMINDER, null),
            new SampleEvent("Movie night", "2026-09-25", "20:00", null, "Sarah", FAMILY, null),
            new SampleEvent("Grocery run", "2026-09-23", "09:00", null, "Mike", FAMILY, null),
            new SampleEvent("Park cycle tour", "2026-09-27", "10:00", "12:00", "Sarah", FAMILY, null));

    private final FamilyMemberRepository memberRepository;
    private final CalendarEventRepository eventRepository;

    public SampleDataLoader(FamilyMemberRepository memberRepository, CalendarEventRepository eventRepository) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    public void load() {
        if (memberRepository.count() > 0) {
            log.info("Beispieldaten übersprungen: Die Datenbank enthält bereits Familienmitglieder.");
            return;
        }

        Map<String, String> idByName = memberRepository.saveAll(MEMBERS).stream()
                .collect(Collectors.toMap(FamilyMember::name, FamilyMember::id));
        eventRepository.saveAll(EVENTS.stream().map(e -> toEvent(e, idByName)).toList());

        log.info("Beispieldaten angelegt: {} Familienmitglieder, {} Termine.", MEMBERS.size(), EVENTS.size());
    }

    private static CalendarEvent toEvent(SampleEvent e, Map<String, String> idByName) {
        LocalDate date = LocalDate.parse(e.date());
        LocalDateTime start = date.atTime(LocalTime.parse(e.time()));
        LocalDateTime end = e.endTime() == null
                ? start.plus(DEFAULT_DURATION)
                : date.atTime(LocalTime.parse(e.endTime()));
        return new CalendarEvent(null, e.title(), start, end, idByName.get(e.member()),
                e.category(), e.location(), null);
    }
}
