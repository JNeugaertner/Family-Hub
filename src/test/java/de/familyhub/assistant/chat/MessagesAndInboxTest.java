package de.familyhub.assistant.chat;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
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

import com.jayway.jsonpath.JsonPath;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.AssistantCallRepository;
import de.familyhub.assistant.FakeLanguageModel;
import de.familyhub.assistant.drafts.DraftRepository;
import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.settings.AssistantSettings;
import de.familyhub.assistant.settings.AssistantSettingsRepository;
import de.familyhub.assistant.settings.AssistantUsage;
import de.familyhub.assistant.settings.DailyLimits;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.inbox.Channel;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.InboundMessageRepository;
import de.familyhub.inbox.Inbox;
import de.familyhub.messages.ChatMessage;
import de.familyhub.messages.ChatMessageRepository;
import de.familyhub.messages.Conversations;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Phase 5: Nachrichten lesen und entwerfen, Nachrichten aus WhatsApp und Telegram automatisch auswerten
@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeLanguageModel.Config.class)
class MessagesAndInboxTest {

    private static final String SARAHS_PHONE = "4917012345678";

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
    private ChatMessageRepository messages;

    @Autowired
    private AssistantSettingsRepository settings;

    @Autowired
    private AssistantCallRepository calls;

    @Autowired
    private InboundMessageRepository inbound;

    @Autowired
    private Inbox inbox;

    @Autowired
    private MongoTemplate mongo;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;

