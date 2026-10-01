package de.familyhub.waste;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.task.TaskRepository;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class WasteCollectionControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private WasteCollectionRepository collections;

    @Autowired
    private TaskRepository tasks;

    private FamilyMember parent;
        private FamilyMember child;

    @BeforeEach
    void setUp() {
        collections.deleteAll();
        tasks.deleteAll();
        members.deleteAll();
        parent = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        child = members.save(TestUsers.member("Emma", Role.KIND));
    }

    @Test
    void importsPickupsAndCreatesDayBeforeTask() throws Exception {
                LocalDate pickupDate = LocalDate.now().plusDays(5);
                MockMultipartFile file = calendar(pickupDate);

        mvc.perform(multipart("/api/waste/import").file(file).param("points", "15").with(as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickups", hasSize(1)))
                .andExpect(jsonPath("$.pickups[0].type").value("Hausmüll"))
                .andExpect(jsonPath("$.pickups[0].date").value(pickupDate.toString()));

        mvc.perform(get("/api/waste").with(as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Anger 23 .ics"));
        org.junit.jupiter.api.Assertions.assertEquals(1, tasks.count());
        org.junit.jupiter.api.Assertions.assertEquals("Hausmüll rausbringen", tasks.findAll().getFirst().title());
        org.junit.jupiter.api.Assertions.assertEquals(pickupDate.minusDays(1), tasks.findAll().getFirst().dueDate());
        mvc.perform(get("/api/tasks").with(as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Hausmüll rausbringen"));

        mvc.perform(multipart("/api/waste/import").file(file).param("points", "20").with(as(parent)))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(1, tasks.count());
        org.junit.jupiter.api.Assertions.assertEquals(20, tasks.findAll().getFirst().points());
    }

    @Test
    void onlyAdministratorsCanUploadCalendar() throws Exception {
                MockMultipartFile file = calendar(LocalDate.now().plusDays(5));

        mvc.perform(multipart("/api/waste/import").file(file).param("points", "5").with(as(child)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignsGeneratedTaskToSelectedMember() throws Exception {
        MockMultipartFile file = calendar(LocalDate.now().plusDays(5));

        mvc.perform(multipart("/api/waste/import").file(file).param("points", "12")
                        .param("assigneeId", child.id()).with(as(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(child.id()));

        org.junit.jupiter.api.Assertions.assertEquals(child.id(), tasks.findAll().getFirst().assigneeId());
        org.junit.jupiter.api.Assertions.assertFalse(tasks.findAll().getFirst().bonus());
        org.junit.jupiter.api.Assertions.assertEquals(12, tasks.findAll().getFirst().points());
    }

    @Test
    void doesNotCreateTaskEarlierThanFiveDaysBeforePickup() throws Exception {
        MockMultipartFile file = calendar(LocalDate.now().plusDays(6));

        mvc.perform(multipart("/api/waste/import").file(file).param("points", "15").with(as(parent)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(0, tasks.count());
    }

    private static MockMultipartFile calendar(LocalDate pickupDate) {
        String ics = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"
                + "BEGIN:VEVENT\r\nUID:waste-1\r\nDTSTART:"
                + pickupDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "T050000Z\r\nSUMMARY:Entsorgung: Hausmüll\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n";
        return new MockMultipartFile("file", "Anger 23 .ics", "text/calendar",
                ics.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}