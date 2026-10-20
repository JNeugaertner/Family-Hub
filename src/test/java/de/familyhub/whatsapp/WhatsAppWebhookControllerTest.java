package de.familyhub.whatsapp;

import static de.familyhub.testsupport.TestUsers.as;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HexFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

    // So schickt Meta einen Webhook: der Body mit dem App-Secret signiert (test-app-secret aus den Test-Einstellungen)
    private static MockHttpServletRequestBuilder signed(String body) {
        return post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)
                .header(WhatsAppSignature.HEADER, signature(body));
    }

    private static String signature(String body) {
        return "sha256=" + HexFormat.of().formatHex(WhatsAppSignature.sign("test-app-secret", body.getBytes(UTF_8)));
    }

    @Test
    void incomingTextMessageIsStoredRawWithoutLogin() throws Exception {
        mvc.perform(signed(payload("wamid.A", "4915712345678", "Hallo")))
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
        mvc.perform(signed(body)).andExpect(status().isOk());
        mvc.perform(signed(body)).andExpect(status().isOk());

        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    void webhookWithoutEntriesIsIgnoredGracefully() throws Exception {
        mvc.perform(signed("{\"object\":\"whatsapp_business_account\",\"entry\":[]}"))
                .andExpect(status().isOk());

        assertThat(messages.count()).isEqualTo(0);
    }

    @Test
    void messagesWithoutValidSignatureAreRejected() throws Exception {
        String body = payload("wamid.A", "4915712345678", "Hallo");

        // ohne Signatur
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // mit falschem Schlüssel signiert
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)
                        .header(WhatsAppSignature.HEADER, "sha256=" + HexFormat.of()
                                .formatHex(WhatsAppSignature.sign("fremdes-secret", body.getBytes(UTF_8)))))
                .andExpect(status().isUnauthorized());
        // Signatur eines anderen Bodys (nachträglich veränderte Nachricht)
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)
                        .header(WhatsAppSignature.HEADER, signature(payload("wamid.A", "4915712345678", "Hallo!"))))
                .andExpect(status().isUnauthorized());
        // kein gültiger Hex-Wert
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content(body)
                        .header(WhatsAppSignature.HEADER, "sha256=xyz"))
                .andExpect(status().isUnauthorized());

        assertThat(messages.count()).isZero();
    }

    @Test
    void signedButInvalidJsonIsRejected() throws Exception {
        mvc.perform(signed("kein json")).andExpect(status().isBadRequest());

        assertThat(messages.count()).isZero();
    }

    private static FamilyMember withPhone(FamilyMember m, String phone) {
        return new FamilyMember(m.id(), m.name(), m.color(), m.username(), m.passwordHash(), m.role(), m.birthDate(),
                m.roleFixed(), m.extraPermissions(), m.revokedPermissions(), phone);
    }

    @Test
    void everyoneSeesOnlyMessagesFromTheirOwnNumberAndAdministratorsSeeAll() throws Exception {
        FamilyMember emma = members.save(withPhone(TestUsers.member("Emma", Role.JUGENDLICHER), "4915712345678"));
        FamilyMember lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        FamilyMember oma = members.save(withPhone(TestUsers.member("Oma", Role.GAST), "4917099999999"));
        mvc.perform(signed(payload("wamid.A", "4915712345678", "Von Emma"))).andExpect(status().isOk());
        mvc.perform(signed(payload("wamid.B", "4917011111111", "Von jemand anderem"))).andExpect(status().isOk());
        mvc.perform(signed(payload("wamid.C", "4917099999999", "Von Oma"))).andExpect(status().isOk());

        mvc.perform(get("/api/whatsapp/messages").with(as(emma)))
                .andExpect(jsonPath("$[*].text", contains("Von Emma")));
        mvc.perform(get("/api/whatsapp/status").with(as(emma)))
                .andExpect(jsonPath("$.messageCount").value(1));
        // Ohne eingetragene Nummer: nichts
        mvc.perform(get("/api/whatsapp/messages").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // Gäste haben keinen Zugang, auch mit Nummer
        mvc.perform(get("/api/whatsapp/messages").with(as(oma)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/whatsapp/status").with(as(oma)))
                .andExpect(jsonPath("$.messageCount").value(0));
        mvc.perform(get("/api/whatsapp/messages").with(as(sarah)))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/whatsapp/status").with(as(sarah)))
                .andExpect(jsonPath("$.messageCount").value(3));
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
