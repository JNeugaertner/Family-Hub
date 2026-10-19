package de.familyhub.whatsapp;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Webhook der WhatsApp Cloud API: Meta wird verifiziert, eingehende Nachrichten landen roh in der Datenbank
// (ohne Anmeldung, ohne CSRF) und werden bei erneuter Zustellung nicht doppelt gespeichert. Der Lesezugriff
// auf gespeicherte Nachrichten und den Status bleibt hinter der Anmeldung.
@SpringBootTest
@AutoConfigureMockMvc
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WhatsAppMessageRepository messages;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
    }

    private String payload(String id, String from, String body) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"E","changes":[{"field":"messages","value":{
                "messaging_product":"whatsapp",
                "messages":[{"from":"%s","id":"%s","timestamp":"1700000000","type":"text","text":{"body":"%s"}}]}
                }] }]}""".formatted(from, id, body);
    }

    @Test
    void verifyEchoesChallengeWhenTokenMatches() throws Exception {
        mvc.perform(get("/api/whatsapp").param("hub.mode", "verify")
                .param("hub.verify_token", "test-verify-token").param("hub.challenge", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("12345"));
    }

    @Test
    void verifyRejectsWrongToken() throws Exception {
        mvc.perform(get("/api/whatsapp").param("hub.mode", "verify")
                .param("hub.verify_token", "falsch").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyRejectsMissingChallenge() throws Exception {
        mvc.perform(get("/api/whatsapp").param("hub.mode", "verify")
                .param("hub.verify_token", "test-verify-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonVerifyModeAnswersOk() throws Exception {
        mvc.perform(get("/api/whatsapp").param("hub.mode", "subscribe")
                .param("hub.verify_token", "test-verify-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void incomingTextMessageIsStoredRawWithoutLogin() throws Exception {
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON)
                .content(payload("wamid.A", "4915712345678", "Hallo")))
                .andExpect(status().isOk());

        WhatsAppMessage stored = messages.findById("wamid.A").orElseThrow();
        assertThat(stored.from()).isEqualTo("4915712345678");
        assertThat(stored.text()).isEqualTo("Hallo");
        assertThat(stored.timestamp()).isNotNull();
        assertThat(stored.receivedAt()).isNotNull();
        assertThat(stored.raw()).contains("Hallo").contains("wamid.A");
    }

    @Test
    void repeatedDeliveryIsNotStoredTwice() throws Exception {
        String body = payload("wamid.A", "4915712345678", "Hallo");
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());

        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    void webhookWithoutEntriesIsIgnoredGracefully() throws Exception {
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON)
                .content("{\"object\":\"whatsapp_business_account\",\"entry\":[]}"))
                .andExpect(status().isOk());

        assertThat(messages.count()).isEqualTo(0);
    }

    @Test
    void statusAndMessagesRequireLogin() throws Exception {
        mvc.perform(get("/api/whatsapp/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/whatsapp/messages")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/whatsapp/status").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.messageCount").value(0));

        mvc.perform(get("/api/whatsapp/messages").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
