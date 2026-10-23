package de.familyhub.security;

import static de.familyhub.testsupport.TestUsers.as;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;
import tools.jackson.databind.json.JsonMapper;

// Issues #1 und #2: Passwörter über 72 Bytes (BCrypt-Grenze) gaben HTTP 500. Erlaubt sind 8 bis 100 Zeichen, auch mit
// Umlauten und Emojis, die mehrere Bytes brauchen.
@SpringBootTest
@AutoConfigureMockMvc
class LongPasswordTest {

    private static final String ASCII_100 = "a1".repeat(50);
    private static final String UMLAUT_74_BYTES = "ä".repeat(37);
    private static final String EMOJI_76_BYTES = "😀".repeat(19);
    // é in zerlegter Form (e + Akzent, 3 Bytes), wie es manche Tastaturen liefern
    private static final String NFD_75_BYTES = "e\u0301".repeat(25);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private JsonMapper json;

    private FamilyMember sarah;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
    }

    private ResultActions createMember(String username, String password) throws Exception {
        return mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON).content(
                "{\"name\": \"Lily\", \"color\": \"#EC4899\", \"username\": \"" + username + "\", \"password\": "
                        + json.writeValueAsString(password) + ", \"role\": \"kind\"}"));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).param("username", username).param("password", password));
    }

    @ParameterizedTest
    @ValueSource(ints = {72, 73, 99, 100})
    void membersCanHavePasswordsUpTo100Characters(int length) throws Exception {
        String password = ASCII_100.substring(0, length);

        createMember("lily", password).andExpect(status().isCreated());

        login("lily", password).andExpect(status().isOk());
        login("lily", password.substring(0, length - 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsWithUmlautsOrEmojisOver72BytesWork() throws Exception {
        assertThat(UMLAUT_74_BYTES.getBytes(UTF_8)).hasSize(74);
        assertThat(EMOJI_76_BYTES.getBytes(UTF_8)).hasSize(76);
        assertThat(NFD_75_BYTES.getBytes(UTF_8)).hasSize(75);

        createMember("lily", UMLAUT_74_BYTES).andExpect(status().isCreated());
        createMember("lily2", EMOJI_76_BYTES).andExpect(status().isCreated());
        createMember("lily3", NFD_75_BYTES).andExpect(status().isCreated());

        login("lily", UMLAUT_74_BYTES).andExpect(status().isOk());
        login("lily2", EMOJI_76_BYTES).andExpect(status().isOk());
        login("lily3", NFD_75_BYTES).andExpect(status().isOk());
        login("lily2", "😀".repeat(18)).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsCanSetLongPasswordsWhenEditingAMember() throws Exception {
        createMember("lily", "kurz1234").andExpect(status().isCreated());
        String id = members.findByUsername("lily").orElseThrow().id();

        mvc.perform(put("/api/members/" + id).with(as(sarah)).contentType(APPLICATION_JSON).content(
                "{\"name\": \"Lily\", \"color\": \"#EC4899\", \"username\": \"lily\", \"password\": "
                        + json.writeValueAsString(EMOJI_76_BYTES) + ", \"role\": \"kind\"}"))
                .andExpect(status().isOk());

        login("lily", EMOJI_76_BYTES).andExpect(status().isOk());
        login("lily", "kurz1234").andExpect(status().isUnauthorized());
    }

    @Test
    void shortPasswordsKeepTheirFormatLongOnesAreHashedFirst() throws Exception {
        createMember("lily", ASCII_100.substring(0, 72)).andExpect(status().isCreated());
        createMember("lily2", ASCII_100.substring(0, 73)).andExpect(status().isCreated());

        assertThat(members.findByUsername("lily").orElseThrow().passwordHash()).startsWith("{bcrypt}");
        assertThat(members.findByUsername("lily2").orElseThrow().passwordHash()).startsWith("{bcrypt-sha256}");
    }

    @Test
    void moreThan100CharactersAreRejectedWithAClearMessage() throws Exception {
        createMember("lily", ASCII_100 + "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Passwort muss 8 bis 100 Zeichen lang sein"));
    }

    @Test
    void longPasswordsCanBeSetAndChangedByTheirOwner() throws Exception {
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));

        mvc.perform(put("/api/auth/password").with(as(emma)).contentType(APPLICATION_JSON).content(
                "{\"currentPassword\": \"" + TestUsers.PASSWORD + "\", \"newPassword\": \"" + ASCII_100 + "\"}"))
                .andExpect(status().isNoContent());
        login("emma", ASCII_100).andExpect(status().isOk());

        emma = members.findByUsername("emma").orElseThrow();
        mvc.perform(put("/api/auth/password").with(as(emma)).contentType(APPLICATION_JSON).content(
                "{\"currentPassword\": \"" + ASCII_100 + "\", \"newPassword\": " + json.writeValueAsString(EMOJI_76_BYTES)
                        + "}"))
                .andExpect(status().isNoContent());
        login("emma", EMOJI_76_BYTES).andExpect(status().isOk());
        login("emma", ASCII_100).andExpect(status().isUnauthorized());
    }

    @Test
    void theFirstAdministratorMayChooseALongPassword() throws Exception {
        members.deleteAll();

        mvc.perform(post("/api/auth/setup").with(csrf()).contentType(APPLICATION_JSON).content(
                "{\"name\": \"Sarah\", \"color\": \"#2563EB\", \"username\": \"sarah\", \"password\": "
                        + json.writeValueAsString(UMLAUT_74_BYTES) + "}"))
                .andExpect(status().isCreated());

        login("sarah", UMLAUT_74_BYTES).andExpect(status().isOk());
    }
}
