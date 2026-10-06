package de.familyhub.meals;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
import de.familyhub.shopping.ShoppingCategory;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.testsupport.TestUsers;

// Essensplan je Rolle (Entscheidungen vom 28.09.2026): Eltern und Jugendliche planen und pflegen die
// Gerichte-Sammlung, Kinder sehen den Plan und äußern Wünsche, nur Eltern übernehmen Wünsche, Gäste sehen nichts.
// Zutaten kommen je Gericht oder für die ganze Woche auf die Einkaufsliste, ohne Doppelte.
@SpringBootTest
@AutoConfigureMockMvc
class MealControllerTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MealEntryRepository meals;

    @Autowired
    private DishRepository dishes;

    @Autowired
    private ShoppingItemRepository shoppingItems;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private Dish bolognese;
    private Dish pancakes;
    private MealEntry mondayDinner;

    @BeforeEach
    void setUp() {
        meals.deleteAll();
        dishes.deleteAll();
        shoppingItems.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        bolognese = dishes.save(new Dish(null, "Spaghetti Bolognese", List.of(
                new Ingredient("Spaghetti", "500 g", ShoppingCategory.VORRAT),
                new Ingredient("Hackfleisch", "500 g", ShoppingCategory.FLEISCH),
                new Ingredient("Milch", "200 ml", ShoppingCategory.MILCHPRODUKTE)), sarah.id(), LocalDateTime.now()));
        pancakes = dishes.save(new Dish(null, "Pfannkuchen", List.of(
                new Ingredient("Mehl", "250 g", ShoppingCategory.VORRAT),
                new Ingredient("milch", "500 ml", ShoppingCategory.MILCHPRODUKTE),
                new Ingredient("Eier", "4 Stück", ShoppingCategory.MILCHPRODUKTE)), sarah.id(), LocalDateTime.now()));
        mondayDinner = meals.save(new MealEntry(null, MONDAY, MealType.ABENDESSEN, bolognese.id(), bolognese.name(),
                MealStatus.APPROVED, sarah.id(), LocalDateTime.now()));
    }

    private static String entry(LocalDate date, String type, String dishId, String name) {
        return "{\"date\": \"" + date + "\", \"type\": \"" + type + "\""
                + (dishId == null ? "" : ", \"dishId\": \"" + dishId + "\"")
                + (name == null ? "" : ", \"name\": \"" + name + "\"") + "}";
    }

    private String plan(FamilyMember by, LocalDate date, String type, String dishId, String name) throws Exception {
        String body = mvc.perform(post("/api/meals").with(as(by)).contentType(APPLICATION_JSON)
                        .content(entry(date, type, dishId, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String week() {
        return "/api/meals?from=" + MONDAY + "&to=" + MONDAY.plusDays(6);
    }

    @Test
    void teenagerPlansDirectlyAndReplacesTheExistingEntry() throws Exception {
        mvc.perform(post("/api/meals").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(entry(MONDAY, "abendessen", pancakes.id(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.name").value("Pfannkuchen"))
                .andExpect(jsonPath("$.dishId").value(pancakes.id()));
        String snack = plan(emma, MONDAY, "snacks", null, "  Apfelschnitze ");

        mvc.perform(get(week()).with(as(emma)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Pfannkuchen", "Apfelschnitze")));
        assertThat(meals.findById(snack).orElseThrow().dishId()).isNull();
        mvc.perform(delete("/api/meals/" + snack).with(as(emma))).andExpect(status().isNoContent());
        assertThat(meals.findAll()).extracting(MealEntry::name).containsExactly("Pfannkuchen");
    }

    @Test
    void entryNeedsDishOrFreeText() throws Exception {
        mvc.perform(post("/api/meals").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(entry(MONDAY, "mittagessen", null, " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        mvc.perform(post("/api/meals").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(entry(MONDAY, "mittagessen", "gibt-es-nicht", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dishId").exists());
        mvc.perform(get("/api/meals?from=" + MONDAY + "&to=" + MONDAY.plusDays(90)).with(as(sarah)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void childSeesPlanAndDishesAndOnlyWishes() throws Exception {
        mvc.perform(get("/api/dishes").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Pfannkuchen", "Spaghetti Bolognese")));
        mvc.perform(post("/api/meals").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(entry(MONDAY, "abendessen", pancakes.id(), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("proposed"));

        mvc.perform(get(week()).with(as(lucas)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Spaghetti Bolognese", "Pfannkuchen")));
        mvc.perform(delete("/api/meals/" + mondayDinner.id()).with(as(lucas))).andExpect(status().isForbidden());
        mvc.perform(post("/api/dishes").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Pizza\"}"))
                .andExpect(status().isForbidden());

        // eigenen Wunsch zurückziehen
        String wish = meals.findAll().stream().filter(MealEntry::isProposal).findFirst().orElseThrow().id();
        mvc.perform(delete("/api/meals/" + wish).with(as(lucas))).andExpect(status().isNoContent());
    }

    @Test
    void wishesAreVisibleOnlyToWisherAndParentsAndApprovalReplacesTheEntry() throws Exception {
        String pancakeWish = plan(lucas, MONDAY, "abendessen", pancakes.id(), null);
        String pizzaWish = plan(lucas, MONDAY.plusDays(1), "abendessen", null, "Pizza");

        mvc.perform(get(week()).with(as(emma)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Spaghetti Bolognese")));
        mvc.perform(post("/api/meals/" + pancakeWish + "/approve").with(as(emma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Wünsche übernehmen oder ablehnen."));

        mvc.perform(get(week()).with(as(sarah)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Spaghetti Bolognese", "Pfannkuchen", "Pizza")));
        plan(lucas, MONDAY.plusDays(30), "snacks", null, "Eis");
        mvc.perform(get("/api/meals/wishes").with(as(sarah)))
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.contains("Pfannkuchen", "Pizza", "Eis")));
        mvc.perform(get("/api/meals/wishes").with(as(emma))).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/meals/wishes").with(as(lucas))).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(post("/api/meals/" + pancakeWish + "/approve").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(post("/api/meals/" + pizzaWish + "/reject").with(as(sarah))).andExpect(status().isNoContent());

        mvc.perform(get(week()).with(as(emma)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Pfannkuchen")));
        mvc.perform(post("/api/meals/" + pancakeWish + "/approve").with(as(sarah))).andExpect(status().isConflict());
    }

    @Test
    void guestSeesNothing() throws Exception {
        mvc.perform(get(week()).with(as(oma))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dishes").with(as(oma))).andExpect(status().isForbidden());
        mvc.perform(post("/api/meals").with(as(oma)).contentType(APPLICATION_JSON)
                        .content(entry(MONDAY, "abendessen", null, "Kuchen")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dishKeepsRecipeWithOneStepPerLineAndChildrenCanReadIt() throws Exception {
        String recipe = "{\"name\": \"Tomatensuppe\", \"ingredients\": [], \"prepMinutes\": 25, \"servings\": 4, "
                + "\"instructions\": \"  Tomaten schneiden \\r\\n\\n Mit Brühe kochen\\n\\n  Pürieren  \\n\"}";
        String body = mvc.perform(post("/api/dishes").with(as(emma)).contentType(APPLICATION_JSON).content(recipe))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructions").value("Tomaten schneiden\nMit Brühe kochen\nPürieren"))
                .andExpect(jsonPath("$.prepMinutes").value(25))
                .andExpect(jsonPath("$.servings").value(4))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Kinder sehen die Kochanleitung in der Gerichte-Sammlung
        mvc.perform(get("/api/dishes").with(as(lucas)))
                .andExpect(jsonPath("$[?(@.name == 'Tomatensuppe')].instructions")
                        .value("Tomaten schneiden\nMit Brühe kochen\nPürieren"));

        // Nur Leerzeilen: keine Kochanleitung; Zubereitungszeit weggelassen wird entfernt
        mvc.perform(put("/api/dishes/" + id).with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Tomatensuppe\", \"instructions\": \" \\n \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructions").doesNotExist())
                .andExpect(jsonPath("$.prepMinutes").doesNotExist());

        mvc.perform(put("/api/dishes/" + id).with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Tomatensuppe\", \"prepMinutes\": 0, \"servings\": 51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.prepMinutes").value("Zubereitungszeit muss mindestens 1 Minute sein"))
                .andExpect(jsonPath("$.errors.servings").value("Höchstens 50 Portionen"));
    }

    @Test
    void teenagerManagesDishesAndRenamingOrDeletingKeepsThePlanReadable() throws Exception {
        String body = mvc.perform(post("/api/dishes").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \" Käsespätzle \", \"ingredients\": [{\"name\": \"Spätzle\", "
                                + "\"quantity\": \" \", \"category\": \"vorrat\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Käsespätzle"))
                .andExpect(jsonPath("$.ingredients[0].quantity").doesNotExist())
                .andExpect(jsonPath("$.createdBy").value(emma.id()))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.id")).isNotBlank();

        mvc.perform(post("/api/dishes").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Suppe\", \"ingredients\": [{\"name\": \"\", \"category\": null}]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/dishes/" + bolognese.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Spaghetti Bolo\", \"ingredients\": []}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spaghetti Bolo"));
        assertThat(meals.findById(mondayDinner.id()).orElseThrow().name()).isEqualTo("Spaghetti Bolo");

        mvc.perform(delete("/api/dishes/" + bolognese.id()).with(as(emma))).andExpect(status().isNoContent());
        MealEntry kept = meals.findById(mondayDinner.id()).orElseThrow();
        assertThat(kept.name()).isEqualTo("Spaghetti Bolo");
        assertThat(kept.dishId()).isNull();
    }

    @Test
    void weekToShoppingAddsIngredientsInItsCategoryAlongsideExistingItems() throws Exception {
        shoppingItems.save(new ShoppingItem(null, "SPAGHETTI", null, ShoppingCategory.VORRAT, false, false,
                ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), null));
        // abgehakt: zählt nicht als offen
        shoppingItems.save(new ShoppingItem(null, "Mehl", null, ShoppingCategory.VORRAT, false, true,
                ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), sarah.id()));
        plan(sarah, MONDAY.plusDays(1), "mittagessen", pancakes.id(), null);
        plan(sarah, MONDAY.plusDays(2), "snacks", null, "Obst");
        plan(lucas, MONDAY.plusDays(3), "abendessen", bolognese.id(), null);
        // außerhalb der Woche
        plan(sarah, MONDAY.plusDays(7), "abendessen", bolognese.id(), null);

        mvc.perform(post("/api/meals/shopping").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"from\": \"" + MONDAY + "\", \"to\": \"" + MONDAY.plusDays(6) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", org.hamcrest.Matchers.contains(
                        "Spaghetti", "Hackfleisch", "Milch", "Mehl", "Eier")))
                .andExpect(jsonPath("$.skipped").isEmpty());

        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equalsIgnoreCase("Spaghetti"))
                .hasSize(2)
                .anySatisfy(i -> assertThat(i.category()).isEqualTo(ShoppingCategory.VORRAT))
                .anySatisfy(i -> assertThat(i.category()).isEqualTo(ShoppingCategory.ZUTATEN_ESSENSPLANUNG));

        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equals("Hackfleisch"))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.quantity()).isEqualTo("500 g");
                    assertThat(i.category()).isEqualTo(ShoppingCategory.ZUTATEN_ESSENSPLANUNG);
                    assertThat(i.status()).isEqualTo(ShoppingItemStatus.APPROVED);
                    assertThat(i.createdBy()).isEqualTo(emma.id());
                });

        // zweites Mal: alles steht schon auf der Liste
        mvc.perform(post("/api/meals/" + mondayDinner.id() + "/shopping").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").isEmpty())
                .andExpect(jsonPath("$.skipped", org.hamcrest.Matchers.contains("Spaghetti", "Hackfleisch", "Milch")));
    }

    @Test
    void weekToShoppingAddsQuantitiesForRepeatedMealsAndRemovesStaleIngredients() throws Exception {
        String tuesdayDinner = plan(sarah, MONDAY.plusDays(1), "abendessen", bolognese.id(), null);
        mvc.perform(post("/api/meals/shopping").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"from\": \"" + MONDAY + "\", \"to\": \"" + MONDAY.plusDays(6) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added", org.hamcrest.Matchers.contains("Spaghetti", "Hackfleisch", "Milch")));

        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equals("Hackfleisch"))
                .singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualTo("1000 g"));
        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equals("Milch"))
                .singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualTo("400 ml"));

        mvc.perform(post("/api/meals/" + mondayDinner.id() + "/shopping").with(as(sarah)))
                .andExpect(status().isOk());
        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equals("Hackfleisch"))
                .singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualTo("1000 g"));

        mvc.perform(delete("/api/meals/" + tuesdayDinner).with(as(sarah))).andExpect(status().isNoContent());
        mvc.perform(post("/api/meals/shopping").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"from\": \"" + MONDAY + "\", \"to\": \"" + MONDAY.plusDays(6) + "\"}"))
                .andExpect(status().isOk());

        assertThat(shoppingItems.findAll()).filteredOn(i -> i.category() == ShoppingCategory.ZUTATEN_ESSENSPLANUNG)
                .extracting(i -> i.name())
                .containsExactly("Spaghetti", "Hackfleisch", "Milch");
        assertThat(shoppingItems.findAll()).filteredOn(i -> i.name().equals("Hackfleisch"))
                .singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualTo("500 g"));
    }

    @Test
    void childCannotPutIngredientsOnTheShoppingList() throws Exception {
        mvc.perform(post("/api/meals/" + mondayDinner.id() + "/shopping").with(as(lucas)))
                .andExpect(status().isForbidden());
        assertThat(shoppingItems.count()).isZero();
    }
}
