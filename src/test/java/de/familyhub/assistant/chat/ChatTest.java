package de.familyhub.assistant.chat;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import de.familyhub.assistant.FakeLanguageModel;
import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.shopping.ShoppingCategory;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

// Der lesende Chat (Phase 3) mit simuliertem Sprachmodell: Werkzeuge sehen nur, was die fragende Person sieht;
// gespeichert werden nur Frage und Antwort
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLanguageModel.Config.class)
class ChatTest {

    private static final List<String> ALL_TOOLS = List.of("termine_lesen", "aufgaben_lesen", "konflikte_pruefen",
            "einkaufsliste_lesen", "essensplan_lesen", "wetter_lesen", "muellabfuhr_lesen");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeLanguageModel model;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private AssistantConversationRepository conversations;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private ShoppingItemRepository shopping;

    @Autowired
    private MongoTemplate mongo;

    private FamilyMember sarah;
    private FamilyMember lucas;
    private FamilyMember oma;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        model.reset();
        conversations.deleteAll();
        events.deleteAll();
        tasks.deleteAll();
        shopping.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private ResultActions ask(FamilyMember who, String text) throws Exception {
        return mvc.perform(post("/api/assistant/conversation/messages").with(as(who)).contentType(APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\"}"));
    }

    // Das Ergebnis, das FamilyHub dem Modell für einen Werkzeug-Aufruf zurückgegeben hat
    private String toolResult(int request, String callId) {
        return model.requests().get(request).messages().stream()
                .filter(m -> m instanceof ModelMessage.ToolResult r && r.toolCallId().equals(callId))
                .map(m -> ((ModelMessage.ToolResult) m).content())
                .findFirst().orElseThrow();
    }

    private void event(String title, boolean privateEvent, FamilyMember createdBy, FamilyMember... who) {
        LocalDateTime start = today.plusDays(1).atTime(18, 0);
        events.save(new CalendarEvent(null, title, start, start.plusHours(1),
                List.of(who).stream().map(FamilyMember::id).toList(), EventCategory.FAMILY, null, null,
                privateEvent, EventStatus.APPROVED, createdBy.id(), null));
    }

    @Test
    void answersAndKeepsTheConversation() throws Exception {
        model.answer("Hallo Lucas! Wie kann ich helfen?", 300, 12);

        ask(lucas, "Hallo").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.text").value("Hallo Lucas! Wie kann ich helfen?"))
                .andExpect(jsonPath("$.sources").isEmpty());

        mvc.perform(get("/api/assistant/conversation").with(as(lucas)))
                .andExpect(jsonPath("$.turns[*].role").value(contains("user", "assistant")))
                .andExpect(jsonPath("$.turns[0].text").value("Hallo"));
        ModelRequest request = model.requests().getFirst();
        assertThat(request.system())
                .contains("Du kannst nur lesen")
                .contains("Du sprichst mit Lucas (Kind).")
                .contains("Zur Familie gehören: Sarah (Administrator), Emma (Jugendlicher), Lucas (Kind), Oma (Gast).");
        assertThat(request.tools()).extracting(ToolSpec::name).containsExactlyInAnyOrderElementsOf(ALL_TOOLS);
        // Sarahs Verlauf bleibt davon unberührt
        mvc.perform(get("/api/assistant/conversation").with(as(sarah))).andExpect(jsonPath("$.turns").isEmpty());
    }

    @Test
    void toolsOnlySeeWhatTheAskerMaySee() throws Exception {
        event("Familienessen", false, sarah, sarah, lucas);
        event("Frauenarzt", true, sarah, sarah);
        String args = "{\"von\": \"" + today + "\", \"bis\": \"" + today.plusDays(3) + "\"}";
        model.callTools(400, 20, new ToolCall("c1", "termine_lesen", args)).answer("Morgen ist Familienessen.", 600, 15)
                .callTools(400, 20, new ToolCall("c2", "termine_lesen", args)).answer("Zwei Termine.", 600, 15);

        ask(lucas, "Was steht an?").andExpect(jsonPath("$.sources[0]").value("Kalender"));
        assertThat(toolResult(1, "c1")).contains("Familienessen").contains("\"Sarah\",\"Lucas\"")
                .doesNotContain("Frauenarzt");

        ask(sarah, "Was steht an?");
        assertThat(toolResult(3, "c2")).contains("Familienessen").contains("Frauenarzt").contains("\"privat\":true");
    }

    @Test
    void tasksCanBeFilteredByName() throws Exception {
        tasks.save(new Task(null, "Zimmer aufräumen", null, lucas.id(), today.minusDays(1), TaskPriority.HIGH,
                TaskCategory.CHORES, 5));
        tasks.save(new Task(null, "Steuer", null, sarah.id(), today, TaskPriority.LOW, TaskCategory.ERRANDS, 0));
        Task done = new Task(null, "Erledigt", null, lucas.id(), today, TaskPriority.LOW, TaskCategory.CHORES, 0);
        tasks.save(done.withStatus(TaskStatus.DONE));
        model.callTools(1, 1, new ToolCall("a", "aufgaben_lesen", "{\"person\": \"lucas\"}"),
                new ToolCall("b", "aufgaben_lesen", "{\"person\": \"Opa\"}")).answer("Eine Aufgabe.", 1, 1);

        ask(sarah, "Was muss Lucas tun?");

        assertThat(toolResult(1, "a")).contains("Zimmer aufräumen").contains("\"ueberfaellig\":true")
                .contains("\"punkte\":5").doesNotContain("Steuer").doesNotContain("Erledigt");
        assertThat(toolResult(1, "b")).contains("Unbekannte Person „Opa“").contains("Sarah, Emma, Lucas, Oma");
    }

