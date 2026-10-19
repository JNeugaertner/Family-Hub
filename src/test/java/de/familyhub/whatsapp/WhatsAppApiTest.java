package de.familyhub.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Die Sendeanfrage an die Meta Cloud API gegen einen simulierten Server: URL, Auth-Header und Antwort.
class WhatsAppApiTest {

    private static final WhatsAppProperties PROPERTIES = new WhatsAppProperties("access-token", "12345",
            "verify-token", "https://graph.invalid");

    private MockRestServiceServer server;
    private WhatsAppApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new WhatsAppApi(PROPERTIES, builder);
    }

    @Test
    void sendTextPostsToPhoneNumberEndpointWithBearerToken() {
        server.expect(requestTo("https://graph.invalid/12345/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {"success":true,"messages":[{"id":"wamid.SENT1"}]}
                        """, MediaType.APPLICATION_JSON));

        String id = api.sendText("4915712345678", "Hallo");

        assertThat(id).isEqualTo("wamid.SENT1");
        server.verify();
    }

    @Test
    void sendTextWithoutCredentialsIsRejected() {
        WhatsAppApi unconfigured = new WhatsAppApi(new WhatsAppProperties("", "", "verify-token", "https://graph.invalid"),
                RestClient.builder());

        assertThatThrownBy(() -> unconfigured.sendText("49157", "Hallo"))
                .isInstanceOf(WhatsAppException.class)
                .hasMessageContaining("nicht eingerichtet");
    }
}
