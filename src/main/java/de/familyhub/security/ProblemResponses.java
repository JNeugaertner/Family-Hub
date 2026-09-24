package de.familyhub.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

// Fehler, die in den Security-Filtern entstehen (nicht angemeldet, CSRF, falsches Passwort), im selben
// ProblemDetail-Format wie die Fehler aus den Controllern.
@Component
public class ProblemResponses {

    private final JsonMapper jsonMapper;

    public ProblemResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status);
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    public void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
