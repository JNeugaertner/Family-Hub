package de.familyhub.sampledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

@DataMongoTest
class SampleDataLoaderTest {

    @Autowired
    private FamilyMemberRepository memberRepository;

    @Autowired
    private CalendarEventRepository eventRepository;

    private SampleDataLoader loader;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        eventRepository.deleteAll();
        loader = new SampleDataLoader(memberRepository, eventRepository);
    }

    @Test
    void loadsFigmaFamilyAndAllEvents() {
        loader.load();

        assertThat(memberRepository.findAll())
                .extracting(FamilyMember::name)
                .containsExactlyInAnyOrder("Sarah", "Mike", "Emma", "Lucas", "Lily");
        assertThat(eventRepository.count()).isEqualTo(16);
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
    void secondLoadDoesNotDuplicateOrOverwriteData() {
        loader.load();
        loader.load();

        assertThat(memberRepository.count()).isEqualTo(5);
        assertThat(eventRepository.count()).isEqualTo(16);
    }

    @Test
    void existingDataIsLeftUntouched() {
        memberRepository.save(new FamilyMember(null, "Eigene Familie", "#000000"));

        loader.load();

        assertThat(memberRepository.findAll()).extracting(FamilyMember::name).containsExactly("Eigene Familie");
        assertThat(eventRepository.count()).isZero();
    }
}
