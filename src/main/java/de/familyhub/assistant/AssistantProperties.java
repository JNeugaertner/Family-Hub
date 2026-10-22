package de.familyhub.assistant;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für den KI-Assistenten (Anbieter heute: Groq). Der API-Schlüssel steht nie im Git, sondern kommt aus
// der Umgebungsvariable GROQ_API_KEY oder aus local.properties (familyhub.assistant.api-key, siehe README). Ohne
// Schlüssel oder Modell ist der Assistent "nicht eingerichtet".
@ConfigurationProperties("familyhub.assistant")
public record AssistantProperties(
        String apiKey,
        // OpenAI-kompatible Adresse des Anbieters, ohne "/chat/completions"
        @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
        // Modell beim Anbieter; muss Werkzeug-Aufrufe (Tool Use) können
        String model,
        // So lange darf eine Antwort dauern; Modelle mit Nachdenken brauchen manchmal länger
        @DefaultValue("PT2M") Duration timeout) {

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(model);
    }
}
