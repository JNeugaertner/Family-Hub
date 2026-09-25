package de.familyhub.sampledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.settings.FamilySettingsRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@DataMongoTest
class SampleDataLoaderTest {

    @Autowired
    private FamilyMemberRepository memberRepository;

    @Autowired
    private CalendarEventRepository eventRepository;

    @Autowired
    private FamilySettingsRepository settingsRepository;

    private static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    // Mittwoch, 07.10.2026: Die Beispielwoche beginnt am Montag, 05.10.2026
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneId.of("Europe/Berlin"));

    private SampleDataLoader loader;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        eventRepository.deleteAll();
        settingsRepository.deleteAll();
        loader = new SampleDataLoader(memberRepository, eventRepository, settingsRepository, PASSWORD_ENCODER, CLOCK);
    }

    @Test
    void eventsLieInTheCurrentWeek() {
        loader.load();

        List<CalendarEvent> events = eventRepository.findAll();
        assertThat(events).filteredOn(e -> e.title().equals("School pickup"))
                .singleElement()
                .satisfies(e -> assertThat(e.start()).isEqualTo(LocalDateTime.of(2026, 10, 5, 15, 0)));
        assertThat(events).allSatisfy(e -> assertThat(e.start().toLocalDate())
                .isBetween(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 13)));
    }

    @Test
    void loadsFigmaFamilyAndAllEvents() {
        loader.load();

        assertThat(memberRepository.findAll())
                .extracting(FamilyMember::name)
                .containsExactlyInAnyOrder("Sarah", "Mike", "Emma", "Lucas", "Lily", "Oma");
        assertThat(eventRepository.count()).isEqualTo(17);
    }

    @Test
    void sampleAccountsHaveRolesAndTheDocumentedPassword() {
        loader.load();

        FamilyMember sarah = memberRepository.findByUsername("sarah").orElseThrow();
        FamilyMember emma = memberRepository.findByUsername("emma").orElseThrow();
        FamilyMember oma = memberRepository.findByUsername("oma").orElseThrow();

        assertThat(sarah.role()).isEqualTo(Role.ADMINISTRATOR);
        assertThat(emma.role()).isEqualTo(Role.KIND);
        assertThat(emma.birthDate()).hasToString("2010-02-14");
        assertThat(oma.role()).isEqualTo(Role.GAST);
        assertThat(PASSWORD_ENCODER.matches(SampleDataLoader.SAMPLE_PASSWORD, sarah.passwordHash())).isTrue();
    }

    @Test
    void everyEventIsValidAndBelongsToAnExistingMember() {
        loader.load();

        Set<String> memberIds = memberRepository.findAll().stream()
                .map(FamilyMember::id)
                .collect(Collectors.toSet());
        List<CalendarEvent> events = eventRepository.findAll();

        assertThat(events).extracting(CalendarEvent::memberId).allMatch(memberIds::contains);
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(events).allSatisfy(e -> assertThat(factory.getValidator().validate(e)).isEmpty());
        }
    }

    @Test
    void eventsWithoutEndTimeGetDefaultDuration() {
        loader.load();

        CalendarEvent bookClub = eventRepository.findAll().stream()
                .filter(e -> e.title().equals("Book club"))
                .findFirst()
                .orElseThrow();

        assertThat(bookClub.end()).isEqualTo(bookClub.start().plus(SampleDataLoader.DEFAULT_DURATION));
    }

    @Test
    void containsProposalPrivateEventAndGuestCategoriesForTryingOutRoles() {
        loader.load();

        String emmaId = memberRepository.findByUsername("emma").orElseThrow().id();
        List<CalendarEvent> events = eventRepository.findAll();
        assertThat(events).filteredOn(CalendarEvent::isProposal)
                .singleElement()
                .satisfies(e -> assertThat(e.createdBy()).isEqualTo(emmaId));
        assertThat(events).filteredOn(CalendarEvent::privateEvent)
                .extracting(CalendarEvent::title)
                .containsExactly("Book club");
        assertThat(events).filteredOn(e -> e.status() == EventStatus.APPROVED).hasSize(16);
        assertThat(settingsRepository.current().guestCategories())
                .containsExactlyInAnyOrder(EventCategory.FAMILY, EventCategory.SCHOOL);
    }

    @Test
    void secondLoadDoesNotDuplicateOrOverwriteData() {
        loader.load();
        loader.load();

        assertThat(memberRepository.count()).isEqualTo(6);
        assertThat(eventRepository.count()).isEqualTo(17);
    }

    @Test
    void existingDataIsLeftUntouched() {
        memberRepository.save(new FamilyMember(null, "Eigene Familie", "#000000", "eigene", null, Role.ADMINISTRATOR,
                null, false));

        loader.load();

        assertThat(memberRepository.findAll()).extracting(FamilyMember::name).containsExactly("Eigene Familie");
        assertThat(eventRepository.count()).isZero();
    }
}
