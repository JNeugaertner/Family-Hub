package de.familyhub.assistant.settings;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.AssistantCallRepository;
import de.familyhub.assistant.FakeLanguageModel;
import de.familyhub.assistant.chat.AssistantConversationRepository;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Admin-Karte des Assistenten (Phase 5): Ein/Aus, Bereiche, Tageslimit je Rolle und Verbrauch
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLanguageModel.Config.class)
class AssistantSettingsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeLanguageModel model;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private AssistantSettingsRepository settings;

    @Autowired
    private AssistantConversationRepository conversations;

    @Autowired
    private AssistantCallRepository calls;

    @Autowired
    private MongoTemplate mongo;

    private FamilyMember sarah;
    private FamilyMember lucas;

    @BeforeEach
    void setUp() {
        model.reset();
        settings.deleteAll();
        conversations.deleteAll();
        calls.deleteAll();
        mongo.dropCollection(AssistantUsage.class);
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private ResultActions ask(FamilyMember who) throws Exception {
        return mvc.perform(post("/api/assistant/conversation/messages").with(as(who)).contentType(APPLICATION_JSON)
                .content("{\"text\": \"Hallo\"}"));
    }

    private void save(AssistantSettings changed) {
        settings.save(new AssistantSettings(AssistantSettings.ID, changed.enabled(), changed.areas(),
                changed.inboxDrafts(), changed.dailyLimits()));
    }

    @Test
    void withoutSettingsEverythingIsOnExceptTheInbox() throws Exception {
        mvc.perform(get("/api/assistant/settings").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.areas").value(containsInAnyOrder("kalender", "aufgaben", "einkauf", "essen",
                        "wetter_muell", "nachrichten")))
                .andExpect(jsonPath("$.inboxDrafts").value(false))
                .andExpect(jsonPath("$.dailyLimits.kind").value(0))
                .andExpect(jsonPath("$.id").doesNotExist());
        mvc.perform(get("/api/assistant/settings").with(as(lucas))).andExpect(status().isForbidden());
        mvc.perform(get("/api/assistant/usage").with(as(lucas))).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdministratorsChangeTheSettingsAndValuesAreChecked() throws Exception {
        String body = """
                {"enabled": true, "areas": ["kalender"], "inboxDrafts": true,
                 "dailyLimits": {"kind": 10, "jugendlicher": 20, "administrator": 0}}""";
        mvc.perform(put("/api/assistant/settings").with(as(lucas)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/assistant/settings").with(as(sarah)).contentType(APPLICATION_JSON)
                .content(body.replace("\"kind\": 10", "\"kind\": -1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['dailyLimits.kind']").value("Das Limit darf nicht negativ sein"));

        mvc.perform(put("/api/assistant/settings").with(as(sarah)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[0]").value("kalender"))
                .andExpect(jsonPath("$.dailyLimits.jugendlicher").value(20));

        assertThat(settings.findById(AssistantSettings.ID).orElseThrow().inboxDrafts()).isTrue();
    }

    @Test
    void whenSwitchedOffNobodyCanAsk() throws Exception {
        save(new AssistantSettings(null, false, Set.of(AssistantArea.values()), false, DailyLimits.NONE));

        ask(sarah).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(containsString("abgeschaltet")));

        mvc.perform(get("/api/assistant/status").with(as(lucas))).andExpect(jsonPath("$.enabled").value(false));
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void switchedOffAreasOfferNoTools() throws Exception {
        save(new AssistantSettings(null, true, Set.of(AssistantArea.KALENDER, AssistantArea.AUFGABEN), false,
                DailyLimits.NONE));
        model.answer("Hallo", 1, 1);

        ask(sarah).andExpect(status().isOk());

        assertThat(model.requests().getFirst().tools()).extracting(ToolSpec::name).containsExactlyInAnyOrder(
                "kalender_lesen", "konflikte_pruefen", "aufgaben_lesen", "kalender_entwerfen", "aufgaben_entwerfen");
    }

    @Test
    void theDailyLimitDependsOnTheRole() throws Exception {
        save(new AssistantSettings(null, true, Set.of(AssistantArea.values()), false, new DailyLimits(1, 5, 0)));
        model.answer("Eins", 1, 1).answer("Zwei", 1, 1).answer("Drei", 1, 1);
        mvc.perform(get("/api/assistant/status").with(as(lucas))).andExpect(jsonPath("$.dailyLimit").value(1));

        ask(lucas).andExpect(status().isOk());
        ask(lucas).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Für heute hast du deine 1 Fragen an den Assistenten "
                        + "aufgebraucht. Morgen geht es weiter."));
        // Administratoren haben hier kein Limit
        ask(sarah).andExpect(status().isOk());
        ask(sarah).andExpect(status().isOk());
        assertThat(model.requests()).hasSize(3);
    }

    @Test
    void failedQuestionsDoNotCount() throws Exception {
        save(new AssistantSettings(null, true, Set.of(AssistantArea.values()), false, new DailyLimits(1, 1, 1)));
        model.fail("Groq ist gerade nicht erreichbar.").answer("Jetzt klappt es", 1, 1);

        ask(lucas).andExpect(status().isServiceUnavailable());
        ask(lucas).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Jetzt klappt es"));
    }

    @Test
    void theUsageShowsQuestionsCallsAndTokensPerPerson() throws Exception {
        model.callTools(300, 20, new ToolCall("k", "konflikte_pruefen", "{}"))
                .answer("Keine Konflikte", 400, 30)
                .answer("Hallo", 100, 5);
        ask(lucas);
        ask(sarah);

        mvc.perform(get("/api/assistant/usage").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("Lucas"))
                .andExpect(jsonPath("$.members[0].questions").value(1))
                .andExpect(jsonPath("$.members[0].calls").value(2))
                .andExpect(jsonPath("$.members[0].inputTokens").value(700))
                .andExpect(jsonPath("$.members[0].outputTokens").value(50))
                .andExpect(jsonPath("$.members[1].name").value("Sarah"))
                .andExpect(jsonPath("$.members[1].calls").value(1));
    }
}
