package de.familyhub.undo;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventSeriesRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class UndoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private ShoppingItemRepository items;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private EventSeriesRepository series;

    @Autowired
    private UndoSnapshotRepository snapshots;

    @Autowired
    private Clock clock;

    private FamilyMember sarah;
    private FamilyMember mike;
    private FamilyMember emma;

    @BeforeEach
    void setUp() {
        snapshots.deleteAll();
        items.deleteAll();
        tasks.deleteAll();
        events.deleteAll();
        series.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        mike = members.save(TestUsers.member("Mike", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
    }

    private String createItem(FamilyMember who, String name, String repeat) throws Exception {
        String body = mvc.perform(post("/api/shopping").with(as(who)).contentType(APPLICATION_JSON).content("""
                        {"name": "%s", "quantity": "2", "category": "vorrat", "urgent": true, "repeat": %s}
                        """.formatted(name, repeat)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String deleteAndGetToken(String path, FamilyMember who) throws Exception {
        return mvc.perform(delete(path).with(as(who)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().exists(UndoService.HEADER))
                .andReturn().getResponse().getHeader(UndoService.HEADER);
    }

    private void check(String id) throws Exception {
        mvc.perform(patch("/api/shopping/" + id + "/checked").with(as(sarah)).contentType(APPLICATION_JSON)
                .content("{\"checked\": true}")).andExpect(status().isOk());
    }

    @Test
    void deletedItemComesBackWithSameIdCreatorAndFields() throws Exception {
        String id = createItem(emma, "Milch", "null");
        ShoppingItem before = items.findById(id).orElseThrow();
        String token = deleteAndGetToken("/api/shopping/" + id, emma);
        assertThat(items.findById(id)).isEmpty();

        mvc.perform(post("/api/shopping/undo/" + token).with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restored").value(1));

        assertThat(items.findById(id)).hasValue(before);
        assertThat(snapshots.count()).isZero();
    }

    @Test
    void onlyTheDeleterCanUndoAndOnlyOnce() throws Exception {
        String id = createItem(sarah, "Brot", "null");
        String token = deleteAndGetToken("/api/shopping/" + id, sarah);

        mvc.perform(post("/api/shopping/undo/" + token).with(as(mike))).andExpect(status().isNotFound());
        assertThat(items.findById(id)).isEmpty();

        mvc.perform(post("/api/shopping/undo/" + token).with(as(sarah))).andExpect(status().isOk());
        mvc.perform(post("/api/shopping/undo/" + token).with(as(sarah))).andExpect(status().isNotFound());
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void tokenOnlyWorksForItsOwnArea() throws Exception {
        String token = deleteAndGetToken("/api/shopping/" + createItem(sarah, "Eier", "null"), sarah);

        mvc.perform(post("/api/tasks/undo/" + token).with(as(sarah))).andExpect(status().isNotFound());
        mvc.perform(post("/api/events/undo/" + token).with(as(sarah))).andExpect(status().isNotFound());
        mvc.perform(post("/api/shopping/undo/unbekannt").with(as(sarah))).andExpect(status().isNotFound());
    }

    @Test
    void undoExpiresAfterTheWindow() throws Exception {
        String id = createItem(sarah, "Käse", "null");
        String token = deleteAndGetToken("/api/shopping/" + id, sarah);
        UndoSnapshot snapshot = snapshots.findById(token).orElseThrow();
        snapshots.save(new UndoSnapshot(token, snapshot.area(), snapshot.memberId(),
                Instant.now(clock).minus(UndoService.WINDOW).minusSeconds(1), snapshot.documents()));

        mvc.perform(post("/api/shopping/undo/" + token).with(as(sarah))).andExpect(status().isNotFound());
        assertThat(items.findById(id)).isEmpty();
        assertThat(snapshots.count()).isZero();
    }

    @Test
    void removingCheckedItemsCanBeUndoneIncludingRepeatingOnes() throws Exception {
        String bought = createItem(sarah, "Butter", "null");
        String everyTrip = createItem(sarah, "Wasser", "{\"unit\": \"trip\"}");
        String later = createItem(sarah, "Spülmittel", "{\"interval\": 2, \"unit\": \"weeks\"}");
        String open = createItem(sarah, "Äpfel", "null");
        check(bought);
        check(everyTrip);
        check(later);

        String token = deleteAndGetToken("/api/shopping/checked", sarah);
        assertThat(items.findById(bought)).isEmpty();
        assertThat(items.findById(everyTrip).orElseThrow().checked()).isFalse();
        assertThat(items.findById(later).orElseThrow().returnsOn()).isNotNull();

        mvc.perform(post("/api/shopping/undo/" + token).with(as(sarah)))
                .andExpect(jsonPath("$.restored").value(3));
        assertThat(items.findById(bought).orElseThrow().checked()).isTrue();
        assertThat(items.findById(everyTrip).orElseThrow().checked()).isTrue();
        assertThat(items.findById(later).orElseThrow().returnsOn()).isNull();
        assertThat(items.findById(later).orElseThrow().checked()).isTrue();
        assertThat(items.findById(open)).isPresent();
    }

    @Test
    void nothingToRemoveGivesNoToken() throws Exception {
        createItem(sarah, "Zucker", "null");
        mvc.perform(delete("/api/shopping/checked").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(UndoService.HEADER));
    }

    @Test
    void deletedTaskAndCompletedTasksComeBack() throws Exception {
        String body = mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Rasen mähen", "assigneeId": "%s", "dueDate": "%s", "priority": "high",
                         "category": "chores", "points": 0}
                        """.formatted(sarah.id(), LocalDate.now(clock))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        String token = deleteAndGetToken("/api/tasks/" + id, sarah);
        mvc.perform(post("/api/tasks/undo/" + token).with(as(sarah))).andExpect(status().isOk());
        assertThat(tasks.findById(id).orElseThrow().title()).isEqualTo("Rasen mähen");

        mvc.perform(patch("/api/tasks/" + id + "/status").with(as(sarah)).contentType(APPLICATION_JSON)
                .content("{\"status\": \"done\"}")).andExpect(status().isOk());
        String completed = deleteAndGetToken("/api/tasks/completed", sarah);
        assertThat(tasks.findById(id)).isEmpty();
        mvc.perform(post("/api/tasks/undo/" + completed).with(as(sarah))).andExpect(status().isOk());
        assertThat(tasks.findById(id).orElseThrow().status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void deletedEventComesBackButSeriesOfferNoUndo() throws Exception {
        String single = mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Elternabend", "start": "2026-12-01T19:00:00", "end": "2026-12-01T20:30:00",
                         "memberIds": ["%s", "%s"], "category": "school", "private": true}
                        """.formatted(sarah.id(), mike.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(single, "$.id");

        String token = deleteAndGetToken("/api/events/" + id, sarah);
        mvc.perform(post("/api/events/undo/" + token).with(as(sarah))).andExpect(status().isOk());
        assertThat(events.findById(id).orElseThrow()).satisfies(e -> {
            assertThat(e.title()).isEqualTo("Elternabend");
            assertThat(e.privateEvent()).isTrue();
            assertThat(e.memberIds()).containsExactly(sarah.id(), mike.id());
            assertThat(e.createdBy()).isEqualTo(sarah.id());
        });

        String first = mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Training", "start": "2026-12-02T17:00:00", "end": "2026-12-02T18:00:00",
                         "memberIds": ["%s"], "category": "sports",
                         "recurrence": {"frequency": "weekly", "count": 3}}
                        """.formatted(sarah.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/events/" + JsonPath.read(first, "$.id")).with(as(sarah)))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(UndoService.HEADER));
    }
}
