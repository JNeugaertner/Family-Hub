package de.familyhub.google;

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

// Ohne Zugangsdaten in local.properties: Status meldet "nicht eingerichtet", Verbinden ist nicht möglich.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "familyhub.google.client-id=")
class GoogleNotConfiguredTest {

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
    void connectIsNotPossibleWithoutCredentials() throws Exception {
        mvc.perform(get("/api/google/status").with(as(sarah)))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.connected").value(false));
        mvc.perform(post("/api/google/connect").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Google-Anbindung ist nicht eingerichtet."));
    }
}
