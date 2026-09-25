package de.familyhub.sampledata;

import static de.familyhub.calendar.EventCategory.APPOINTMENT;
import static de.familyhub.calendar.EventCategory.FAMILY;
import static de.familyhub.calendar.EventCategory.REMINDER;
import static de.familyhub.calendar.EventCategory.SCHOOL;
import static de.familyhub.calendar.EventCategory.SPORTS;
import static de.familyhub.calendar.EventCategory.WORK;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.settings.FamilySettings;
import de.familyhub.settings.FamilySettingsRepository;

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

    // day: Tag relativ zum Montag der aktuellen Woche (0 = Montag, 7 = Montag der Folgewoche), damit der Kalender
    // beim ersten Start immer Termine rund um heute zeigt. privateEvent: nur Beteiligte und Administratoren sehen den
    // Termin; proposedBy: offener Vorschlag dieser Person
    private record SampleEvent(String title, int day, String time, String endTime,
            String member, EventCategory category, String location, boolean privateEvent, String proposedBy) {

        SampleEvent(String title, int day, String time, String endTime, String member, EventCategory category,
                String location) {
            this(title, day, time, endTime, member, category, location, false, null);
        }
    }

    private static final List<SampleEvent> EVENTS = List.of(
            new SampleEvent("School pickup", 0, "15:00", "15:30", "Lucas", SCHOOL, "Lincoln Middle School"),
            new SampleEvent("Soccer practice", 0, "16:30", "18:00", "Emma", SPORTS, "City Sports Complex"),
            new SampleEvent("Team standup", 1, "09:00", "09:30", "Mike", WORK, null),
            new SampleEvent("Piano lesson", 1, "15:30", "16:30", "Lily", SCHOOL, "Music Academy"),
            new SampleEvent("Dentist – Lucas", 2, "11:00", "12:00", "Lucas", APPOINTMENT, "Bright Smile Dental"),
            new SampleEvent("Family dinner", 5, "18:00", "20:00", "Sarah", FAMILY, null),
            new SampleEvent("Parent-teacher conf.", 3, "14:00", "15:00", "Sarah", SCHOOL, "Lincoln Elementary"),
            new SampleEvent("Basketball game", 4, "10:00", "12:00", "Lucas", SPORTS, "Sports Center"),
            new SampleEvent("Doctor checkup", 7, "10:00", "11:00", "Lily", APPOINTMENT, null),
            new SampleEvent("Work presentation", 8, "14:00", null, "Mike", WORK, null),
            new SampleEvent("Gymnastics", 3, "14:30", "15:30", "Lily", SPORTS, null),
            new SampleEvent("Book club", 6, "19:00", null, "Sarah", FAMILY, null, true, null),
            new SampleEvent("🗑️ Gelber Sack", 1, "07:00", null, "Mike", REMINDER, null),
            new SampleEvent("Movie night", 4, "20:00", null, "Sarah", FAMILY, null),
            new SampleEvent("Grocery run", 2, "09:00", null, "Mike", FAMILY, null),
            new SampleEvent("Park cycle tour", 6, "10:00", "12:00", "Sarah", FAMILY, null),
            new SampleEvent("Kinoabend mit Lucas", 5, "19:00", "21:00", "Lucas", FAMILY, "Cinestar", false,
                    "Emma"));

    // Termine dieser Kategorien sehen Gäste (sofern nicht privat)
    static final Set<EventCategory> GUEST_CATEGORIES = Set.of(FAMILY, SCHOOL);

    private final FamilyMemberRepository memberRepository;
    private final CalendarEventRepository eventRepository;
    private final FamilySettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public SampleDataLoader(FamilyMemberRepository memberRepository, CalendarEventRepository eventRepository,
            FamilySettingsRepository settingsRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
        LocalDate monday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        eventRepository.saveAll(EVENTS.stream().map(e -> toEvent(e, monday, idByName)).toList());
        settingsRepository.save(new FamilySettings(FamilySettings.ID, GUEST_CATEGORIES));

        log.warn("Beispieldaten angelegt: {} Konten (Benutzername = Vorname in Kleinbuchstaben, Passwort \"{}\") "
                + "und {} Termine. Nur für Entwicklung, für echten Betrieb familyhub.sample-data.enabled=false setzen.",
                MEMBERS.size(), SAMPLE_PASSWORD, EVENTS.size());
    }

    private static CalendarEvent toEvent(SampleEvent e, LocalDate monday, Map<String, String> idByName) {
        LocalDate date = monday.plusDays(e.day());
        LocalDateTime start = date.atTime(LocalTime.parse(e.time()));
        LocalDateTime end = e.endTime() == null
                ? start.plus(DEFAULT_DURATION)
                : date.atTime(LocalTime.parse(e.endTime()));
        boolean proposal = e.proposedBy() != null;
        return new CalendarEvent(null, e.title(), start, end, idByName.get(e.member()), e.category(), e.location(),
                null, e.privateEvent(), proposal ? EventStatus.PROPOSED : EventStatus.APPROVED,
                idByName.get(proposal ? e.proposedBy() : "Sarah"));
    }
}