    @Test
    void theShoppingListShowsProposalsOnlyToTheirAuthorAndTheParents() throws Exception {
        shopping.save(new ShoppingItem(null, "Milch", "2 l", ShoppingCategory.MILCHPRODUKTE, true, false,
                ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), null));
        shopping.save(new ShoppingItem(null, "Chips", null, ShoppingCategory.SNACKS, false, false,
                ShoppingItemStatus.PROPOSED, members.findByUsername("emma").orElseThrow().id(), LocalDateTime.now(),
                null));
        model.callTools(1, 1, new ToolCall("s", "einkaufsliste_lesen", "{}")).answer("Milch.", 1, 1)
                .callTools(1, 1, new ToolCall("t", "einkaufsliste_lesen", "{}")).answer("Milch und Chips.", 1, 1);

        ask(lucas, "Was fehlt?");
        assertThat(toolResult(1, "s")).contains("Milch").contains("\"dringend\":true").doesNotContain("Chips");
        ask(sarah, "Was fehlt?");
        assertThat(toolResult(3, "t")).contains("Chips").contains("\"vorschlag\":true");
    }

    @Test
    void withoutTheRightTheToolIsNotOffered() throws Exception {
        FamilyMember noShopping = members.save(new FamilyMember(null, "Lily", "#2563EB", "lily",
                "{noop}" + TestUsers.PASSWORD, Role.KIND, null, false, Set.of(),
                Set.of(Permission.of(Module.EINKAUF, Action.ANSEHEN, Scope.FAMILIE))));
        model.callTools(1, 1, new ToolCall("x", "einkaufsliste_lesen", "{}")).answer("Das kann ich nicht.", 1, 1);

        ask(noShopping, "Was fehlt?");

        assertThat(model.requests().getFirst().tools()).extracting(ToolSpec::name)
                .doesNotContain("einkaufsliste_lesen").contains("termine_lesen");
        assertThat(toolResult(1, "x")).contains("gibt es nicht");
    }

    @Test
    void invalidArgumentsGoBackToTheModel() throws Exception {
        model.callTools(1, 1, new ToolCall("d", "termine_lesen", "{\"von\": \"morgen\"}"),
                new ToolCall("j", "termine_lesen", "kein json")).answer("Welcher Tag?", 1, 1);

        ask(sarah, "Was ist morgen?").andExpect(jsonPath("$.text").value("Welcher Tag?"))
                .andExpect(jsonPath("$.sources").isEmpty());

        assertThat(toolResult(1, "d")).contains("JJJJ-MM-TT");
        assertThat(toolResult(1, "j")).contains("kein gültiges JSON");
    }

    @Test
    void stopsAfterEightToolRounds() throws Exception {
        for (int i = 0; i < AssistantChat.MAX_ROUNDS; i++) {
            model.callTools(1, 1, new ToolCall("r" + i, "konflikte_pruefen", "{}"));
        }

        ask(sarah, "Gibt es Konflikte?").andExpect(jsonPath("$.text").value(AssistantChat.TOO_MANY_ROUNDS));

        assertThat(model.requests()).hasSize(AssistantChat.MAX_ROUNDS);
        assertThat(toolResult(1, "r0")).contains("\"konflikte\":[]");
    }

    @Test
    void earlierQuestionsGoAlongButNotTheLookedUpData() throws Exception {
        model.answer("Antwort eins", 1, 1).answer("Antwort zwei", 1, 1);

        ask(sarah, "Frage eins");
        ask(sarah, "Frage zwei");

        assertThat(model.requests().get(1).messages()).containsExactly(new ModelMessage.User("Frage eins"),
                new ModelMessage.Assistant("Antwort eins", List.of()), new ModelMessage.User("Frage zwei"));
    }

    @Test
    void failuresAreReportedAndNothingIsSaved() throws Exception {
        model.fail("Groq ist gerade nicht erreichbar.");

        ask(sarah, "Hallo").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Groq ist gerade nicht erreichbar."));

        assertThat(conversations.findById(sarah.id())).isEmpty();
    }

    @Test
    void questionsMustNotBeEmptyOrTooLong() throws Exception {
        ask(sarah, "  ").andExpect(status().isBadRequest());
        ask(sarah, "x".repeat(1001)).andExpect(status().isBadRequest());
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void aNewConversationStartsEmpty() throws Exception {
        model.answer("Hallo", 1, 1);
        ask(sarah, "Hallo");

        mvc.perform(delete("/api/assistant/conversation").with(as(sarah))).andExpect(status().isNoContent());

        mvc.perform(get("/api/assistant/conversation").with(as(sarah))).andExpect(jsonPath("$.turns").isEmpty());
    }

    @Test
    void guestsHaveNoChat() throws Exception {
        ask(oma, "Hallo").andExpect(status().isForbidden());
        mvc.perform(get("/api/assistant/conversation").with(as(oma))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/assistant/conversation").with(as(oma))).andExpect(status().isForbidden());
    }

    @Test
    void conversationsDisappearWithTheMemberAndAfterThirtyDays() throws Exception {
        model.answer("Hallo", 1, 1);
        ask(lucas, "Hallo");

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah))).andExpect(status().isNoContent());

        assertThat(conversations.findById(lucas.id())).isEmpty();
        assertThat(mongo.indexOps(AssistantConversation.class).getIndexInfo())
                .filteredOn(index -> index.isIndexForFields(List.of("updatedAt")))
                .singleElement()
                .extracting(IndexInfo::getExpireAfter)
                .isEqualTo(Optional.of(Duration.ofDays(30)));
    }
}
