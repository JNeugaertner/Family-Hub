package de.familyhub.live;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.testsupport.TestUsers;

// Live-Aktualisierung (30.09.2026): Nach jeder erfolgreichen Änderung erfahren alle verbundenen Browser den Bereich,
// ohne Inhalte; fehlgeschlagene oder nur lesende Anfragen, Anmeldung und Passwort werden nicht gemeldet.
@SpringBootTest
@AutoConfigureMockMvc
class LiveUpdatesTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private ShoppingItemRepository shopping;

    private FamilyMember sarah;
    private FamilyMember lucas;

    @BeforeEach
    void setUp() {
        shopping.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private MvcResult connect(FamilyMember who) throws Exception {
        return mvc.perform(get("/api/live").with(as(who))).andExpect(request().asyncStarted()).andReturn();
    }

    private static String item(String name) {
        return """
                {"name": "%s", "category": "snacks"}
                """.formatted(name);
    }

    @Test
    void changeIsAnnouncedToEveryConnectedBrowserWithoutContent() throws Exception {
        MvcResult sarahsBrowser = connect(sarah);
        MvcResult lucasBrowser = connect(lucas);

        mvc.perform(post("/api/shopping").with(as(lucas)).contentType(APPLICATION_JSON).content(item("Gummibärchen")))
                .andExpect(status().isCreated());

        for (MvcResult browser : new MvcResult[] { sarahsBrowser, lucasBrowser }) {
            String stream = browser.getResponse().getContentAsString();
            assertThat(stream).contains("event:aenderung\ndata:shopping\n");
            assertThat(stream).doesNotContain("Gummibärchen");
        }
    }

    @Test
    void failedReadingAndPrivateRequestsAreNotAnnounced() throws Exception {
        MvcResult browser = connect(sarah);

        mvc.perform(post("/api/shopping").with(as(lucas)).contentType(APPLICATION_JSON).content(item("")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/shopping").with(as(lucas))).andExpect(status().isOk());
        mvc.perform(put("/api/auth/password").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + TestUsers.PASSWORD + "\", \"newPassword\": \"neuesPasswort1\"}"))
                .andExpect(status().isNoContent());

        assertThat(browser.getResponse().getContentAsString()).doesNotContain("event:aenderung");
    }

    @Test
    void onlySignedInMembersCanConnect() throws Exception {
        mvc.perform(get("/api/live")).andExpect(status().isUnauthorized());
    }

    @Test
    void areaIsTheFirstPathSegmentAfterApi() {
        assertThat(ChangeNotifier.area("/api/tasks/123/confirm")).contains("tasks");
        assertThat(ChangeNotifier.area("/api/shopping")).contains("shopping");
        assertThat(ChangeNotifier.area("/api/auth/password")).isEmpty();
        assertThat(ChangeNotifier.area("/api/live")).isEmpty();
        assertThat(ChangeNotifier.area("/swagger-ui/index.html")).isEmpty();
    }
}
