package de.familyhub.shopping;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

// Wiederkehrende Artikel (Entscheidung vom 05.11.2026): je Artikel "bei jedem Einkauf" oder "n Tage/Wochen/Monate
// nach dem Kauf". "Abgehakte entfernen" löscht sie nicht; mit Abstand warten sie bis Kauftag + Abstand.
@SpringBootTest
@AutoConfigureMockMvc
class RecurringShoppingTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShoppingItemRepository items;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private ShoppingRepeats repeats;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        items.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private static String body(String name, String repeat) {
        return "{\"name\": \"" + name + "\", \"category\": \"haushalt\", \"urgent\": true"
                + (repeat == null ? "" : ", \"repeat\": " + repeat) + "}";
    }

    private String add(FamilyMember by, String name, String repeat) throws Exception {
        String response = mvc.perform(post("/api/shopping").with(as(by)).contentType(APPLICATION_JSON)
                        .content(body(name, repeat)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void check(String id, boolean checked) throws Exception {
        mvc.perform(patch("/api/shopping/" + id + "/checked").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"checked\": " + checked + "}"))
                .andExpect(status().isOk());
    }

    private ShoppingItem item(String id) {
        return items.findById(id).orElseThrow();
    }

    // Gekauft vor boughtDaysAgo Tagen und schon von der Liste genommen
    private ShoppingItem waiting(String name, ShoppingRepeat repeat, int boughtDaysAgo) {
        LocalDate boughtOn = today.minusDays(boughtDaysAgo);
        return items.save(new ShoppingItem(null, name, null, ShoppingCategory.HAUSHALT, false, true,
                ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), sarah.id(), repeat, boughtOn,
                repeat.nextDue(boughtOn)));
    }

    private static ShoppingRepeat days(int interval) {
        return new ShoppingRepeat(interval, ShoppingRepeatUnit.DAYS);
    }

    @Test
    void everyTripItemsAreBackOnTheListRightAfterTheTrip() throws Exception {
        String milk = add(emma, "Milch", "{\"interval\": 5, \"unit\": \"trip\"}");
        String bread = add(emma, "Brot", null);
        assertThat(item(milk).repeat()).isEqualTo(new ShoppingRepeat(null, ShoppingRepeatUnit.TRIP));
        check(milk, true);
        check(bread, true);
        assertThat(item(milk).checkedOn()).isEqualTo(today);

        mvc.perform(delete("/api/shopping/checked").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1))
                .andExpect(jsonPath("$.backOnList").value(1))
                .andExpect(jsonPath("$.waiting").value(0));

        assertThat(items.findById(bread)).isEmpty();
        ShoppingItem back = item(milk);
        assertThat(back.checked()).isFalse();
        assertThat(back.urgent()).as("dringend galt dem letzten Einkauf").isFalse();
        assertThat(back.checkedBy()).isNull();
        assertThat(back.repeat().everyTrip()).isTrue();
    }

    @Test
    void itemsWithAnIntervalWaitUntilTheIntervalAfterThePurchase() throws Exception {
        String paper = add(emma, "Klopapier", "{\"interval\": 2, \"unit\": \"weeks\"}");
        check(paper, true);

        mvc.perform(delete("/api/shopping/checked").with(as(sarah)))
                .andExpect(jsonPath("$.deleted").value(0))
                .andExpect(jsonPath("$.backOnList").value(0))
                .andExpect(jsonPath("$.waiting").value(1));

        mvc.perform(get("/api/shopping").with(as(lucas)))
                .andExpect(jsonPath("$[0].name").value("Klopapier"))
                .andExpect(jsonPath("$[0].checked").value(true))
                .andExpect(jsonPath("$[0].repeat.interval").value(2))
                .andExpect(jsonPath("$[0].repeat.unit").value("weeks"))
                .andExpect(jsonPath("$[0].checkedOn").value(today.toString()))
                .andExpect(jsonPath("$[0].returnsOn").value(today.plusWeeks(2).toString()));

        // Nochmal abhaken ändert nichts, ein zweites "Abgehakte entfernen" lässt den wartenden Artikel in Ruhe
        check(paper, true);
        assertThat(item(paper).returnsOn()).isEqualTo(today.plusWeeks(2));
        mvc.perform(delete("/api/shopping/checked").with(as(sarah)))
                .andExpect(jsonPath("$.waiting").value(0));
        assertThat(item(paper).returnsOn()).isEqualTo(today.plusWeeks(2));
    }

    @Test
    void dueItemsComeBackOnTheirDayEvenIfNobodyRemovedThem() {
        ShoppingItem due = waiting("Kaffee", days(3), 3);
        ShoppingItem later = waiting("Spülmittel", days(3), 2);
        // abgehakt, aber noch nicht entfernt
        ShoppingItem stillChecked = items.save(new ShoppingItem(null, "Salz", null, ShoppingCategory.VORRAT, true,
                true, ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), sarah.id(), days(3),
                today.minusDays(5), null));
        // bei jedem Einkauf: erst "Abgehakte entfernen" bringt ihn zurück
        ShoppingItem everyTrip = items.save(new ShoppingItem(null, "Milch", null, ShoppingCategory.MILCHPRODUKTE,
                false, true, ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), sarah.id(),
                new ShoppingRepeat(null, ShoppingRepeatUnit.TRIP), today.minusDays(30), null));

        repeats.returnDueItems();

        assertThat(item(due.id()).checked()).isFalse();
        assertThat(item(due.id()).returnsOn()).isNull();
        assertThat(item(stillChecked.id()).checked()).isFalse();
        assertThat(item(stillChecked.id()).urgent()).isFalse();
        assertThat(item(later.id()).returnsOn()).isEqualTo(today.plusDays(1));
        assertThat(item(everyTrip.id()).checked()).isTrue();
    }

    @Test
    void uncheckingAWaitingItemPutsItBackOnTheListNow() throws Exception {
        ShoppingItem paper = waiting("Klopapier", new ShoppingRepeat(2, ShoppingRepeatUnit.WEEKS), 1);

        check(paper.id(), false);

        ShoppingItem back = item(paper.id());
        assertThat(back.checked()).isFalse();
        assertThat(back.returnsOn()).isNull();
        assertThat(back.checkedOn()).isNull();
        assertThat(back.repeat()).isEqualTo(paper.repeat());
    }

    @Test
    void aChangedIntervalCountsFromTheLastPurchase() throws Exception {
        ShoppingItem paper = waiting("Klopapier", new ShoppingRepeat(2, ShoppingRepeatUnit.WEEKS), 4);
        assertThat(paper.returnsOn()).isEqualTo(today.plusDays(10));

        mvc.perform(put("/api/shopping/" + paper.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(body("Klopapier", "{\"interval\": 1, \"unit\": \"weeks\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnsOn").value(today.plusDays(3).toString()))
                .andExpect(jsonPath("$.checked").value(true));

        // Abstand schon vorbei: sofort zurück auf die Liste
        mvc.perform(put("/api/shopping/" + paper.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(body("Klopapier", "{\"interval\": 3, \"unit\": \"days\"}")))
                .andExpect(jsonPath("$.checked").value(false))
                .andExpect(jsonPath("$.returnsOn").doesNotExist());

        ShoppingItem coffee = waiting("Kaffee", days(10), 1);
        mvc.perform(put("/api/shopping/" + coffee.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(body("Kaffee", "{\"unit\": \"trip\"}")))
                .andExpect(jsonPath("$.checked").value(false));
    }

    @Test
    void switchedOffRepeatsAreRemovedWithTheNextTrip() throws Exception {
        ShoppingItem coffee = waiting("Kaffee", days(10), 1);

        mvc.perform(put("/api/shopping/" + coffee.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(body("Kaffee", null)))
                .andExpect(jsonPath("$.repeat").doesNotExist())
                .andExpect(jsonPath("$.returnsOn").doesNotExist())
                .andExpect(jsonPath("$.checked").value(true));

        mvc.perform(delete("/api/shopping/checked").with(as(emma))).andExpect(jsonPath("$.deleted").value(1));
        assertThat(items.findAll()).isEmpty();
    }

    @Test
    void onlyThoseWhoAddDirectlyCreateRecurringItems() throws Exception {
        mvc.perform(post("/api/shopping").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(body("Schokolade", "{\"unit\": \"trip\"}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Wiederkehrende Artikel darf nur anlegen, wer direkt auf die Einkaufsliste schreibt."));
        add(lucas, "Schokolade", null);
        add(emma, "Chips", "{\"unit\": \"trip\"}");
    }

    @Test
    void invalidRepeatsAreRejected() throws Exception {
        mvc.perform(post("/api/shopping").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(body("Milch", "{\"unit\": \"days\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['repeat.interval']").value("Abstand ist Pflicht"));
        mvc.perform(post("/api/shopping").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(body("Milch", "{\"interval\": 0, \"unit\": \"days\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['repeat.interval']").value("Der Abstand muss mindestens 1 sein"));
        mvc.perform(post("/api/shopping").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(body("Milch", "{\"interval\": 3}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['repeat.unit']")
                        .value("Wie oft der Artikel wiederkommt, ist Pflicht (trip, days, weeks oder months)"));

        ShoppingItem rice = items.save(new ShoppingItem(null, "Reis", null, ShoppingCategory.ZUTATEN_ESSENSPLANUNG,
                false, false, ShoppingItemStatus.APPROVED, sarah.id(), LocalDateTime.now(), null));
        mvc.perform(put("/api/shopping/" + rice.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"name\": \"Reis\", \"category\": \"zutaten_essensplanung\", "
                                + "\"repeat\": {\"unit\": \"trip\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.repeat").value("Zutaten aus dem Essensplan wiederholen sich nicht, "
                        + "der Essensplan setzt sie bei Bedarf wieder auf die Liste."));
    }

    @Test
    void monthsEndOnTheLastDayOfShorterMonths() {
        ShoppingRepeat monthly = new ShoppingRepeat(1, ShoppingRepeatUnit.MONTHS);
        assertThat(monthly.nextDue(LocalDate.of(2027, 1, 31))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(new ShoppingRepeat(2, ShoppingRepeatUnit.WEEKS).nextDue(LocalDate.of(2026, 12, 25)))
                .isEqualTo(LocalDate.of(2027, 1, 8));
    }
}
