package de.familyhub.assistant;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import de.familyhub.assistant.llm.ModelMessage;
import de.familyhub.assistant.llm.ModelReply;
import de.familyhub.assistant.llm.ModelRequest;
import de.familyhub.assistant.llm.ToolCall;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;

// Verbindungstest für Administratoren: eine kurze Frage mit einem Test-Werkzeug. Er zeigt, ob Schlüssel und Modell
// stimmen und ob das Modell Werkzeuge aufrufen kann, was der Assistent ab Phase 3 braucht. Das Werkzeug liefert nur
// das heutige Datum aus FamilyHub, keine Familiendaten.
@Service
public class ConnectionTest {

    static final String PURPOSE = "verbindungstest";
    static final String TOOL = "heutiges_datum";

    private static final String SYSTEM = "Du bist der KI-Assistent von FamilyHub. Antworte auf Deutsch in einem kurzen "
            + "Satz.";
    private static final String QUESTION = "Welches Datum ist heute laut FamilyHub? Frag es mit dem Werkzeug "
            + TOOL + " ab.";
    private static final ToolSpec DATE_TOOL = new ToolSpec(TOOL, "Liefert das heutige Datum aus FamilyHub.",
            Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN);

    private final AssistantCalls calls;
    private final Clock clock;

    public ConnectionTest(AssistantCalls calls, Clock clock) {
        this.calls = calls;
        this.clock = clock;
    }

    // toolCalling: das Modell hat das Test-Werkzeug aufgerufen und danach geantwortet
    public record Result(String reply, boolean toolCalling, String model, long inputTokens, long outputTokens) {
    }

    public Result run(FamilyMember admin) {
        List<ModelMessage> messages = new ArrayList<>(List.of(new ModelMessage.User(QUESTION)));
        ModelReply first = calls.call(admin, PURPOSE, new ModelRequest(SYSTEM, messages, List.of(DATE_TOOL)));
        if (first.toolCalls().isEmpty()) {
            return new Result(first.text(), false, first.model(), first.inputTokens(), first.outputTokens());
        }
        messages.add(new ModelMessage.Assistant(first.text(), first.toolCalls()));
        String today = LocalDate.now(clock).format(DATE);
        boolean usedTool = false;
        for (ToolCall call : first.toolCalls()) {
            boolean known = TOOL.equals(call.name());
            usedTool |= known;
            messages.add(new ModelMessage.ToolResult(call.id(), known ? today : "Unbekanntes Werkzeug"));
        }
        ModelReply second = calls.call(admin, PURPOSE, new ModelRequest(SYSTEM, messages, List.of(DATE_TOOL)));
        return new Result(second.text(), usedTool, second.model(), first.inputTokens() + second.inputTokens(),
                first.outputTokens() + second.outputTokens());
    }
}
