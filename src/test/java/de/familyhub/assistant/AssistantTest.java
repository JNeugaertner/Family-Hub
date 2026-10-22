package de.familyhub.assistant;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// KI-Assistent, Phase 1: Status, Verbindungstest und Protokoll mit simuliertem Sprachmodell (FakeLanguageModel)
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLanguageModel.Config.class)
class AssistantTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private AssistantCallRepository calls;

    @Autowired
    private FakeLanguageModel model;

    @Autowired
    private MongoTemplate mongo;

    private FamilyMember sarah;
    private FamilyMember mike;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        model.reset();
        calls.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        mike = members.save(TestUsers.member("Mike", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    @Test
    void everyoneButGuestsSeesTheStatus() throws Exception {
        mvc.perform(get("/api/assistant/status").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.provider").value("Test"))
                .andExpect(jsonPath("$.model").value(FakeLanguageModel.MODEL));
        mvc.perform(get("/api/assistant/status").with(as(oma))).andExpect(status().isForbidden());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void connectionTestChecksToolCallingAndLogsBothRounds() throws Exception {
        model.callTools(50, 10, new ToolCall("call_1", ConnectionTest.TOOL, "{}"))
                .answer("Heute ist ein schöner Tag.", 70, 8);

        mvc.perform(post("/api/assistant/test").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Heute ist ein schöner Tag."))
                .andExpect(jsonPath("$.toolCalling").value(true))
                .andExpect(jsonPath("$.model").value(FakeLanguageModel.MODEL))
                .andExpect(jsonPath("$.inputTokens").value(120))
                .andExpect(jsonPath("$.outputTokens").value(18));

        // Die zweite Runde enthält das Ergebnis des Werkzeugs: das heutige Datum, keine Familiendaten
        ModelRequest second = model.requests().get(1);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN));
        assertThat(second.messages()).last().isEqualTo(new ModelMessage.ToolResult("call_1", today));
        assertThat(second.tools()).extracting(tool -> tool.name()).containsExactly(ConnectionTest.TOOL);

        List<AssistantCall> logged = calls.findByMemberIdOrderByAtAsc(sarah.id());
        assertThat(logged).hasSize(2).allSatisfy(call -> {
            assertThat(call.ok()).isTrue();
            assertThat(call.purpose()).isEqualTo(ConnectionTest.PURPOSE);
            assertThat(call.provider()).isEqualTo("Test");
        });
        assertThat(logged).extracting(AssistantCall::toolCalls).containsExactly(1, 0);
        assertThat(logged).extracting(AssistantCall::inputTokens).containsExactly(50L, 70L);
    }

    @Test
    void modelWithoutToolCallingIsReported() throws Exception {
        model.answer("Ich weiß das Datum leider nicht.", 40, 9);

        mvc.perform(post("/api/assistant/test").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolCalling").value(false));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void failuresAreReportedAndLogged() throws Exception {
        model.fail("Groq ist gerade nicht erreichbar.");

        mvc.perform(post("/api/assistant/test").with(as(sarah)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Groq ist gerade nicht erreichbar."));

        assertThat(calls.findByMemberIdOrderByAtAsc(sarah.id())).singleElement().satisfies(call -> {
            assertThat(call.ok()).isFalse();
            assertThat(call.error()).isEqualTo("Groq ist gerade nicht erreichbar.");
        });
    }

    @Test
    void onlyAdministratorsMayTest() throws Exception {
        mvc.perform(post("/api/assistant/test").with(as(lucas))).andExpect(status().isForbidden());
        mvc.perform(post("/api/assistant/test").with(as(oma))).andExpect(status().isForbidden());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void logsDisappearWithTheMemberAndAfterThirtyDays() throws Exception {
        model.answer("Hallo Mike.", 10, 2).answer("Hallo Sarah.", 10, 2);
        mvc.perform(post("/api/assistant/test").with(as(mike))).andExpect(status().isOk());
        mvc.perform(post("/api/assistant/test").with(as(sarah))).andExpect(status().isOk());

        mvc.perform(delete("/api/members/" + mike.id()).with(as(sarah))).andExpect(status().isNoContent());

        assertThat(calls.findAll()).extracting(AssistantCall::memberId).containsExactly(sarah.id());
        assertThat(mongo.indexOps(AssistantCall.class).getIndexInfo())
                .filteredOn(index -> index.isIndexForFields(List.of("at")))
                .singleElement()
                .extracting(IndexInfo::getExpireAfter)
                .isEqualTo(Optional.of(Duration.ofDays(30)));
    }
}
