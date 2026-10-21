package de.familyhub.notifications;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.meals.MealEntry;
import de.familyhub.meals.MealEntryRepository;
import de.familyhub.meals.MealStatus;
import de.familyhub.meals.MealType;
import de.familyhub.messages.ChatMessageRepository;
import de.familyhub.messages.Conversations;
import de.familyhub.permission.Role;
import de.familyhub.rewards.Redemption;
import de.familyhub.rewards.RedemptionRepository;
import de.familyhub.rewards.RedemptionStatus;
import de.familyhub.shopping.ShoppingCategory;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.telegram.TelegramApi;
import de.familyhub.telegram.TelegramException;
import de.familyhub.telegram.TelegramLink;
import de.familyhub.telegram.TelegramLinkRepository;
import de.familyhub.testsupport.TestUsers;

// Benachrichtigungen über Telegram (Entscheidungen vom 21.10.2026) mit simuliertem Telegram (TelegramApi als Mock):
// dringende Aufgaben, kurzfristige Termine, lange offene Bestätigungen und Chat-Nachrichten.
@SpringBootTest
@AutoConfigureMockMvc
class NotificationsTest {

    private static final long SARAH_CHAT = 1;
    private static final long EMMA_CHAT = 2;
    private static final long LUCAS_CHAT = 3;
    private static final long OMA_CHAT = 4;
    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private TelegramLinkRepository links;

    @Autowired
    private NotificationSettingsRepository settings;

    @Autowired
    private PendingConfirmationRepository pending;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private ShoppingItemRepository shopping;

    @Autowired
    private MealEntryRepository meals;

    @Autowired
    private RedemptionRepository redemptions;

    @Autowired
    private ChatMessageRepository messages;

    @Autowired
    private TaskNotifications taskNotifications;

    @Autowired
    private PendingConfirmations pendingConfirmations;

    @MockitoBean
    private TelegramApi api;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;

