package de.familyhub.telegram;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.InboundMessageRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Der Telegram-Bot der Familie mit simuliertem Telegram (TelegramApi als Mock): verknüpfen, empfangen, senden.
@SpringBootTest
@AutoConfigureMockMvc
class TelegramTest {

    private static final long EMMA_TELEGRAM = 111;
    private static final long LUCAS_TELEGRAM = 222;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private TelegramLinkRepository links;

    @Autowired
    private InboundMessageRepository inbox;

    @Autowired
    private TelegramService service;

    @Autowired
    private TelegramPoller poller;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private TelegramApi api;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private int nextUpdate = 1;

    @BeforeEach
    void setUp() {
        links.deleteAll();
        inbox.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        when(api.getMe()).thenReturn(new TelegramApi.Bot(42, "FamilyHubTestBot"));
    }

    // Eine Nachricht, wie Telegram sie beim Abholen liefert
    private JsonNode update(long userId, String chatType, String text) {
        int id = nextUpdate++;
        return json.readTree("""
                {"update_id": %d, "message": {"message_id": %d,
                 "from": {"id": %d, "is_bot": false, "first_name": "Name%d"},
                 "chat": {"id": %d, "type": "%s"}, "date": 1761040000, "text": "%s"}}
                """.formatted(id, id, userId, userId, userId, chatType, text));
    }

    private String linkCode(FamilyMember member) throws Exception {
        String body = mvc.perform(post("/api/telegram/link").with(as(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("https://t.me/FamilyHubTestBot?start=")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        return url.substring(url.indexOf("start=") + "start=".length());
    }

    private void link(FamilyMember member, long telegramUserId) throws Exception {
        service.handle(update(telegramUserId, "private", "/start " + linkCode(member)));
    }

    @Test
    void linkingWithTheCodeFromFamilyHub() throws Exception {
        mvc.perform(get("/api/telegram/status").with(as(emma)))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.botUsername").value("FamilyHubTestBot"))
                .andExpect(jsonPath("$.linked").value(false));

        link(emma, EMMA_TELEGRAM);

        assertThat(links.findByMemberId(emma.id())).hasValueSatisfying(l -> {
            assertThat(l.telegramUserId()).isEqualTo(EMMA_TELEGRAM);
            assertThat(l.chatId()).isEqualTo(EMMA_TELEGRAM);
        });
        verify(api).sendMessage(eq(EMMA_TELEGRAM), org.mockito.ArgumentMatchers.contains("Emma"));
        mvc.perform(get("/api/telegram/status").with(as(emma)))
                .andExpect(jsonPath("$.linked").value(true))
                .andExpect(jsonPath("$.linkedName").value("Name111"));
    }

    @Test
    void invalidReusedOrMissingCodesDoNotLink() throws Exception {
        String code = linkCode(emma);
        service.handle(update(EMMA_TELEGRAM, "private", "/start " + code));
        // Derselbe Code ein zweites Mal, ein erfundener und keiner
        service.handle(update(333, "private", "/start " + code));
        service.handle(update(444, "private", "/start erfunden"));
        service.handle(update(555, "private", "/start"));
        // In einer Gruppe wird nicht verknüpft
        service.handle(update(666, "group", "/start " + linkCode(lucas)));

        assertThat(links.findAll()).extracting(TelegramLink::telegramUserId).containsExactly(EMMA_TELEGRAM);
        verify(api).sendMessage(eq(333L), org.mockito.ArgumentMatchers.contains("abgelaufen"));
        verify(api).sendMessage(eq(444L), org.mockito.ArgumentMatchers.contains("abgelaufen"));
        verify(api).sendMessage(eq(555L), org.mockito.ArgumentMatchers.contains("Mit Telegram verbinden"));
        verify(api, never()).sendMessage(eq(666L), any());
    }

    @Test
    void messagesFromLinkedMembersLandInTheInboxOthersAreDropped() throws Exception {
        link(emma, EMMA_TELEGRAM);
        JsonNode milk = update(EMMA_TELEGRAM, "private", "Bitte Milch kaufen");

        service.handle(milk);
        service.handle(milk);
        service.handle(update(999, "private", "Hallo, wer bist du?"));

        assertThat(inbox.findAll()).singleElement().satisfies(m -> {
            assertThat(m.senderKey()).isEqualTo("telegram:111");
            assertThat(m.senderName()).isEqualTo("Name111");
            assertThat(m.text()).isEqualTo("Bitte Milch kaufen");
            assertThat(m.sentAt()).isNotNull();
            assertThat(m.raw()).contains("Bitte Milch kaufen");
        });
    }

    @Test
    void everyoneSeesOnlyTheirOwnTelegramMessages() throws Exception {
        link(emma, EMMA_TELEGRAM);
        link(lucas, LUCAS_TELEGRAM);
        service.handle(update(EMMA_TELEGRAM, "private", "Von Emma"));
        service.handle(update(LUCAS_TELEGRAM, "private", "Von Lucas"));

        mvc.perform(get("/api/inbox").param("channel", "telegram").with(as(emma)))
                .andExpect(jsonPath("$[*].text", contains("Von Emma")));
        mvc.perform(get("/api/telegram/status").with(as(emma)))
                .andExpect(jsonPath("$.messageCount").value(1));
        mvc.perform(get("/api/inbox").param("channel", "telegram").with(as(sarah)))
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/inbox").param("channel", "telegram").with(as(oma)))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestsCannotLink() throws Exception {
        mvc.perform(post("/api/telegram/link").with(as(oma))).andExpect(status().isForbidden());
    }

    @Test
    void testMessageGoesToTheOwnChat() throws Exception {
        link(emma, EMMA_TELEGRAM);

        mvc.perform(post("/api/telegram/test").with(as(emma))).andExpect(status().isNoContent());
        verify(api).sendMessage(EMMA_TELEGRAM, TelegramController.TEST_MESSAGE);

        mvc.perform(post("/api/telegram/test").with(as(lucas)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Es ist noch kein Telegram-Konto verbunden."));
    }

    @Test
    void unlinkingAndDeletingTheMemberRemoveTheLink() throws Exception {
        link(emma, EMMA_TELEGRAM);
        link(lucas, LUCAS_TELEGRAM);

        mvc.perform(delete("/api/telegram/link").with(as(emma))).andExpect(status().isNoContent());
        service.handle(update(EMMA_TELEGRAM, "private", "Noch da?"));
        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah))).andExpect(status().isNoContent());

        assertThat(links.findAll()).isEmpty();
        assertThat(inbox.count()).isZero();
    }

    @Test
    void pollerFetchesUpdatesAndAcknowledgesThem() throws Exception {
        JsonNode linking = update(EMMA_TELEGRAM, "private", "/start " + linkCode(emma));
        JsonNode message = update(EMMA_TELEGRAM, "private", "Abgeholt");
        long last = message.path("update_id").asLong();
        when(api.getUpdates(any(), anyInt())).thenReturn(List.of(linking, message), List.of());

        poller.pollOnce(0);
        poller.pollOnce(0);

        // Beim zweiten Mal fragt FamilyHub erst ab der nächsten Nummer und bestätigt damit die abgeholten
        verify(api).getUpdates(last + 1, 0);
        assertThat(inbox.findAll()).extracting(InboundMessage::text).containsExactly("Abgeholt");
        verify(api, never()).sendMessage(anyLong(), eq(TelegramController.TEST_MESSAGE));
    }
}
