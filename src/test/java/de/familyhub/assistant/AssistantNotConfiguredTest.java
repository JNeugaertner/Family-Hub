package de.familyhub.assistant;

import static de.familyhub.testsupport.TestUsers.as;
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
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Ohne API-Schlüssel: Der Assistent ist "nicht eingerichtet", der Test ruft Groq gar nicht erst an.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "familyhub.assistant.api-key=")
class AssistantNotConfiguredTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;

    @BeforeEach
    void setUp() {
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
    }

    @Test
    void nothingWorksWithoutApiKey() throws Exception {
        mvc.perform(get("/api/assistant/status").with(as(sarah)))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.provider").value("Groq"));
        mvc.perform(post("/api/assistant/test").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Der KI-Assistent ist nicht eingerichtet."));
    }
}
