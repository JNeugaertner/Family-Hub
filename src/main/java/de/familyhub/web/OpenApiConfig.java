package de.familyhub.web;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

@Configuration
public class OpenApiConfig {

    private static final String AUTH_TAG = "Anmeldung";

    @Bean
    public OpenAPI familyHubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("FamilyHub API")
                .version("0.1.0")
                .description("REST-Schnittstelle von FamilyHub: Anmeldung, Familienmitglieder, Rollen und Termine. "
                        + "Zuerst über POST /api/auth/login anmelden, danach gilt das Sitzungs-Cookie. "
                        + "Zeiten im Format 2026-09-25T10:00, Fehler als ProblemDetail (RFC 9457)."));
    }

    // Login und Logout erledigt Spring Security ohne Controller, daher werden sie hier beschrieben.
    // Die springdoc-Automatik (show-login-endpoint) beschreibt den Login als JSON, Spring erwartet aber
    // Formularfelder; die Swagger UI könnte sich damit nicht anmelden.
    @Bean
    public OpenApiCustomizer authEndpoints() {
        return openApi -> {
            Schema<?> credentials = new ObjectSchema()
                    .addProperty("username", new StringSchema())
                    .addProperty("password", new StringSchema().format("password"));
            credentials.setRequired(List.of("username", "password"));

            Operation login = new Operation()
                    .addTagsItem(AUTH_TAG)
                    .operationId("login")
                    .summary("Anmelden")
                    .description("Formularfelder username und password. Die Antwort entspricht GET /api/auth/me.")
                    .requestBody(new RequestBody().required(true).content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                            new MediaType().schema(credentials))))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("Angemeldet")
                                    .content(new Content().addMediaType(
                                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                                            new MediaType().schema(
                                                    new Schema<>().$ref("#/components/schemas/MeResponse")))))
                            .addApiResponse("401", new ApiResponse().description("Benutzername oder Passwort falsch")));
            Operation logout = new Operation()
                    .addTagsItem(AUTH_TAG)
                    .operationId("logout")
                    .summary("Abmelden")
                    .responses(new ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("Abgemeldet")));

            openApi.path("/api/auth/login", new PathItem().post(login));
            openApi.path("/api/auth/logout", new PathItem().post(logout));
        };
    }
}
