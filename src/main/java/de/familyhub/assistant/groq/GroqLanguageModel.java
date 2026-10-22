package de.familyhub.assistant.groq;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(GroqLanguageModel.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // Zwei Fehler gehen oft beim nächsten Versuch weg, deshalb versucht FamilyHub es dann noch einmal (höchstens
    // RETRIES-mal), statt die ganze Frage scheitern zu lassen:
    // - Das kostenlose Groq-Konto erlaubt nur wenige Tokens pro Minute, eine Frage mit Nachschlagen und Entwurf braucht
    //   aber mehrere Anfragen kurz hintereinander. Nennt Groq eine kurze Wartezeit (429), wartet FamilyHub so lange.
    // - Das Modell hat ein Werkzeug falsch aufgerufen (z. B. den Namen mit einem kyrillischen Buchstaben geschrieben);
    //   Groq lehnt das mit 400 und dem Code tool_use_failed ab. Ein neuer Versuch erzeugt eine neue Antwort.
    static final Duration MAX_WAIT = Duration.ofSeconds(20);
    static final int RETRIES = 2;
    private static final Pattern TRY_AGAIN = Pattern.compile("try again in (\\d+(?:\\.\\d+)?)s");

    // Warten zwischen zwei Versuchen; in Tests ersetzbar
    interface Pause {
        void sleep(Duration duration) throws InterruptedException;
    }

    // Ein Fehler, nach dem sich ein neuer Versuch lohnen kann; wait ist die Wartezeit davor (null: kein neuer Versuch)
    private static final class Retryable extends LanguageModelException {

        private final Duration wait;

        Retryable(String message, Duration wait) {
            super(message);
            this.wait = wait;
        }
    }

    private final AssistantProperties properties;
    private final RestClient rest;
    private final Pause pause;

    @Autowired
    public GroqLanguageModel(AssistantProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties.timeout())));
    }

    // Für Tests, die Groq mit MockRestServiceServer simulieren
    GroqLanguageModel(AssistantProperties properties, RestClient.Builder builder) {
        this(properties, builder, duration -> Thread.sleep(duration));
    }

    GroqLanguageModel(AssistantProperties properties, RestClient.Builder builder, Pause pause) {
        this.properties = properties;
        this.rest = builder.build();
        this.pause = pause;
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
        for (int attempt = 0; ; attempt++) {
            try {
                return reply(send(body));
            } catch (Retryable e) {
                if (attempt >= RETRIES || e.wait == null || e.wait.compareTo(MAX_WAIT) > 0) {
                    throw e;
                }
                LOG.info("Groq: neuer Versuch in {} ms nach „{}“", e.wait.toMillis(), e.getMessage());
                try {
                    if (e.wait.isPositive()) {
                        pause.sleep(e.wait);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private JsonNode send(Map<String, Object> body) {
        try {
            return rest.post()
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
            throw unreachable(e);
        }
    }

    // Netzprobleme genauer benennen, damit man z. B. ein sperrendes Firmennetz erkennt. Die Adresse enthält keinen
    // Schlüssel (der steckt im Header), deshalb darf die Ursache ins Log.
    private LanguageModelException unreachable(RestClientException e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        LOG.warn("Groq nicht erreichbar: {}", cause.toString());
        String host = URI.create(properties.baseUrl()).getHost();
        String reason = switch (cause) {
            case UnknownHostException _ -> "Die Adresse " + host + " ist unbekannt (kein Internet, oder das Netz "
                    + "sperrt sie).";
            case SSLException _ -> "Java vertraut dem Zertifikat der Verbindung nicht. Das passiert oft in "
                    + "Firmennetzen, die verschlüsselte Verbindungen prüfen.";
            case ConnectException _ -> "Die Verbindung zu " + host + " wurde abgelehnt (Firewall oder Proxy).";
            case SocketTimeoutException timeout when String.valueOf(timeout.getMessage()).toLowerCase(Locale.ROOT)
                    .contains("connect") -> "Keine Verbindung zu " + host + " innerhalb von 10 Sekunden (Firewall "
                            + "oder Proxy).";
            case SocketTimeoutException _ -> "Groq hat nicht rechtzeitig geantwortet.";
            default -> null;
        };
        return new LanguageModelException("Groq ist gerade nicht erreichbar" + (reason == null ? "." : ": " + reason));
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
            return new Retryable("Groq meldet zu viele Anfragen oder ein ausgeschöpftes Kontingent. Bitte später "
                    + "noch einmal.", retryAfter(response));
        }
        if (status >= 500) {
            return new LanguageModelException("Groq ist gerade gestört (HTTP " + status + ").");
        }
        String body = new String(response.getBody().readAllBytes(), UTF_8);
        String detail = errorMessage(body);
        String message = "Groq hat die Anfrage abgelehnt (HTTP " + status + ")" + (detail.isEmpty() ? "." : ": " + detail);
        return status == 400 && "tool_use_failed".equals(errorCode(body)) ? new Retryable(message, Duration.ZERO)
                : new LanguageModelException(message);
    }

    private static String errorCode(String body) {
        try {
            return textOrNull(JSON.readTree(body).path("error").path("code"));
        } catch (JacksonException e) {
            return null;
        }
    }

    // Wartezeit aus dem Header retry-after (Sekunden) oder aus der Meldung ("Please try again in 7.5s")
    private static Duration retryAfter(ClientHttpResponse response) throws IOException {
        String header = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        String seconds = header;
        if (seconds == null) {
            Matcher matcher = TRY_AGAIN.matcher(new String(response.getBody().readAllBytes(), UTF_8));
            seconds = matcher.find() ? matcher.group(1) : null;
        }
        try {
            return seconds == null ? null : Duration.ofMillis(Math.round(Double.parseDouble(seconds.strip()) * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
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
