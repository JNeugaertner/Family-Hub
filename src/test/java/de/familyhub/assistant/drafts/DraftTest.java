package de.familyhub.assistant.drafts;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.assistant.FakeLanguageModel;
import de.familyhub.assistant.chat.AssistantConversationRepository;
import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.meals.Dish;
import de.familyhub.meals.DishRepository;
import de.familyhub.meals.MealEntry;
import de.familyhub.meals.MealEntryRepository;
import de.familyhub.meals.MealStatus;
import de.familyhub.meals.MealType;
import de.familyhub.permission.Role;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

// Entwürfe (Phase 4) mit simuliertem Sprachmodell: Der Assistent ändert nichts, bis die Person den Entwurf übernimmt,
// und dann nur mit ihren Rechten
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLanguageModel.Config.class)
class DraftTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeLanguageModel model;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private AssistantConversationRepository conversations;

    @Autowired
    private DraftRepository drafts;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private ShoppingItemRepository shopping;

    @Autowired
    private MealEntryRepository meals;

    @Autowired
    private DishRepository dishes;

    @Autowired
    private MongoTemplate mongo;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        model.reset();
        conversations.deleteAll();
        drafts.deleteAll();
        events.deleteAll();
        tasks.deleteAll();
        shopping.deleteAll();
        meals.deleteAll();
        dishes.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    // Das Modell ruft ein Entwurfs-Werkzeug auf und antwortet dann; liefert die Antwort
    private ResultActions draft(FamilyMember who, String tool, String args) throws Exception {
        model.callTools(1, 1, new ToolCall("e", tool, args)).answer("Der Entwurf liegt bereit.", 1, 1);
        return mvc.perform(post("/api/assistant/conversation/messages").with(as(who)).contentType(APPLICATION_JSON)
                .content("{\"text\": \"Bitte vorbereiten\"}"));
    }

    private String draftId(ResultActions answer) throws Exception {
        return JsonPath.read(answer.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.drafts[0].id");
    }

    // Was FamilyHub dem Modell auf den letzten Werkzeug-Aufruf geantwortet hat
    private String lastToolResult() {
        return model.requests().getLast().messages().stream()
                .filter(m -> m instanceof ModelMessage.ToolResult)
                .map(m -> ((ModelMessage.ToolResult) m).content())
                .reduce((first, second) -> second).orElseThrow();
    }

    private ResultActions accept(FamilyMember who, String draftId, String items) throws Exception {
        return mvc.perform(post("/api/assistant/drafts/" + draftId + "/accept").with(as(who))
                .contentType(APPLICATION_JSON).content("{\"items\": " + items + "}"));
    }

    private CalendarEvent event(String title, boolean privateEvent, FamilyMember... who) {
        LocalDateTime start = tomorrow.atTime(17, 0);
        return events.save(new CalendarEvent(null, title, start, start.plusHours(1),
                List.of(who).stream().map(FamilyMember::id).toList(), EventCategory.SPORTS, null, null, privateEvent,
                EventStatus.APPROVED, sarah.id(), null));
    }

    @Test
    void nothingChangesUntilTheChosenItemsAreAccepted() throws Exception {
        String id = draftId(draft(sarah, "einkauf_entwerfen", """
                {"eintraege": [
                  {"aktion": "neu", "name": "Milch", "menge": "2 l", "kategorie": "milchprodukte"},
                  {"aktion": "neu", "name": "Chips", "kategorie": "snacks"},
                  {"aktion": "neu", "name": "Brot", "kategorie": "backwaren", "dringend": true}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Der Entwurf liegt bereit."))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.drafts[0].kind").value("shopping"))
                .andExpect(jsonPath("$.drafts[0].items[*].state").value(contains("open", "open", "open")))
                .andExpect(jsonPath("$.drafts[0].items[0].action").value("create"))
                .andExpect(jsonPath("$.drafts[0].items[0].data.quantity").value("2 l")));
        assertThat(lastToolResult()).contains("\"entwurf\":\"bereit\"").contains("Noch ist nichts geändert");
        assertThat(shopping.findAll()).isEmpty();

        // Milch mit geänderter Menge, Chips abgewählt, Brot wie entworfen
        accept(sarah, id, "[{\"index\": 0, \"changes\": {\"quantity\": \"3 l\"}}, {\"index\": 2}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].state").value(contains("applied", "discarded", "applied")))
                .andExpect(jsonPath("$.items[0].outcome").value("approved"))
                .andExpect(jsonPath("$.items[0].data.quantity").value("3 l"));

        assertThat(shopping.findAll()).extracting(ShoppingItem::name, ShoppingItem::quantity, ShoppingItem::status,
                ShoppingItem::viaAssistant).containsExactlyInAnyOrder(
                        tuple("Milch", "3 l", ShoppingItemStatus.APPROVED, true),
                        tuple("Brot", null, ShoppingItemStatus.APPROVED, true));
        // Ein zweiter Klick legt nichts doppelt an
        accept(sarah, id, "[{\"index\": 0}]").andExpect(status().isConflict());
        assertThat(shopping.findAll()).hasSize(2);
    }

    @Test
    void childrenMakeProposalsAndGetNoCalendarDrafts() throws Exception {
        String id = draftId(draft(lucas, "einkauf_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"neu\", \"name\": \"Eis\", \"kategorie\": \"tiefkuehl\"}]}"));
        assertThat(model.requests().getFirst().tools()).extracting(ToolSpec::name)
                .contains("einkauf_entwerfen", "essen_entwerfen", "aufgaben_entwerfen")
                .doesNotContain("kalender_entwerfen");
        assertThat(lastToolResult()).contains("ein Vorschlag");

        accept(lucas, id, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].outcome").value("proposed"));

        ShoppingItem item = shopping.findAll().getFirst();
        assertThat(item.status()).isEqualTo(ShoppingItemStatus.PROPOSED);
        assertThat(item.createdBy()).isEqualTo(lucas.id());
        assertThat(item.viaAssistant()).isTrue();
    }

    @Test
    void eventsCanBeMovedAndDeleted() throws Exception {
        CalendarEvent training = event("Training", false, lucas);
        String move = draftId(draft(sarah, "kalender_entwerfen", "{\"eintraege\": [{\"aktion\": \"aendern\", \"id\": \""
                + training.id() + "\", \"beginn\": \"" + tomorrow + "T18:00\"}]}")
                .andExpect(jsonPath("$.drafts[0].items[0].action").value("update"))
                .andExpect(jsonPath("$.drafts[0].items[0].before.title").value("Training"))
                .andExpect(jsonPath("$.drafts[0].items[0].data.title").doesNotExist()));
        assertThat(events.findById(training.id()).orElseThrow().start()).isEqualTo(tomorrow.atTime(17, 0));

        accept(sarah, move, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].state").value("applied"));

        CalendarEvent moved = events.findById(training.id()).orElseThrow();
        assertThat(moved.start()).isEqualTo(tomorrow.atTime(18, 0));
        // Die Dauer bleibt
        assertThat(moved.end()).isEqualTo(tomorrow.atTime(19, 0));
        // Geändert, nicht über den Assistenten angelegt
        assertThat(moved.viaAssistant()).isFalse();

        String remove = draftId(draft(sarah, "kalender_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"loeschen\", \"id\": \"" + training.id() + "\"}]}"));
        accept(sarah, remove, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].state").value("applied"));
        assertThat(events.findById(training.id())).isEmpty();
    }

    @Test
    void newEventsKeepTheirOriginAndWarnAboutOverlaps() throws Exception {
        event("Training", false, lucas);
        String id = draftId(draft(sarah, "kalender_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"titel\": "
                + "\"Zahnarzt\", \"beginn\": \"" + tomorrow + "T17:30\", \"personen\": [\"Lucas\", \"ich\"], "
                + "\"kategorie\": \"appointment\"}]}"));
        assertThat(lastToolResult()).contains("\"ueberschneidet_sich_mit\":[\"Training (17:00–18:00)\"]");

        accept(sarah, id, "[{\"index\": 0}]");

        CalendarEvent created = events.findAll().stream().filter(e -> e.title().equals("Zahnarzt")).findFirst()
                .orElseThrow();
        assertThat(created.memberIds()).containsExactly(lucas.id(), sarah.id());
        assertThat(created.end()).isEqualTo(tomorrow.atTime(18, 30));
        assertThat(created.viaAssistant()).isTrue();
        // Wer den Termin später auf der Kalenderseite ändert, ändert nichts an der Herkunft
        mvc.perform(put("/api/events/" + created.id()).with(as(sarah)).contentType(APPLICATION_JSON).content("""
                {"title": "Zahnarzt Dr. Weiß", "start": "%sT17:30", "end": "%sT18:30", "memberIds": ["%s"],
                 "category": "appointment"}""".formatted(tomorrow, tomorrow, lucas.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viaAssistant").value(true));
    }

    @Test
    void draftsOnlyReachWhatTheAskerMaySee() throws Exception {
        CalendarEvent privateOne = event("Frauenarzt", true, sarah);

        draft(emma, "kalender_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"loeschen\", \"id\": \"" + privateOne.id() + "\"}]}")
                .andExpect(jsonPath("$.drafts").isEmpty());

        assertThat(lastToolResult()).contains("gibt es nicht");
        assertThat(drafts.findAll()).isEmpty();
    }

    @Test
    void teenagersProposeEventsForOthers() throws Exception {
        String id = draftId(draft(emma, "kalender_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"titel\": "
                + "\"Kino\", \"beginn\": \"" + tomorrow + "T20:00\", \"personen\": [\"Emma\", \"Sarah\"]}]}"));
        assertThat(lastToolResult()).contains("ein Vorschlag, den ein Elternteil freigeben muss");

        accept(emma, id, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].outcome").value("proposed"));

        assertThat(events.findAll()).singleElement().extracting(CalendarEvent::status).isEqualTo(EventStatus.PROPOSED);
    }

    @Test
    void theHumanChoosesWhoGetsANewTaskAndNeverThePoints() throws Exception {
        draft(sarah, "aufgaben_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"titel\": \"Rasen mähen\", "
                + "\"punkte\": 5}]}").andExpect(jsonPath("$.drafts").isEmpty());
        assertThat(lastToolResult()).contains("wählt die Person selbst auf der Karte");

        String id = draftId(draft(sarah, "aufgaben_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"titel\": "
                + "\"Rasen mähen\", \"faellig\": \"" + tomorrow + "\", \"prioritaet\": \"hoch\"}]}")
                .andExpect(jsonPath("$.drafts[0].items[0].data.assigneeId").doesNotExist()));
        assertThat(lastToolResult()).contains("wählt die Person auf der Karte");

        accept(sarah, id, "[{\"index\": 0, \"changes\": {\"points\": 5}}]")
                .andExpect(jsonPath("$.items[0].state").value("open"))
                .andExpect(jsonPath("$.items[0].error").value("„points“ lässt sich hier nicht ändern."));
        accept(sarah, id, "[{\"index\": 0}]")
                .andExpect(jsonPath("$.items[0].state").value("open"))
                .andExpect(jsonPath("$.items[0].error").value("Aufgabe muss einem Familienmitglied zugewiesen sein"));
        assertThat(tasks.findAll()).isEmpty();

        accept(sarah, id, "[{\"index\": 0, \"changes\": {\"assigneeId\": \"" + lucas.id() + "\"}}]")
                .andExpect(jsonPath("$.items[0].state").value("applied"))
                .andExpect(jsonPath("$.items[0].error").doesNotExist());

        Task task = tasks.findAll().getFirst();
        assertThat(task.assigneeId()).isEqualTo(lucas.id());
        assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.points()).isZero();
        assertThat(task.createdBy()).isEqualTo(sarah.id());
        assertThat(task.viaAssistant()).isTrue();
    }

    @Test
    void childrenCanTickOffTheirOwnTasks() throws Exception {
        Task task = tasks.save(new Task(null, "Zimmer aufräumen", null, lucas.id(), tomorrow, TaskPriority.LOW,
                TaskCategory.CHORES, 5));
        String id = draftId(draft(lucas, "aufgaben_entwerfen", "{\"eintraege\": [{\"aktion\": \"aendern\", \"id\": \""
                + task.id() + "\", \"status\": \"erledigt\"}]}"));

        accept(lucas, id, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].state").value("applied"));

        assertThat(tasks.findById(task.id()).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
        // Löschen dürfen Kinder auch über den Assistenten nicht
        draft(lucas, "aufgaben_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"loeschen\", \"id\": \"" + task.id() + "\"}]}")
                .andExpect(jsonPath("$.drafts").isEmpty());
        assertThat(lastToolResult()).contains("fehler");
    }

    @Test
    void mealsUseDishesFromTheCollectionAndReplaceTheSlot() throws Exception {
        Dish lasagne = dishes.save(new Dish(null, "Lasagne", List.of(), sarah.id(), LocalDateTime.now()));
        meals.save(new MealEntry(null, tomorrow, MealType.ABENDESSEN, null, "Brote", MealStatus.APPROVED, sarah.id(),
                LocalDateTime.now()));
        String id = draftId(draft(sarah, "essen_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"datum\": \""
                + tomorrow + "\", \"mahlzeit\": \"abendessen\", \"gericht\": \"lasagne\"}]}"));
        assertThat(lastToolResult()).contains("\"aus_der_sammlung\":true").contains("\"ersetzt\":\"Brote\"");

        accept(sarah, id, "[{\"index\": 0}]").andExpect(jsonPath("$.items[0].state").value("applied"));

        assertThat(meals.findAll()).singleElement().satisfies(meal -> {
            assertThat(meal.name()).isEqualTo("Lasagne");
            assertThat(meal.dishId()).isEqualTo(lasagne.id());
            assertThat(meal.viaAssistant()).isTrue();
        });
    }

    @Test
    void invalidEntriesGoBackToTheModelAndNothingIsSaved() throws Exception {
        draft(sarah, "kalender_entwerfen", "{\"eintraege\": [{\"aktion\": \"neu\", \"titel\": \"A\", \"beginn\": \""
                + tomorrow + "T10:00\"}, {\"aktion\": \"neu\", \"beginn\": \"morgen\"}]}")
                .andExpect(jsonPath("$.drafts").isEmpty());

        assertThat(lastToolResult()).contains("Eintrag 2: titel fehlt.");
        assertThat(drafts.findAll()).isEmpty();
    }

    @Test
    void onlyTheOwnerCanUseADraftAndItCanBeDiscarded() throws Exception {
        String id = draftId(draft(sarah, "einkauf_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"neu\", \"name\": \"Milch\", \"kategorie\": \"milchprodukte\"}]}"));

        accept(emma, id, "[{\"index\": 0}]").andExpect(status().isNotFound());
        accept(sarah, id, "[{\"index\": 3}]").andExpect(status().isBadRequest());
        mvc.perform(post("/api/assistant/drafts/" + id + "/discard").with(as(sarah)))
                .andExpect(jsonPath("$.items[0].state").value("discarded"));

        assertThat(shopping.findAll()).isEmpty();
        mvc.perform(get("/api/assistant/conversation").with(as(sarah)))
                .andExpect(jsonPath("$.turns[1].drafts[0].items[0].state").value("discarded"));
    }

    @Test
    void draftsDisappearWithTheConversationAndAfterThirtyDays() throws Exception {
        draft(sarah, "einkauf_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"neu\", \"name\": \"Milch\", \"kategorie\": \"milchprodukte\"}]}");

        mvc.perform(delete("/api/assistant/conversation").with(as(sarah))).andExpect(status().isNoContent());

        assertThat(drafts.findAll()).isEmpty();
        assertThat(mongo.indexOps(Draft.class).getIndexInfo())
                .filteredOn(index -> index.isIndexForFields(List.of("createdAt")))
                .singleElement()
                .extracting(IndexInfo::getExpireAfter)
                .isEqualTo(Optional.of(Duration.ofDays(30)));
    }
}
