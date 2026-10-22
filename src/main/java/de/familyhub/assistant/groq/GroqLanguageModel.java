package de.familyhub.assistant.groq;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import de.familyhub.assistant.AssistantProperties;
import de.familyhub.assistant.llm.LanguageModel;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ModelReply;
import de.familyhub.assistant.llm.ModelReply.StopReason;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Adapter für Groq (Entscheidung vom 22.10.2026) über dessen OpenAI-kompatible Chat-Completions-Schnittstelle
// (https://console.groq.com/docs/openai). Der API-Schlüssel steckt im Authorization-Header; Fehler gehen nur mit
// eigenen Meldungen weiter, damit er nie in Logs oder Antworten landet.
@Component
public class GroqLanguageModel implements LanguageModel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AssistantProperties properties;
    private final RestClient rest;

    @Autowired
    public GroqLanguageModel(AssistantProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties.timeout())));
    }

    // Für Tests, die Groq mit MockRestServiceServer simulieren
    GroqLanguageModel(AssistantProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public String provider() {
        return "Groq";
    }

    @Override
    public ModelReply complete(ModelRequest request) {
        if (!properties.isConfigured()) {
            throw new LanguageModelException("Der KI-Assistent ist nicht eingerichtet.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages(request));
        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(GroqLanguageModel::tool).toList());
            body.put("tool_choice", "auto");
        }
        JsonNode response;
        try {
            response = rest.post()
                    .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, result) -> {
                        throw error(result);
                    })
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new LanguageModelException("Groq ist gerade nicht erreichbar.");
        }
        return reply(response);
    }

    private static List<Map<String, Object>> messages(ModelRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.system() != null) {
            messages.add(Map.of("role", "system", "content", request.system()));
        }
        for (ModelMessage message : request.messages()) {
            messages.add(switch (message) {
                case ModelMessage.User user -> Map.of("role", "user", "content", user.text());
                case ModelMessage.Assistant assistant -> assistant(assistant);
                case ModelMessage.ToolResult result ->
                        Map.of("role", "tool", "tool_call_id", result.toolCallId(), "content", result.content());
            });
        }
        return messages;
    }

    // content darf fehlen, wenn das Modell nur Werkzeuge aufgerufen hat
    private static Map<String, Object> assistant(ModelMessage.Assistant assistant) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", assistant.text());
        if (!assistant.toolCalls().isEmpty()) {
            message.put("tool_calls", assistant.toolCalls().stream()
                    .map(call -> Map.of("id", call.id(), "type", "function",
                            "function", Map.of("name", call.name(), "arguments", call.argumentsJson())))
                    .toList());
        }
        return message;
    }

    private static Map<String, Object> tool(ToolSpec tool) {
        return Map.of("type", "function", "function",
                Map.of("name", tool.name(), "description", tool.description(), "parameters", tool.parameters()));
    }

    private static ModelReply reply(JsonNode response) {
        JsonNode choice = response == null ? null : response.path("choices").path(0);
        JsonNode message = choice == null ? null : choice.path("message");
        if (message == null || !message.isObject()) {
            throw new LanguageModelException("Groq hat eine unerwartete Antwort geliefert.");
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            toolCalls.add(new ToolCall(textOrNull(call.path("id")), textOrNull(function.path("name")),
                    textOrNull(function.path("arguments"))));
        }
        StopReason stop = switch (Objects.requireNonNullElse(textOrNull(choice.path("finish_reason")), "")) {
            case "stop" -> StopReason.END;
            case "tool_calls" -> StopReason.TOOL_CALLS;
            case "length" -> StopReason.LENGTH;
            default -> StopReason.OTHER;
        };
        JsonNode usage = response.path("usage");
        return new ModelReply(textOrNull(message.path("content")), toolCalls, stop, textOrNull(response.path("model")),
                usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    }

    // Häufige Ursachen verständlich melden; Groq schickt im Fehlerfall {"error": {"message": "...", ...}}
    private LanguageModelException error(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status == 401) {
            return new LanguageModelException(
                    "Der API-Schlüssel für Groq ist ungültig. Bitte familyhub.assistant.api-key prüfen.");
        }
        if (status == 403) {
            return new LanguageModelException("Groq verweigert den Zugriff mit diesem API-Schlüssel.");
        }
        if (status == 404) {
            return new LanguageModelException("Groq kennt das Modell „" + properties.model()
                    + "“ nicht. Bitte familyhub.assistant.model prüfen.");
        }
        if (status == 429) {
            return new LanguageModelException(
                    "Groq meldet zu viele Anfragen oder ein ausgeschöpftes Kontingent. Bitte später noch einmal.");
        }
        if (status >= 500) {
            return new LanguageModelException("Groq ist gerade gestört (HTTP " + status + ").");
        }
        String detail = errorMessage(new String(response.getBody().readAllBytes(), UTF_8));
        return new LanguageModelException("Groq hat die Anfrage abgelehnt (HTTP " + status + ")"
                + (detail.isEmpty() ? "." : ": " + detail));
    }

    // Die Meldung aus dem Fehler-JSON, sonst der Text; gekürzt, damit keine langen Antworten im Log landen
    private static String errorMessage(String body) {
        String detail = body;
        try {
            String message = textOrNull(JSON.readTree(body).path("error").path("message"));
            if (message != null) {
                detail = message;
            }
        } catch (JacksonException e) {
            // kein JSON: den Text nehmen
        }
        detail = detail.replaceAll("\\s+", " ").strip();
        return detail.length() > 200 ? detail.substring(0, 200) + "…" : detail;
    }

    private static String textOrNull(JsonNode value) {
        return value.isString() ? value.stringValue() : null;
    }
}
