package de.familyhub.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.familyhub.assistant.llm.LanguageModel;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.assistant.llm.ModelReply;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.family.FamilyMember;

// Jeder Aufruf des Sprachmodells läuft hierüber und wird protokolliert (AssistantCall), ob er klappt oder nicht
@Service
public class AssistantCalls {

    private static final Logger LOG = LoggerFactory.getLogger(AssistantCalls.class);

    private final LanguageModel model;
    private final AssistantProperties properties;
    private final AssistantCallRepository calls;
    private final Clock clock;

    public AssistantCalls(LanguageModel model, AssistantProperties properties, AssistantCallRepository calls,
            Clock clock) {
        this.model = model;
        this.properties = properties;
        this.calls = calls;
        this.clock = clock;
    }

    public ModelReply call(FamilyMember member, String purpose, ModelRequest request) {
        Instant start = clock.instant();
        try {
            ModelReply reply = model.complete(request);
            calls.save(new AssistantCall(null, start, member.id(), purpose, model.provider(),
                    reply.model() == null ? properties.model() : reply.model(), true, null, reply.inputTokens(),
                    reply.outputTokens(), reply.toolCalls().size(), millisSince(start)));
            return reply;
        } catch (LanguageModelException e) {
            LOG.warn("KI-Aufruf ({}) fehlgeschlagen: {}", purpose, e.getMessage());
            calls.save(new AssistantCall(null, start, member.id(), purpose, model.provider(), properties.model(),
                    false, e.getMessage(), 0, 0, 0, millisSince(start)));
            throw e;
        }
    }

    // Beim Löschen eines Familienmitglieds
    public void forgetMember(String memberId) {
        calls.deleteByMemberId(memberId);
    }

    private long millisSince(Instant start) {
        return Duration.between(start, clock.instant()).toMillis();
    }
}
