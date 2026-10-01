package de.familyhub.messages;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Familien-Chat (Entscheidungen vom 01.10.2026): Familiengruppe und Einzelchats für alle außer Gästen; Einzelchats
// sehen nur die beiden Beteiligten; eigene Nachrichten löschen, Administratoren auch fremde in der Familiengruppe.
@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private ReadMarkRepository reads;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
        reads.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private ResultActions send(FamilyMember by, String conversation, String text) throws Exception {
        String body = "{\"conversation\": \"" + conversation + "\", \"text\": \"" + text + "\"}";
        return mvc.perform(post("/api/messages").with(as(by)).contentType(APPLICATION_JSON).content(body));
    }

    private String sendOk(FamilyMember by, String conversation, String text) throws Exception {
        String body = send(by, conversation, text).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String direct(FamilyMember a, FamilyMember b) {
        return Conversations.direct(a.id(), b.id());
    }

    @Test
    void childWritesInFamilyGroupAndEveryoneExceptGuestsReadsIt() throws Exception {
        send(lucas, "family", "  Hausaufgaben fertig  ")
                .andExpect(jsonPath("$.text").value("Hausaufgaben fertig"))
                .andExpect(jsonPath("$.senderId").value(lucas.id()))
                .andExpect(jsonPath("$.conversation").value("family"));
        sendOk(emma, "family", "Super!");

        for (FamilyMember reader : new FamilyMember[] { sarah, emma, lucas }) {
            mvc.perform(get("/api/messages").param("conversation", "family").with(as(reader)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].text", contains("Hausaufgaben fertig", "Super!")));
        }
        mvc.perform(get("/api/messages").param("conversation", "family").with(as(oma)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/conversations").with(as(oma))).andExpect(status().isForbidden());
        send(oma, "family", "Hallo").andExpect(status().isForbidden());
    }

    @Test
    void directChatIsOnlyVisibleToItsTwoParticipantsNotEvenToAdministrators() throws Exception {
        sendOk(emma, direct(emma, lucas), "Leihst du mir dein Ladekabel?");

        mvc.perform(get("/api/messages").param("conversation", direct(lucas, emma)).with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Leihst du mir dein Ladekabel?"));
        mvc.perform(get("/api/messages").param("conversation", direct(emma, lucas)).with(as(sarah)))
                .andExpect(status().isNotFound());
        send(sarah, direct(emma, lucas), "Ich mische mich ein").andExpect(status().isNotFound());
    }

    @Test
    void conversationsListFamilyGroupAndOneDirectChatPerMemberWithoutGuests() throws Exception {
        sendOk(sarah, direct(sarah, lucas), "Zähne putzen!");

        mvc.perform(get("/api/messages/conversations").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value("family"))
                .andExpect(jsonPath("$[0].kind").value("family"))
                .andExpect(jsonPath("$[*].partnerId", containsInAnyOrder(null, sarah.id(), emma.id())))
                .andExpect(jsonPath("$[?(@.partnerId == '" + sarah.id() + "')].lastMessage.text").value("Zähne putzen!"))
                .andExpect(jsonPath("$[?(@.partnerId == '" + emma.id() + "')].lastMessage").value(contains((Object) null)));
    }

    @Test
    void unreadCountsOnlyMessagesOfOthersSinceLastRead() throws Exception {
        sendOk(emma, "family", "Eins");
        sendOk(lucas, "family", "Zwei");

        mvc.perform(get("/api/messages/conversations").with(as(sarah)))
                .andExpect(jsonPath("$[0].unread").value(2));
        mvc.perform(get("/api/messages/conversations").with(as(emma)))
                .andExpect(jsonPath("$[0].unread").value(1));

        mvc.perform(post("/api/messages/read").with(as(sarah)).contentType(APPLICATION_JSON)
                .content("{\"conversation\": \"family\"}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/messages/conversations").with(as(sarah)))
                .andExpect(jsonPath("$[0].unread").value(0));

        Thread.sleep(5);
        sendOk(lucas, "family", "Drei");
        mvc.perform(get("/api/messages/conversations").with(as(sarah)))
                .andExpect(jsonPath("$[0].unread").value(1));
        // Wer schreibt, hat gelesen
        sendOk(emma, "family", "Vier");
        mvc.perform(get("/api/messages/conversations").with(as(emma)))
                .andExpect(jsonPath("$[0].unread").value(0));
    }

    @Test
    void ownMessagesCanBeDeletedAndAdministratorsModerateTheFamilyGroup() throws Exception {
        String fromSarah = sendOk(sarah, "family", "Von Sarah");
        String fromLucas = sendOk(lucas, "family", "Von Lucas");
        String ownByLucas = sendOk(lucas, "family", "Vertippt");

        mvc.perform(delete("/api/messages/" + fromSarah).with(as(lucas))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/messages/" + ownByLucas).with(as(lucas))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/messages/" + fromLucas).with(as(sarah))).andExpect(status().isNoContent());
        assertThat(messages.findAll()).extracting(ChatMessage::text).containsExactly("Von Sarah");
    }

    @Test
    void inDirectChatsOnlyOwnMessagesCanBeDeleted() throws Exception {
        String fromEmma = sendOk(emma, direct(sarah, emma), "Darf ich zu Lena?");

        mvc.perform(delete("/api/messages/" + fromEmma).with(as(sarah))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/messages/" + fromEmma).with(as(lucas))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/messages/" + fromEmma).with(as(emma))).andExpect(status().isNoContent());
    }

    @Test
    void rejectsEmptyTooLongAndUnknownConversations() throws Exception {
        send(lucas, "family", "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("Nachricht darf nicht leer sein"));
        send(lucas, "family", "x".repeat(2001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("Nachricht darf höchstens 2000 Zeichen lang sein"));
        send(lucas, "direct:" + lucas.id() + ":" + lucas.id(), "Selbstgespräch").andExpect(status().isNotFound());
        send(lucas, "direct:" + lucas.id(), "kaputt").andExpect(status().isNotFound());
        send(lucas, direct(lucas, oma), "Hallo Oma").andExpect(status().isNotFound());
        send(lucas, "gruppe", "Hallo").andExpect(status().isNotFound());
    }

    @Test
    void deletingMemberRemovesDirectChatsButKeepsFamilyGroupMessages() throws Exception {
        sendOk(emma, "family", "Bis später");
        sendOk(emma, direct(sarah, emma), "Bin bei Lena");
        sendOk(lucas, direct(lucas, sarah), "Bleibt");

        mvc.perform(delete("/api/members/" + emma.id()).with(as(sarah))).andExpect(status().isNoContent());

        assertThat(messages.findAll()).extracting(ChatMessage::text).containsExactlyInAnyOrder("Bis später", "Bleibt");
        assertThat(reads.findAll()).extracting(ReadMark::memberId).doesNotContain(emma.id());
    }
}
