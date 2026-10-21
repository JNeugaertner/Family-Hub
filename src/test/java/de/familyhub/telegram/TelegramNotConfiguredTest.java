package de.familyhub.telegram;

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

// Ohne Bot-Token: Telegram ist "nicht eingerichtet", verbinden und senden geht nicht.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "familyhub.telegram.bot-token=")
class TelegramNotConfiguredTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember emma;

    @BeforeEach
    void setUp() {
        members.deleteAll();
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
    }

    @Test
    void nothingWorksWithoutBotToken() throws Exception {
        mvc.perform(get("/api/telegram/status").with(as(emma)))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.botUsername").doesNotExist())
                .andExpect(jsonPath("$.linked").value(false));
        mvc.perform(post("/api/telegram/link").with(as(emma)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Telegram ist nicht eingerichtet."));
        mvc.perform(post("/api/telegram/test").with(as(emma))).andExpect(status().isConflict());
    }
}
