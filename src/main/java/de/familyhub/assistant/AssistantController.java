package de.familyhub.assistant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.llm.LanguageModel;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.settings.AssistantQuota;
import de.familyhub.assistant.settings.AssistantSettingsService;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// KI-Assistent: Status und Verbindungstest (Phase 1); Fragen stellen in ChatController, Einstellungen in
// AssistantSettingsController.
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "KI-Assistent")
public class AssistantController {

    private final AssistantProperties properties;
    private final LanguageModel model;
    private final ConnectionTest connectionTest;
    private final AssistantAccess access;
    private final AssistantSettingsService settings;
    private final AssistantQuota quota;
    private final CurrentMember currentMember;

    public AssistantController(AssistantProperties properties, LanguageModel model, ConnectionTest connectionTest,
            AssistantAccess access, AssistantSettingsService settings, AssistantQuota quota,
            CurrentMember currentMember) {
        this.properties = properties;
        this.model = model;
        this.connectionTest = connectionTest;
        this.access = access;
        this.settings = settings;
        this.quota = quota;
        this.currentMember = currentMember;
    }

    // configured: API-Schlüssel und Modell sind gesetzt; enabled: von den Administratoren eingeschaltet;
    // provider: Anbieter, z. B. Groq; dailyLimit: Fragen pro Tag für die angemeldete Person, 0 = unbegrenzt
    public record Status(boolean configured, boolean enabled, String provider, String model, int dailyLimit) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status des KI-Assistenten", description = "Ohne API-Schlüssel oder Modell ist er nicht "
            + "eingerichtet (siehe README). Fragt den Anbieter nicht an.")
    public Status status() {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        return new Status(properties.isConfigured(), settings.current().enabled(), model.provider(),
                properties.model(), quota.limitFor(viewer));
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
