package de.familyhub.shopping;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Einkaufsliste je Rolle (Entscheidungen vom 28.09.2026): Eltern und Jugendliche bearbeiten, Kinder sehen die Liste
// und schlagen vor, nur Eltern übernehmen Vorschläge, Gäste sehen nichts.
@SpringBootTest
@AutoConfigureMockMvc
class ShoppingControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShoppingItemRepository items;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private ShoppingItem milk;

    @BeforeEach
    void setUp() {
        items.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        milk = items.save(new ShoppingItem(null, "Milch", "2 × 1 l", ShoppingCategory.MILCHPRODUKTE, true, false,
                ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), null));
    }

    private static String json(String name) {
        return "{\"name\": \"" + name + "\", \"quantity\": \"1\", \"category\": \"snacks\"}";
    }

    private String add(FamilyMember by, String name) throws Exception {
        String body = mvc.perform(post("/api/shopping").with(as(by)).contentType(APPLICATION_JSON).content(json(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void teenagerAddsChecksEditsAndDeletes() throws Exception {
        mvc.perform(post("/api/shopping").with(as(emma)).contentType(APPLICATION_JSON).content(json("Chips")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.createdBy").value(emma.id()))
                .andExpect(jsonPath("$.checked").value(false));

        mvc.perform(patch("/api/shopping/" + milk.id() + "/checked").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"checked\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(true))
                .andExpect(jsonPath("$.checkedBy").value(emma.id()));

        mvc.perform(put("/api/shopping/" + milk.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Hafermilch\", \"quantity\": \"\", \"category\": \"milchprodukte\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hafermilch"))
                .andExpect(jsonPath("$.quantity").doesNotExist())
                .andExpect(jsonPath("$.checked").value(true));

        mvc.perform(delete("/api/shopping/" + milk.id()).with(as(emma))).andExpect(status().isNoContent());
    }

    @Test
    void childSeesListAndOnlyProposes() throws Exception {
        String id = add(lucas, "Schokolade");

        mvc.perform(get("/api/shopping").with(as(lucas)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Milch", "Schokolade")))
                .andExpect(jsonPath("$[?(@.name == 'Schokolade')].status").value("proposed"));
        mvc.perform(patch("/api/shopping/" + milk.id() + "/checked").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"checked\": true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/shopping/" + milk.id()).with(as(lucas))).andExpect(status().isForbidden());

        // eigenen Vorschlag zurückziehen
        mvc.perform(delete("/api/shopping/" + id).with(as(lucas))).andExpect(status().isNoContent());
    }

    @Test
    void proposalsAreVisibleOnlyToProposerAndParentsAndOnlyParentsDecide() throws Exception {
        String chocolate = add(lucas, "Schokolade");
        String gummies = add(lucas, "Gummibärchen");

        mvc.perform(get("/api/shopping").with(as(emma)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Milch")));
        mvc.perform(post("/api/shopping/" + chocolate + "/approve").with(as(emma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Vorschläge übernehmen oder ablehnen."));

        mvc.perform(get("/api/shopping").with(as(sarah)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Milch", "Schokolade", "Gummibärchen")));
        mvc.perform(post("/api/shopping/" + chocolate + "/approve").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(post("/api/shopping/" + gummies + "/reject").with(as(sarah))).andExpect(status().isNoContent());

        mvc.perform(get("/api/shopping").with(as(emma)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Milch", "Schokolade")));
        mvc.perform(post("/api/shopping/" + chocolate + "/approve").with(as(sarah))).andExpect(status().isConflict());
    }

    @Test
    void proposalsCannotBeCheckedBeforeApproval() throws Exception {
        String id = add(lucas, "Schokolade");

        mvc.perform(patch("/api/shopping/" + id + "/checked").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"checked\": true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void removingCheckedItemsKeepsOpenOnes() throws Exception {
        add(sarah, "Brot");
        mvc.perform(patch("/api/shopping/" + milk.id() + "/checked").with(as(sarah)).contentType(APPLICATION_JSON)
                .content("{\"checked\": true}"));

        mvc.perform(delete("/api/shopping/checked").with(as(lucas))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/shopping/checked").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));

        assertThat(items.findAll()).extracting(ShoppingItem::name).containsExactly("Brot");
    }

    @Test
    void invalidItemsAreRejectedAndGuestsSeeNothing() throws Exception {
        mvc.perform(post("/api/shopping").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \" \", \"category\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Artikel darf nicht leer sein"))
                .andExpect(jsonPath("$.errors.category").value("Kategorie ist Pflicht"));
        mvc.perform(get("/api/shopping").with(as(oma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, die Einkaufsliste anzusehen."));
        mvc.perform(post("/api/shopping").with(as(oma)).contentType(APPLICATION_JSON).content(json("Kekse")))
                .andExpect(status().isForbidden());
    }
}
