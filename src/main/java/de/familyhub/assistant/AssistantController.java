package de.familyhub.assistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.llm.LanguageModel;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// KI-Assistent, Phase 1 (Grundgerüst): Status und Verbindungstest. Fragen stellen kommt in Phase 3.
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "KI-Assistent")
public class AssistantController {

    private final AssistantProperties properties;
    private final LanguageModel model;
    private final ConnectionTest connectionTest;
    private final AssistantAccess access;
    private final CurrentMember currentMember;

    public AssistantController(AssistantProperties properties, LanguageModel model, ConnectionTest connectionTest,
            AssistantAccess access, CurrentMember currentMember) {
        this.properties = properties;
        this.model = model;
        this.connectionTest = connectionTest;
        this.access = access;
        this.currentMember = currentMember;
    }

    // configured: API-Schlüssel und Modell sind gesetzt; provider: Anbieter, z. B. Groq
    public record Status(boolean configured, String provider, String model) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status des KI-Assistenten", description = "Ohne API-Schlüssel oder Modell ist er nicht "
            + "eingerichtet (siehe README). Fragt den Anbieter nicht an.")
    public Status status() {
        access.requireUse(currentMember.get());
        return new Status(properties.isConfigured(), model.provider(), properties.model());
    }

    @PostMapping("/test")
    @Operation(summary = "Verbindung zum Sprachmodell testen", description = "Nur für Administratoren. Stellt eine "
            + "kurze Frage mit einem Test-Werkzeug (heutiges Datum, keine Familiendaten). toolCalling zeigt, ob das "
            + "Modell Werkzeuge aufrufen kann. Kostet zwei kleine Aufrufe beim Anbieter.")
    public ConnectionTest.Result test() {
        FamilyMember viewer = currentMember.get();
        access.requireManage(viewer);
        if (!properties.isConfigured()) {
            throw ApiException.conflict("Der KI-Assistent ist nicht eingerichtet.");
        }
        try {
            return connectionTest.run(viewer);
        } catch (LanguageModelException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }
}
