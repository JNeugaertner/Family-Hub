package de.familyhub.telegram;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

// Aufrufe an die Telegram Bot API (https://core.telegram.org/bots/api). Der Bot-Token steckt in der Adresse jedes
// Aufrufs; deshalb gehen Fehler nur mit eigenen Meldungen weiter, nie mit Adresse oder Originalmeldung.
@Component
public class TelegramApi {

    // So lange hält Telegram eine Abfrage offen, wenn gerade keine Nachricht da ist (Long Polling)
    static final int POLL_SECONDS = 25;

    private final TelegramProperties properties;
    private final RestClient rest;

    @Autowired
    public TelegramApi(TelegramProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()));
    }

    // Für Tests, die Telegram mit MockRestServiceServer simulieren
    TelegramApi(TelegramProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    // Lesezeit länger als das Long Polling, sonst bricht jede ruhige Abfrage ab
    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(POLL_SECONDS + 15));
        return factory;
    }

    public record Bot(long id, String username) {
    }

    public Bot getMe() {
        JsonNode bot = call("getMe", Map.of());
        JsonNode username = bot.path("username");
        return new Bot(bot.path("id").asLong(), username.isString() ? username.stringValue() : null);
    }

    // Neue Nachrichten seit offset (null: alle, die Telegram noch nicht als abgeholt kennt). Mit offset bestätigt
    // FamilyHub zugleich alle älteren; Telegram liefert sie danach nicht mehr.
    public List<JsonNode> getUpdates(Long offset, int timeoutSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (offset != null) {
            body.put("offset", offset);
        }
        body.put("timeout", timeoutSeconds);
        body.put("allowed_updates", List.of("message"));
        List<JsonNode> updates = new ArrayList<>();
        call("getUpdates", body).forEach(updates::add);
        return updates;
    }

    public void sendMessage(long chatId, String text) {
        call("sendMessage", Map.of("chat_id", chatId, "text", text));
    }

    private JsonNode call(String method, Map<String, ?> body) {
        JsonNode response;
        try {
            response = rest.post()
                    .uri(URI.create(properties.apiUrl() + "/bot" + properties.botToken() + "/" + method))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, result) -> {
                        throw error(result);
                    })
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // Die Meldung enthielte die Adresse samt Token
            throw new TelegramException("Telegram ist gerade nicht erreichbar.");
        }
        if (response == null || !response.path("ok").asBoolean(false)) {
            throw new TelegramException("Telegram hat die Anfrage abgelehnt.");
        }
        return response.path("result");
    }

    // Häufige Ursachen verständlich melden; Telegram schickt im Fehlerfall {"ok":false,"description":"..."}
    private static TelegramException error(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status == 401 || status == 404) {
            return new TelegramException("Der Bot-Token ist ungültig. Bitte in local.properties prüfen.");
        }
        if (status == 409) {
            return new TelegramException("Telegram liefert keine Nachrichten zum Abholen: Für den Bot ist ein Webhook "
                    + "eingerichtet, oder ein zweites FamilyHub holt schon ab.");
        }
        if (status == 403) {
            return new TelegramException("Der Bot wurde in Telegram blockiert.");
        }
        String body = new String(response.getBody().readAllBytes(), UTF_8).replaceAll("\\s+", " ").strip();
        return new TelegramException("Telegram hat die Anfrage abgelehnt (HTTP " + status + "): "
                + (body.length() > 200 ? body.substring(0, 200) + "…" : body));
    }
}
