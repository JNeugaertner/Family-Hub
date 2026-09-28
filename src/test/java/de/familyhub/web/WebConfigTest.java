package de.familyhub.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebConfigTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void frontendOriginMayCallTheApi() throws Exception {
        mvc.perform(options("/api/events")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // Aufgaben abhaken nutzt PATCH (PATCH /api/tasks/{id}/status)
    @Test
    void frontendOriginMayUsePatch() throws Exception {
        mvc.perform(options("/api/tasks/1/status")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherOriginsAreBlocked() throws Exception {
        mvc.perform(options("/api/events")
                        .header("Origin", "http://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDescribesBothResources() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FamilyHub API"))
                .andExpect(jsonPath("$.paths['/api/events']").exists())
                .andExpect(jsonPath("$.paths['/api/members/{id}']").exists());
    }

    // Spring Security erwartet beim Login Formularfelder; sonst scheitert die Anmeldung in der Swagger UI.
    @Test
    void openApiDescribesLoginAsFormSoSwaggerUiCanLogIn() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.tags[0]").value("Anmeldung"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/x-www-form-urlencoded']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/json']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists());
    }

    @Test
    void swaggerUiIsAvailable() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
