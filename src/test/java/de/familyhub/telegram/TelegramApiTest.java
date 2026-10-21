package de.familyhub.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

// Die Aufrufe an Telegram gegen einen simulierten Server. Der Token steckt in der Adresse und darf in keiner
// Fehlermeldung auftauchen.
class TelegramApiTest {

    private static final String TOKEN = "123456:geheimer-token";
    private static final String BASE = "https://api.test/bot" + TOKEN + "/";

    private MockRestServiceServer server;
    private TelegramApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new TelegramApi(new TelegramProperties(TOKEN, false, "https://api.test"), builder);
    }

    @Test
    void getMeReadsTheBotName() {
        server.expect(requestTo(BASE + "getMe"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"ok": true, "result": {"id": 42, "is_bot": true, "first_name": "FamilyHub",
                         "username": "FamilyHubBot"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(api.getMe()).isEqualTo(new TelegramApi.Bot(42, "FamilyHubBot"));
        server.verify();
    }

    @Test
    void getUpdatesAsksForNewMessagesOnly() {
        server.expect(requestTo(BASE + "getUpdates"))
                .andExpect(jsonPath("$.offset").value(7))
                .andExpect(jsonPath("$.timeout").value(25))
                .andExpect(jsonPath("$.allowed_updates[0]").value("message"))
                .andRespond(withSuccess("""
                        {"ok": true, "result": [
                          {"update_id": 7, "message": {"message_id": 1, "text": "Hallo"}},
                          {"update_id": 8, "message": {"message_id": 2, "text": "Tschüss"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<JsonNode> updates = api.getUpdates(7L, TelegramApi.POLL_SECONDS);

        assertThat(updates).extracting(u -> u.path("update_id").asLong()).containsExactly(7L, 8L);
        server.verify();
    }

    @Test
    void firstFetchHasNoOffset() {
        server.expect(requestTo(BASE + "getUpdates"))
                .andExpect(jsonPath("$.offset").doesNotExist())
                .andRespond(withSuccess("{\"ok\": true, \"result\": []}", MediaType.APPLICATION_JSON));

        assertThat(api.getUpdates(null, 0)).isEmpty();
        server.verify();
    }

    @Test
    void sendMessageSendsChatAndText() {
        server.expect(requestTo(BASE + "sendMessage"))
                .andExpect(jsonPath("$.chat_id").value(111))
                .andExpect(jsonPath("$.text").value("Hallo Emma"))
                .andRespond(withSuccess("{\"ok\": true, \"result\": {\"message_id\": 5}}", MediaType.APPLICATION_JSON));

        api.sendMessage(111, "Hallo Emma");
        server.verify();
    }

    @Test
    void errorsAreExplainedWithoutTheToken() {
        server.expect(requestTo(BASE + "getMe"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\": false, \"error_code\": 401, \"description\": \"Unauthorized\"}"));
        server.expect(requestTo(BASE + "getUpdates"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"ok\": false, \"error_code\": 409, \"description\": \"Conflict: webhook is active\"}"));
        // Netzwerkfehler: Die Meldung des HTTP-Clients enthielte die Adresse samt Token
        server.expect(requestTo(BASE + "sendMessage"))
                .andRespond(request -> {
                    throw new IOException("Verbindung zu " + request.getURI() + " abgebrochen");
                });

        assertThatThrownBy(() -> api.getMe())
                .isInstanceOf(TelegramException.class)
                .hasMessageStartingWith("Der Bot-Token ist ungültig")
                .hasMessageNotContaining(TOKEN);
        assertThatThrownBy(() -> api.getUpdates(null, 0))
                .hasMessageContaining("Webhook")
                .hasMessageNotContaining(TOKEN);
        assertThatThrownBy(() -> api.sendMessage(111, "Hallo"))
                .isInstanceOf(TelegramException.class)
                .hasMessage("Telegram ist gerade nicht erreichbar.");
    }
}
