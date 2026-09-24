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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;

// Beispielfamilie und -termine aus dem Figma-UI (frontend/src/components/data.ts), dazu eine Oma als Gast.
// Nur für Entwicklung und Tests: alle Beispielkonten haben dasselbe, öffentlich bekannte Passwort.
@Component
@ConditionalOnProperty(name = "familyhub.sample-data.enabled", havingValue = "true", matchIfMissing = true)
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    // Im Figma-UI haben einige Termine keine Endzeit; das Backend verlangt eine.
    static final Duration DEFAULT_DURATION = Duration.ofHours(1);

    public static final String SAMPLE_PASSWORD = "familyhub";

    private record SampleMember(String name, String color, Role role, String birthDate) {
    }

    // Emma ist als Kind hinterlegt und gilt durch den Altersübergang ab 13 automatisch als Jugendliche.
    private static final List<SampleMember> MEMBERS = List.of(
            new SampleMember("Sarah", "#2563EB", Role.ADMINISTRATOR, null),
            new SampleMember("Mike", "#14B8A6", Role.ADMINISTRATOR, null),
            new SampleMember("Emma", "#8B5CF6", Role.KIND, "2010-02-14"),
            new SampleMember("Lucas", "#F97316", Role.KIND, "2014-05-03"),
            new SampleMember("Lily", "#EC4899", Role.KIND, "2018-01-30"),
            new SampleMember("Oma", "#64748B", Role.GAST, null));

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
    private final PasswordEncoder passwordEncoder;

    public SampleDataLoader(FamilyMemberRepository memberRepository, CalendarEventRepository eventRepository,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
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

        String passwordHash = passwordEncoder.encode(SAMPLE_PASSWORD);
        List<FamilyMember> members = MEMBERS.stream()
                .map(m -> new FamilyMember(null, m.name(), m.color(), m.name().toLowerCase(), passwordHash, m.role(),
                        m.birthDate() == null ? null : LocalDate.parse(m.birthDate()), false))
                .toList();
        Map<String, String> idByName = memberRepository.saveAll(members).stream()
                .collect(Collectors.toMap(FamilyMember::name, FamilyMember::id));
        eventRepository.saveAll(EVENTS.stream().map(e -> toEvent(e, idByName)).toList());

        log.warn("Beispieldaten angelegt: {} Konten (Benutzername = Vorname in Kleinbuchstaben, Passwort \"{}\") "
                + "und {} Termine. Nur für Entwicklung, für echten Betrieb familyhub.sample-data.enabled=false setzen.",
                MEMBERS.size(), SAMPLE_PASSWORD, EVENTS.size());
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
