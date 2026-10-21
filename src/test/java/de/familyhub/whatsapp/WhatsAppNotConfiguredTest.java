package de.familyhub.whatsapp;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.inbox.InboundMessageRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Ohne Verify-Token und App-Secret: Der Webhook lässt niemanden durch, auch nicht mit leerem Token.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "familyhub.whatsapp.verify-token=", "familyhub.whatsapp.app-secret=" })
class WhatsAppNotConfiguredTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private InboundMessageRepository messages;

    private FamilyMember sarah;

    @BeforeEach
    void setUp() {
        messages.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
    }

    @Test
    void webhookRejectsEverythingWithoutCredentials() throws Exception {
        mvc.perform(get("/api/whatsapp").param("hub.mode", "verify").param("hub.verify_token", "")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp").contentType(APPLICATION_JSON).content("{\"entry\":[]}")
                        .header(WhatsAppSignature.HEADER, "sha256=00"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/whatsapp/status").with(as(sarah)))
                .andExpect(jsonPath("$.configured").value(false));

        assertThat(messages.count()).isZero();
    }
}
