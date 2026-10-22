package de.familyhub.assistant.groq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import de.familyhub.assistant.AssistantProperties;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ModelReply;
import de.familyhub.assistant.llm.ModelReply.StopReason;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;

// Die Aufrufe an Groq gegen einen simulierten Server. Der API-Schlüssel darf in keiner Fehlermeldung auftauchen.
class GroqLanguageModelTest {

    private static final String KEY = "gsk_geheimer-schluessel";
    private static final String URL = "https://groq.test/openai/v1/chat/completions";
    private static final ToolSpec DATE_TOOL = new ToolSpec("heutiges_datum", "Liefert das heutige Datum.",
            Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));

    private MockRestServiceServer server;
    private GroqLanguageModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        model = new GroqLanguageModel(properties(KEY), builder);
    }

    private static AssistantProperties properties(String key) {
        return new AssistantProperties(key, "https://groq.test/openai/v1", "groq-model", Duration.ofMinutes(2));
    }

    private static ModelRequest question(String text) {
        return new ModelRequest("Antworte kurz.", List.of(new ModelMessage.User(text)), List.of());
    }

    @Test
    void sendsTheConversationInTheOpenAiFormat() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.model").value("groq-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("Antworte kurz."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Welcher Tag ist heute?"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].type").value("function"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("heutiges_datum"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.arguments").value("{}"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].content").value("Mittwoch, 21. Oktober 2026"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("heutiges_datum"))
                .andExpect(jsonPath("$.tools[0].function.parameters.type").value("object"))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andRespond(withSuccess("""
                        {"id": "chatcmpl-1", "model": "groq-model",
                         "choices": [{"index": 0, "finish_reason": "stop",
                                      "message": {"role": "assistant", "content": "Heute ist Mittwoch."}}],
                         "usage": {"prompt_tokens": 120, "completion_tokens": 8, "total_tokens": 128}}
                        """, MediaType.APPLICATION_JSON));

        ModelReply reply = model.complete(new ModelRequest("Antworte kurz.", List.of(
                new ModelMessage.User("Welcher Tag ist heute?"),
                new ModelMessage.Assistant(null, List.of(new ToolCall("call_1", "heutiges_datum", "{}"))),
                new ModelMessage.ToolResult("call_1", "Mittwoch, 21. Oktober 2026")), List.of(DATE_TOOL)));

        assertThat(reply).isEqualTo(new ModelReply("Heute ist Mittwoch.", List.of(), StopReason.END, "groq-model",
                120, 8));
        server.verify();
    }

    @Test
    void readsToolCallsFromTheReply() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"model": "groq-model",
                         "choices": [{"finish_reason": "tool_calls", "message": {"role": "assistant", "content": null,
                           "tool_calls": [{"id": "call_7", "type": "function",
                                           "function": {"name": "heutiges_datum", "arguments": "{\\"x\\": 1}"}}]}}],
                         "usage": {"prompt_tokens": 50, "completion_tokens": 12}}
                        """, MediaType.APPLICATION_JSON));

        ModelReply reply = model.complete(new ModelRequest(null, List.of(new ModelMessage.User("Datum?")),
                List.of(DATE_TOOL)));

        assertThat(reply.text()).isNull();
        assertThat(reply.stopReason()).isEqualTo(StopReason.TOOL_CALLS);
        assertThat(reply.toolCalls()).containsExactly(new ToolCall("call_7", "heutiges_datum", "{\"x\": 1}"));
        assertThat(reply.inputTokens()).isEqualTo(50);
        server.verify();
    }

    @Test
    void withoutToolsNoToolFieldsAreSent() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.tool_choice").doesNotExist())
                .andRespond(withSuccess("""
                        {"choices": [{"finish_reason": "length", "message": {"content": "Abgeschn"}}]}
                        """, MediaType.APPLICATION_JSON));

        ModelReply reply = model.complete(question("Erzähl was."));

        assertThat(reply.stopReason()).isEqualTo(StopReason.LENGTH);
        assertThat(reply.model()).isNull();
        server.verify();
    }

    private String failureMessage(HttpStatus status, String body) {
        server.expect(requestTo(URL)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
        Throwable thrown = catchThrowable(() -> model.complete(question("Hallo")));
        assertThat(thrown).isInstanceOf(LanguageModelException.class);
        assertThat(thrown.getMessage()).doesNotContain(KEY);
        return thrown.getMessage();
    }

    @Test
    void commonErrorsGetUnderstandableMessagesWithoutTheKey() {
        String error = "{\"error\": {\"message\": \"Invalid API Key " + KEY + "\", \"type\": \"invalid_request_error\"}}";
        assertThat(failureMessage(HttpStatus.UNAUTHORIZED, error)).contains("familyhub.assistant.api-key");
        setUp();
        assertThat(failureMessage(HttpStatus.NOT_FOUND, "{}")).contains("„groq-model“");
        setUp();
        assertThat(failureMessage(HttpStatus.TOO_MANY_REQUESTS, "{}")).contains("zu viele Anfragen pro Minute");
        setUp();
        assertThat(failureMessage(HttpStatus.TOO_MANY_REQUESTS, "{\"error\": {\"message\": \"Rate limit reached for "
                + "model `openai/gpt-oss-120b` on tokens per day (TPD): Limit 200000, Used 199500\"}}"))
                .contains("Tageskontingent");
        setUp();
        assertThat(failureMessage(HttpStatus.SERVICE_UNAVAILABLE, "{}")).contains("gestört (HTTP 503)");
        setUp();
        assertThat(failureMessage(HttpStatus.BAD_REQUEST,
                "{\"error\": {\"message\": \"messages: must not be empty\"}}"))
                .isEqualTo("Groq hat die Anfrage abgelehnt (HTTP 400): messages: must not be empty");
    }

    private static final String HELLO = """
            {"model": "groq-model", "choices": [{"message": {"role": "assistant", "content": "Hallo!"},
             "finish_reason": "stop"}], "usage": {"prompt_tokens": 3, "completion_tokens": 2}}""";

    // Kostenloses Konto: Nennt Groq eine kurze Wartezeit, wartet FamilyHub und versucht es noch einmal
    @Test
    void waitsBrieflyWhenGroqAsksForIt() {
        List<Duration> waits = new ArrayList<>();
        model = new GroqLanguageModel(properties(KEY), builderWith(), waits::add);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "3"));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"message\": \"Rate limit reached. Please try again in 7.5s. Need more tokens?\"}}"));
        server.expect(requestTo(URL)).andRespond(withSuccess(HELLO, MediaType.APPLICATION_JSON));

        assertThat(model.complete(question("Hallo")).text()).isEqualTo("Hallo!");

        assertThat(waits).containsExactly(Duration.ofSeconds(3), Duration.ofMillis(7500));
    }

    // Das Modell hat ein Werkzeug falsch aufgerufen; Groq lehnt ab, ein neuer Versuch erzeugt eine neue Antwort
    @Test
    void invalidToolCallsOfTheModelAreTriedAgainAtOnce() {
        List<Duration> waits = new ArrayList<>();
        model = new GroqLanguageModel(properties(KEY), builderWith(), waits::add);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": {\"message\": \"Tool call validation failed: attempted to call tool 'terminе_lesen' "
                        + "which was not in request.tools\", \"code\": \"tool_use_failed\"}}"));
        server.expect(requestTo(URL)).andRespond(withSuccess(HELLO, MediaType.APPLICATION_JSON));

        assertThat(model.complete(question("Hallo")).text()).isEqualTo("Hallo!");
        assertThat(waits).isEmpty();
    }

    @Test
    void longWaitsOrTooManyAttemptsAreReported() {
        List<Duration> waits = new ArrayList<>();
        model = new GroqLanguageModel(properties(KEY), builderWith(), waits::add);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "120"));

        assertThatThrownBy(() -> model.complete(question("Hallo"))).hasMessageContaining("zu viele Anfragen");
        assertThat(waits).isEmpty();

        model = new GroqLanguageModel(properties(KEY), builderWith(), waits::add);
        for (int i = 0; i <= GroqLanguageModel.RETRIES; i++) {
            server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("retry-after", "1"));
        }
        assertThatThrownBy(() -> model.complete(question("Hallo"))).hasMessageContaining("zu viele Anfragen");
        assertThat(waits).hasSize(GroqLanguageModel.RETRIES);
    }

    // Neuer simulierter Server; das Modell darauf baut der Test selbst (z. B. mit eigener Pause)
    private RestClient.Builder builderWith() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return builder;
    }

    private String networkFailure(IOException cause) {
        setUp();
        server.expect(requestTo(URL)).andRespond(withException(cause));
        Throwable thrown = catchThrowable(() -> model.complete(question("Hallo")));
        assertThat(thrown).isInstanceOf(LanguageModelException.class);
        return thrown.getMessage();
    }

    // Die Ursache genau benennen, damit man z. B. ein sperrendes Firmennetz erkennt
    @Test
    void networkProblemsNameTheirCause() {
        assertThat(networkFailure(new UnknownHostException("groq.test")))
                .isEqualTo("Groq ist gerade nicht erreichbar: Die Adresse groq.test ist unbekannt (kein Internet, oder "
                        + "das Netz sperrt sie).");
        assertThat(networkFailure(new SSLHandshakeException("PKIX path building failed")))
                .contains("Zertifikat").contains("Firmennetzen");
        assertThat(networkFailure(new ConnectException("Connection refused")))
                .contains("wurde abgelehnt (Firewall oder Proxy)");
        assertThat(networkFailure(new SocketTimeoutException("Connect timed out")))
                .contains("innerhalb von 10 Sekunden");
        assertThat(networkFailure(new SocketTimeoutException("Read timed out")))
                .isEqualTo("Groq ist gerade nicht erreichbar: Groq hat nicht rechtzeitig geantwortet.");
        assertThat(networkFailure(new IOException("irgendwas")))
                .isEqualTo("Groq ist gerade nicht erreichbar.");
    }

    @Test
    void withoutKeyNothingIsSent() {
        model = new GroqLanguageModel(properties(""), RestClient.builder());

        assertThatThrownBy(() -> model.complete(question("Hallo")))
                .isInstanceOf(LanguageModelException.class)
                .hasMessage("Der KI-Assistent ist nicht eingerichtet.");
    }
}