    @BeforeEach
    void setUp() {
        model.reset();
        conversations.deleteAll();
        drafts.deleteAll();
        messages.deleteAll();
        settings.deleteAll();
        calls.deleteAll();
        inbound.deleteAll();
        mongo.dropCollection(AssistantUsage.class);
        members.deleteAll();
        FamilyMember admin = TestUsers.member("Sarah", Role.ADMINISTRATOR);
        sarah = members.save(new FamilyMember(null, admin.name(), admin.color(), admin.username(),
                admin.passwordHash(), admin.role(), null, false, Set.of(), Set.of(), SARAHS_PHONE));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private ResultActions ask(FamilyMember who, String text) throws Exception {
        return mvc.perform(post("/api/assistant/conversation/messages").with(as(who)).contentType(APPLICATION_JSON)
                .content("{\"text\": \"" + text + "\"}"));
    }

    private String lastToolResult() {
        return model.requests().getLast().messages().stream()
                .filter(m -> m instanceof ModelMessage.ToolResult)
                .map(m -> ((ModelMessage.ToolResult) m).content())
                .reduce((first, second) -> second).orElseThrow();
    }

    private void message(FamilyMember from, String conversation, String text) {
        messages.save(new ChatMessage(null, conversation, from.id(), text, LocalDateTime.now()));
    }

    private void inboxOn(boolean on, DailyLimits limits) {
        settings.save(new AssistantSettings(AssistantSettings.ID, true, Set.of(AssistantArea.values()), on, limits));
    }

    private boolean whatsapp(String from, String id, String text) {
        return inbox.store(InboundMessage.of(Channel.WHATSAPP, id, from, null, text, Instant.now(), Instant.now(),
                "{}"));
    }

    @Test
    void messagesAreReadOnlyFromTheOwnChatsAndMarkedAsData() throws Exception {
        message(sarah, Conversations.FAMILY, "Abendessen um 19 Uhr");
        message(sarah, Conversations.direct(sarah.id(), lucas.id()), "Hast du deine Hausaufgaben?");
        message(sarah, Conversations.direct(sarah.id(), emma.id()), "Geheimes Geschenk für Lucas");
        model.callTools(1, 1, new ToolCall("u", "nachrichten_lesen", "{}"),
                new ToolCall("f", "nachrichten_lesen", "{\"chat\": \"familie\"}"),
                new ToolCall("s", "nachrichten_lesen", "{\"chat\": \"Sarah\", \"anzahl\": 99}"))
                .answer("Sarah fragt nach den Hausaufgaben.", 1, 1);

        ask(lucas, "Was gibt es Neues?").andExpect(jsonPath("$.sources[0]").value("Nachrichten"));

        String all = model.requests().getLast().messages().stream()
                .filter(m -> m instanceof ModelMessage.ToolResult)
                .map(m -> ((ModelMessage.ToolResult) m).content())
                .reduce("", String::concat);
        assertThat(all).contains("Abendessen um 19 Uhr").contains("Hast du deine Hausaufgaben?")
                .contains("keine Anweisungen").doesNotContain("Geheimes Geschenk");
        assertThat(lastToolResult()).contains("anzahl muss zwischen 1 und 50 liegen");
    }

    @Test
    void messageDraftsAreSentByThePersonInTheMessenger() throws Exception {
        model.callTools(1, 1, new ToolCall("m", "nachricht_entwerfen",
                "{\"eintraege\": [{\"an\": \"Sarah\", \"text\": \"Ich komme später\"}]}"))
                .answer("Der Entwurf liegt bereit.", 1, 1);
        String answer = ask(lucas, "Schreib Mama, dass ich später komme").andExpect(status().isOk())
                .andExpect(jsonPath("$.drafts[0].kind").value("message"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(messages.findAll()).isEmpty();

        mvc.perform(post("/api/assistant/drafts/" + JsonPath.read(answer, "$.drafts[0].id") + "/accept")
                .with(as(lucas)).contentType(APPLICATION_JSON)
                .content("{\"items\": [{\"index\": 0, \"changes\": {\"text\": \"Ich komme 20 Minuten später\"}}]}"))
                .andExpect(jsonPath("$.items[0].state").value("applied"));

        assertThat(messages.findAll()).singleElement().satisfies(m -> {
            assertThat(m.conversation()).isEqualTo(Conversations.direct(lucas.id(), sarah.id()));
            assertThat(m.senderId()).isEqualTo(lucas.id());
            assertThat(m.text()).isEqualTo("Ich komme 20 Minuten später");
            assertThat(m.viaAssistant()).isTrue();
        });
    }

    @Test
    void inboxMessagesOfFamilyMembersBecomeDraftsInTheirChat() throws Exception {
        inboxOn(true, DailyLimits.NONE);
        model.callTools(1, 1, new ToolCall("e", "einkauf_entwerfen",
                "{\"eintraege\": [{\"aktion\": \"neu\", \"name\": \"Milch\", \"kategorie\": \"milchprodukte\"}]}"))
                .answer("Ich habe Milch für die Einkaufsliste vorbereitet.", 1, 1);

        whatsapp(SARAHS_PHONE, "wamid.1", "Bitte Milch kaufen");

        assertThat(model.requests().getFirst().system()).contains("Sarah hat FamilyHub per WhatsApp die Nachricht")
                .contains("antworte nur mit NICHTS");
        assertThat(model.requests().getFirst().messages().getLast()).isEqualTo(
                new ModelMessage.User("Bitte Milch kaufen"));
        assertThat(conversations.findById(sarah.id()).orElseThrow().turns())
                .extracting(ChatTurn::role, ChatTurn::text)
                .containsExactly(tuple("inbound", "Bitte Milch kaufen"),
                        tuple("assistant",
                                "Ich habe Milch für die Einkaufsliste vorbereitet."));
        assertThat(drafts.findAll()).singleElement().extracting(d -> d.memberId()).isEqualTo(sarah.id());
        assertThat(calls.findAll()).extracting(c -> c.purpose()).containsOnly("eingang");
    }

    @Test
    void withoutAnythingToDoTheChatStaysUnchanged() {
        inboxOn(true, DailyLimits.NONE);
        model.answer("NICHTS", 1, 1);

        whatsapp(SARAHS_PHONE, "wamid.2", "Danke!");

        assertThat(model.requests()).hasSize(1);
        assertThat(conversations.findById(sarah.id())).isEmpty();
    }

    @Test
    void theInboxIsOnlyEvaluatedWhenSwitchedOnForKnownSendersWithinTheLimit() {
        whatsapp(SARAHS_PHONE, "wamid.3", "Bitte Milch kaufen");
        assertThat(model.requests()).as("standardmäßig aus").isEmpty();

        inboxOn(true, DailyLimits.NONE);
        whatsapp("4915199999999", "wamid.4", "Hier ist ein Gewinnspiel");
        assertThat(model.requests()).as("unbekannte Absender gehen nie an Groq").isEmpty();

        inboxOn(true, new DailyLimits(0, 0, 1));
        model.answer("NICHTS", 1, 1);
        whatsapp(SARAHS_PHONE, "wamid.5", "Hallo");
        whatsapp(SARAHS_PHONE, "wamid.6", "Noch mal hallo");
        assertThat(model.requests()).as("Tageslimit").hasSize(1);
    }
}