    @BeforeEach
    void setUp() {
        links.deleteAll();
        settings.deleteAll();
        pending.deleteAll();
        tasks.deleteAll();
        events.deleteAll();
        shopping.deleteAll();
        meals.deleteAll();
        redemptions.deleteAll();
        messages.deleteAll();
        members.deleteAll();
        sarah = link(members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR)), SARAH_CHAT);
        emma = link(members.save(TestUsers.member("Emma", Role.JUGENDLICHER)), EMMA_CHAT);
        lucas = link(members.save(TestUsers.member("Lucas", Role.KIND)), LUCAS_CHAT);
        // Gäste können Telegram nicht verbinden; verknüpft ist Oma hier nur, um zu zeigen, dass sie nichts bekommt
        link(members.save(TestUsers.member("Oma", Role.GAST)), OMA_CHAT);
    }

    private FamilyMember link(FamilyMember member, long chatId) {
        links.save(new TelegramLink(null, member.id(), chatId, chatId, member.name(), Instant.now()));
        return member;
    }

    private void verifyNothingSent() {
        verify(api, never()).sendMessage(anyLong(), anyString());
    }

    // ─── Aufgaben ─────────────────────────────────────────────────────────────

    private String taskJson(String title, FamilyMember assignee, String priority) {
        return """
                {"title": "%s", "assigneeId": "%s", "dueDate": "%s", "priority": "%s", "category": "chores"}
                """.formatted(title, assignee.id(), LocalDate.now(), priority);
    }

    private String createTask(FamilyMember by, String body) throws Exception {
        String response = mvc.perform(post("/api/tasks").with(as(by)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void urgentTaskReachesTheAssigneeWhenAssignedOrRaisedToHigh() throws Exception {
        createTask(sarah, taskJson("Zimmer aufräumen", lucas, "high"));
        verify(api).sendMessage(LUCAS_CHAT, "⚠️ Dringende Aufgabe für dich: „Zimmer aufräumen“, fällig heute");

        String low = createTask(sarah, taskJson("Blumen gießen", lucas, "low"));
        mvc.perform(put("/api/tasks/" + low).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(taskJson("Blumen gießen", lucas, "high")))
                .andExpect(status().isOk());
        verify(api).sendMessage(eq(LUCAS_CHAT), contains("„Blumen gießen“"));

        // Nur den Titel ändern, sich selbst etwas zuweisen oder abhaken löst nichts aus
        clearInvocations(api);
        mvc.perform(put("/api/tasks/" + low).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(taskJson("Blumen gießen (Balkon)", lucas, "high")))
                .andExpect(status().isOk());
        createTask(sarah, taskJson("Steuer machen", sarah, "high"));
        mvc.perform(patch("/api/tasks/" + low + "/status")
                        .with(as(lucas)).contentType(APPLICATION_JSON).content("{\"status\": \"inprogress\"}"))
                .andExpect(status().isOk());
        verifyNothingSent();

        // Wird die Aufgabe jemand anderem gegeben, erfährt es die neue Person
        mvc.perform(put("/api/tasks/" + low).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(taskJson("Blumen gießen (Balkon)", emma, "high")))
                .andExpect(status().isOk());
        verify(api).sendMessage(eq(EMMA_CHAT), contains("„Blumen gießen (Balkon)“"));
        verify(api, times(1)).sendMessage(anyLong(), anyString());
    }

    @Test
    void morningReminderListsOpenUrgentTasksDueToday() {
        LocalDate today = LocalDate.now();
        tasks.save(task("Müll rausbringen", lucas, today, TaskPriority.HIGH, TaskStatus.TODO));
        tasks.save(task("Hausaufgaben", lucas, today, TaskPriority.HIGH, TaskStatus.IN_PROGRESS));
        tasks.save(task("Schon erledigt", lucas, today, TaskPriority.HIGH, TaskStatus.DONE));
        tasks.save(task("Nicht dringend", lucas, today, TaskPriority.LOW, TaskStatus.TODO));
        tasks.save(task("Erst morgen", lucas, today.plusDays(1), TaskPriority.HIGH, TaskStatus.TODO));
        tasks.save(task("Geige üben", emma, today, TaskPriority.HIGH, TaskStatus.TODO));

        taskNotifications.remindDueToday();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(api).sendMessage(eq(LUCAS_CHAT), text.capture());
        assertThat(text.getValue()).startsWith("⏰ Heute fällig:\n• ")
                .contains("„Müll rausbringen“", "„Hausaufgaben“")
                .doesNotContain("Schon erledigt", "Nicht dringend", "Erst morgen");
        verify(api).sendMessage(EMMA_CHAT, "⏰ Heute fällig: „Geige üben“");
        verify(api, times(2)).sendMessage(anyLong(), anyString());
    }

    private static Task task(String title, FamilyMember assignee, LocalDate due, TaskPriority priority,
            TaskStatus status) {
        return new Task(null, title, null, assignee.id(), due, priority, TaskCategory.CHORES, 0, status, null, null,
                null);
    }

    // ─── Termine ──────────────────────────────────────────────────────────────

    private static String eventJson(String title, LocalDateTime start, FamilyMember... who) {
        String ids = Arrays.stream(who).map(m -> '"' + m.id() + '"').collect(Collectors.joining(", "));
        return """
                {"title": "%s", "start": "%s", "end": "%s", "memberIds": [%s], "category": "appointment",
                 "location": "Praxis Dr. Weber"}
                """.formatted(title, start.format(MINUTES), start.plusHours(1).format(MINUTES), ids);
    }

    private String createEvent(FamilyMember by, String body) throws Exception {
        String response = mvc.perform(post("/api/events").with(as(by)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static LocalDateTime inHours(long hours) {
        return LocalDateTime.now().plusHours(hours).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void shortNoticeEventReachesTheOtherParticipants() throws Exception {
        createEvent(sarah, eventJson("Zahnarzt", inHours(2), sarah, lucas, emma));

        verify(api).sendMessage(eq(LUCAS_CHAT), contains("📅 Neuer Termin "));
        verify(api).sendMessage(eq(EMMA_CHAT), contains("„Zahnarzt“ (Praxis Dr. Weber)"));
        verify(api, never()).sendMessage(eq(SARAH_CHAT), anyString());

        // In drei Tagen ist nicht kurzfristig
        clearInvocations(api);
        createEvent(sarah, eventJson("Elternabend", inHours(72), sarah, lucas));
        verifyNothingSent();
    }

    @Test
    void movingIntoOrOutOfTheNextDayCountsAddedPeopleHearAlone() throws Exception {
        String later = createEvent(sarah, eventJson("Elternabend", inHours(72), sarah, lucas));
        mvc.perform(put("/api/events/" + later).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(eventJson("Elternabend", inHours(3), sarah, lucas)))
                .andExpect(status().isOk());
        verify(api).sendMessage(eq(LUCAS_CHAT), contains("📅 Termin verschoben: „Elternabend“ ist jetzt "));

        clearInvocations(api);
        mvc.perform(put("/api/events/" + later).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(eventJson("Elternabend", inHours(96), sarah, lucas)))
                .andExpect(status().isOk());
        verify(api).sendMessage(eq(LUCAS_CHAT), contains("verschoben"));

        clearInvocations(api);
        String soon = createEvent(sarah, eventJson("Training", inHours(4), sarah, emma));
        clearInvocations(api);
        mvc.perform(put("/api/events/" + soon).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(eventJson("Training", inHours(4), sarah, emma, lucas)))
                .andExpect(status().isOk());
        verify(api).sendMessage(eq(LUCAS_CHAT), contains("📅 Neuer Termin "));
        verify(api, times(1)).sendMessage(anyLong(), anyString());
    }

    @Test
    void proposalsCountOnlyOnceApproved() throws Exception {
        String proposal = createEvent(emma, eventJson("Kino", inHours(5), emma, lucas));
        verifyNothingSent();

        mvc.perform(post("/api/events/" + proposal + "/approve").with(as(sarah))).andExpect(status().isOk());
        verify(api).sendMessage(eq(EMMA_CHAT), contains("„Kino“"));
        verify(api).sendMessage(eq(LUCAS_CHAT), contains("„Kino“"));
        verify(api, never()).sendMessage(eq(SARAH_CHAT), anyString());
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────

    private void sendChat(FamilyMember by, String conversation, String text) throws Exception {
        mvc.perform(post("/api/messages").with(as(by)).contentType(APPLICATION_JSON)
                        .content("{\"conversation\": \"%s\", \"text\": \"%s\"}".formatted(conversation, text)))
                .andExpect(status().isCreated());
    }

    @Test
    void directMessageGoesOnlyToThePartner() throws Exception {
        sendChat(lucas, Conversations.direct(lucas.id(), sarah.id()), "Holst du mich ab?");

        verify(api).sendMessage(SARAH_CHAT, "💬 Lucas: Holst du mich ab?");
        verify(api, times(1)).sendMessage(anyLong(), anyString());
    }

    @Test
    void familyMessageGoesToEveryoneWhoMayReadItExceptTheSender() throws Exception {
        sendChat(emma, Conversations.FAMILY, "Essen ist fertig");

        verify(api).sendMessage(SARAH_CHAT, "💬 Emma in der Familiengruppe: Essen ist fertig");
        verify(api).sendMessage(LUCAS_CHAT, "💬 Emma in der Familiengruppe: Essen ist fertig");
        verify(api, never()).sendMessage(eq(EMMA_CHAT), anyString());
        verify(api, never()).sendMessage(eq(OMA_CHAT), anyString());
    }

    // ─── Einstellungen ────────────────────────────────────────────────────────

    @Test
    void everyoneCanSwitchOffSingleKinds() throws Exception {
        mvc.perform(get("/api/notifications/settings").with(as(sarah)))
                .andExpect(jsonPath("$.tasks").value(true))
                .andExpect(jsonPath("$.events").value(true))
                .andExpect(jsonPath("$.confirmations").value(true))
                .andExpect(jsonPath("$.messages").value(true));

        mvc.perform(put("/api/notifications/settings").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"tasks\": true, \"events\": true, \"confirmations\": true, \"messages\": false}"))
                .andExpect(jsonPath("$.messages").value(false))
                .andExpect(jsonPath("$.tasks").value(true));

        sendChat(emma, Conversations.FAMILY, "Essen ist fertig");
        verify(api, never()).sendMessage(eq(SARAH_CHAT), anyString());
        verify(api).sendMessage(eq(LUCAS_CHAT), anyString());
        // Die Einstellung gilt nur für Sarah
        mvc.perform(get("/api/notifications/settings").with(as(lucas))).andExpect(jsonPath("$.messages").value(true));
    }

    @Test
    void deletingTheMemberRemovesTheSettings() throws Exception {
        mvc.perform(put("/api/notifications/settings").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"tasks\": false, \"events\": true, \"confirmations\": true, \"messages\": true}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/members/" + emma.id()).with(as(sarah))).andExpect(status().isNoContent());

        assertThat(settings.findById(emma.id())).isEmpty();
    }

    @Test
    void unreachableTelegramNeverBreaksTheRequest() throws Exception {
        doThrow(new TelegramException("Telegram ist gerade nicht erreichbar.")).when(api)
                .sendMessage(anyLong(), anyString());

        createTask(sarah, taskJson("Zimmer aufräumen", lucas, "high"));
        sendChat(lucas, Conversations.direct(lucas.id(), sarah.id()), "Hallo");
    }

    // ─── Lange offene Bestätigungen ───────────────────────────────────────────

    @Test
    void openConfirmationsRemindTheDecidersOnceAfterADay() {
        LocalDateTime now = LocalDateTime.now();
        Task done = tasks.save(new Task(null, "Zimmer aufräumen", null, lucas.id(), LocalDate.now(),
                TaskPriority.LOW, TaskCategory.CHORES, 10, TaskStatus.DONE, sarah.id(), null, null));
        events.save(new CalendarEvent(null, "Kino", now.plusDays(2), now.plusDays(2).plusHours(2),
                List.of(emma.id(), lucas.id()), EventCategory.FAMILY, null, null, false, EventStatus.PROPOSED,
                emma.id(), null));
        shopping.save(new ShoppingItem(null, "Chips", null, ShoppingCategory.SNACKS, false, false,
                ShoppingItemStatus.PROPOSED, lucas.id(), now, null));
        meals.save(new MealEntry(null, LocalDate.now().plusDays(1), MealType.ABENDESSEN, null, "Pizza",
                MealStatus.PROPOSED, lucas.id(), now));
        redemptions.save(new Redemption(null, "r1", "Kinoabend", "🎬", 50, emma.id(), RedemptionStatus.PENDING, now,
                emma.id(), null, null, null));

        // Beim ersten Abgleich merkt sich FamilyHub nur, seit wann alles offen ist
        pendingConfirmations.remind();
        assertThat(pending.count()).isEqualTo(5);
        verifyNothingSent();

        // Einen Tag später: eine Nachricht an die Eltern mit allem, was offen ist, an niemanden sonst
        pending.saveAll(pending.findAll().stream()
                .map(p -> new PendingConfirmation(p.key(), p.since().minus(Duration.ofHours(25)), false))
                .toList());
        pendingConfirmations.remind();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(api).sendMessage(eq(SARAH_CHAT), text.capture());
        assertThat(text.getValue()).startsWith("⏳ Seit über 24 Stunden offen, bitte bestätigen oder ablehnen:\n• ")
                .contains("Aufgabe „Zimmer aufräumen“, erledigt von Lucas")
                .contains("Terminvorschlag „Kino“", "von Emma")
                .contains("Einkaufsvorschlag „Chips“ von Lucas")
                .contains("Essenswunsch „Pizza“ morgen von Lucas")
                .contains("Belohnung „Kinoabend“ für Emma");
        verify(api, times(1)).sendMessage(anyLong(), anyString());

        // Erinnert wird nur einmal; was bestätigt ist, vergisst FamilyHub
        clearInvocations(api);
        tasks.save(done.confirmed(now, sarah.id()));
        pendingConfirmations.remind();
        verifyNothingSent();
        assertThat(pending.findById("task:" + done.id())).isEmpty();
        assertThat(pending.count()).isEqualTo(4);
    }
}
