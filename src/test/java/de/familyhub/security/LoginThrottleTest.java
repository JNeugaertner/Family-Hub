package de.familyhub.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Schutz gegen das Durchprobieren von Passwörtern (29.09.2026): 5 Fehlversuche innerhalb von 5 Minuten sperren das
// Konto für 5 Minuten; eine erfolgreiche Anmeldung setzt die Zählung zurück.
@SpringBootTest
@AutoConfigureMockMvc
class LoginThrottleTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private LoginAttempts attempts;

    @BeforeEach
    void setUp() {
        attempts.reset();
        members.deleteAll();
        members.save(TestUsers.member("Emma", Role.KIND));
        members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password));
    }

    @Test
    void fifthWrongPasswordLocksTheAccountEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("emma", "falsch").andExpect(status().isUnauthorized());
        }
        login("emma", "falsch")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail")
                        .value("Zu viele falsche Passwörter für dieses Konto. Bitte in 5 Minuten erneut versuchen."));

        login("emma", TestUsers.PASSWORD).andExpect(status().isTooManyRequests());
        // Groß- und Kleinschreibung umgeht die Sperre nicht
        login("Emma", TestUsers.PASSWORD).andExpect(status().isTooManyRequests());
        // Andere Konten sind nicht betroffen
        login("lucas", TestUsers.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheCount() throws Exception {
        for (int i = 0; i < 4; i++) {
            login("emma", "falsch").andExpect(status().isUnauthorized());
        }
        login("emma", TestUsers.PASSWORD).andExpect(status().isOk());

        login("emma", "falsch").andExpect(status().isUnauthorized());
    }

    // Uhr, die der Test vorstellen kann
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-09-29T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Berlin");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void lockEndsAfterTheLockDurationAndOldFailuresExpire() {
        TestClock clock = new TestClock();
        LoginAttempts limiter = new LoginAttempts(new LoginProperties(5, Duration.ofMinutes(5)), clock);

        for (int i = 0; i < 4; i++) {
            assertThat(limiter.failed("emma")).isFalse();
        }
        // Der fünfte Fehlversuch kommt erst nach mehr als 5 Minuten: die alten zählen nicht mehr
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.failed("emma")).isFalse();
        assertThat(limiter.lockedFor("emma")).isEmpty();

        for (int i = 0; i < 4; i++) {
            limiter.failed("emma");
        }
        assertThat(limiter.lockedFor("emma")).contains(Duration.ofMinutes(5));
        clock.advance(Duration.ofMinutes(3));
        assertThat(limiter.lockedFor("emma")).contains(Duration.ofMinutes(2));
        clock.advance(Duration.ofMinutes(2));
        assertThat(limiter.lockedFor("emma")).isEmpty();
    }
}
