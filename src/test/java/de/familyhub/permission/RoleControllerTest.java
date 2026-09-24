package de.familyhub.permission;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class RoleControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Test
    void listsAllRolesWithStandardPermissions() throws Exception {
        FamilyMember lily = members.save(TestUsers.member("Lily-Rollen", Role.KIND));

        mvc.perform(get("/api/roles").with(as(lily)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id",
                        contains("administrator", "jugendlicher", "kind", "gast", "ki_agent")))
                .andExpect(jsonPath("$[?(@.id == 'ki_agent')].assignable").value(contains(false)))
                .andExpect(jsonPath("$[?(@.id == 'gast')].name").value(contains("Gast")))
                .andExpect(jsonPath("$[?(@.id == 'kind')].permissions[?(@.module == 'kalender')].scope")
                        .value(containsInAnyOrder("eigen", "familie")));

        members.delete(lily);
    }
}
