package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.assistant.tools.AgentRights;
import de.familyhub.assistant.tools.MessageTool;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.messages.ChatMessage;
import de.familyhub.messages.Conversations;
import de.familyhub.messages.MessageAccess;
import de.familyhub.messages.MessageController;
import de.familyhub.permission.Module;

// Entwürfe für Nachrichten im FamilyHub-Messenger (Entscheidung vom 22.10.2026): Der Assistent schreibt einen Text vor,
// abschicken tut ihn die Person selbst mit "Übernehmen". Nie über WhatsApp oder Telegram.
@Component
public class MessageDrafts extends DraftTool {

    static final String NAME = "nachricht_entwerfen";

    private static final Set<String> KNOWN = Set.of("an", "text");
    private static final Set<String> CREATE_FIELDS = Set.of("text");

    private final MessageTool messages;
    private final MessageAccess access;
    private final MessageController controller;
    private final AgentRights rights;
    private final DraftSupport support;

    public MessageDrafts(DraftRepository drafts, Clock clock, MessageTool messages, MessageAccess access,
            MessageController controller, AgentRights rights, DraftSupport support) {
        super(drafts, clock);
        this.messages = messages;
        this.access = access;
        this.controller = controller;
        this.rights = rights;
        this.support = support;
    }

    @Override
    public DraftKind kind() {
        return DraftKind.MESSAGE;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("an", Map.of("type", "string",
                "description", "familie für die Familiengruppe oder der Name der Person für den Einzelchat"));
        entry.put("text", Map.of("type", "string", "description", "Die Nachricht, wie die Person sie schreiben würde"));
        return new ToolSpec(NAME, "Bereitet Nachrichten im FamilyHub-Messenger als Entwurf vor, nur wenn die Person "
                + "darum bittet. Abschicken tut die Person selbst; nicht für WhatsApp oder Telegram.",
                Map.of("type", "object", "properties", Map.of("eintraege", Map.of("type", "array",
                        "items", Map.of("type", "object", "properties", entry, "required", List.of("an", "text"),
                                "additionalProperties", false))),
                        "required", List.of("eintraege"), "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.NACHRICHTEN;
    }

    @Override
    public String label() {
        return "Nachrichten";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayDraft(viewer, Module.MESSENGER);
    }

    @Override
    public Set<String> editable(DraftItem.Action action) {
        return action == DraftItem.Action.CREATE ? CREATE_FIELDS : Set.of();
    }

    @Override
    protected Prepared prepare(FamilyMember viewer, ToolArguments entry) {
        entry.requireOnly(KNOWN);
        String to = entry.text("an").orElseThrow(() -> new ToolException("an fehlt (familie oder ein Name)."));
        String conversation = messages.conversation(viewer, to);
        access.requireWrite(viewer, conversation);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversation", conversation);
        data.put("text", entry.text("text").orElseThrow(() -> new ToolException("text fehlt.")));
        support.read(data, MessageController.MessageRequest.class);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("an", Conversations.isFamily(conversation) ? "Familiengruppe" : to);
        summary.put("text", data.get("text"));
        return new Prepared(DraftItem.create(data), summary);
    }

    @Override
    public String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data) {
        ChatMessage sent = controller.send(support.read(data, MessageController.MessageRequest.class)).getBody();
        support.markViaAssistant(sent.id(), ChatMessage.class);
        return outcome(false);
    }
}
