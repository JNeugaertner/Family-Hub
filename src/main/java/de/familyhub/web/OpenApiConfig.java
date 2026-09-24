package de.familyhub.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI familyHubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FamilyHub API")
                .version("0.1.0")
                .description("REST-Schnittstelle des Kalender-MVP: Familienmitglieder und Termine. "
                        + "Zeiten im Format 2026-09-25T10:00, Fehler als ProblemDetail (RFC 9457)."));
    }
}
