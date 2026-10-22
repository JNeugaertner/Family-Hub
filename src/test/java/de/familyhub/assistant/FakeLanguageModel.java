package de.familyhub.assistant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import de.familyhub.assistant.llm.LanguageModel;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.llm.ModelReply;
import de.familyhub.assistant.llm.ModelReply.StopReason;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;

// Sprachmodell für Tests: antwortet der Reihe nach mit vorbereiteten Antworten und merkt sich jede Anfrage. Mit
// @Import(FakeLanguageModel.Config.class) ersetzt es in Spring-Tests den echten Anbieter.
public class FakeLanguageModel implements LanguageModel {

    public static final String MODEL = "test-model";

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        FakeLanguageModel fakeLanguageModel() {
            return new FakeLanguageModel();
        }
    }

    // ModelReply oder LanguageModelException
    private final Deque<Object> script = new ArrayDeque<>();
    private final List<ModelRequest> requests = new ArrayList<>();

    @Override
    public String provider() {
        return "Test";
    }

    @Override
    public synchronized ModelReply complete(ModelRequest request) {
        requests.add(request);
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("Keine Antwort für das Test-Sprachmodell vorbereitet.");
        }
        if (next instanceof LanguageModelException e) {
            throw e;
        }
        return (ModelReply) next;
    }

    public synchronized FakeLanguageModel answer(String text, long inputTokens, long outputTokens) {
        script.add(new ModelReply(text, List.of(), StopReason.END, MODEL, inputTokens, outputTokens));
        return this;
    }

    public synchronized FakeLanguageModel callTools(long inputTokens, long outputTokens, ToolCall... calls) {
        script.add(new ModelReply(null, List.of(calls), StopReason.TOOL_CALLS, MODEL, inputTokens, outputTokens));
        return this;
    }

    public synchronized FakeLanguageModel fail(String message) {
        script.add(new LanguageModelException(message));
        return this;
    }

    public synchronized List<ModelRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized void reset() {
        script.clear();
        requests.clear();
    }
}
