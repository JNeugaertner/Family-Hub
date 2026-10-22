package de.familyhub.assistant.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.assistant.AssistantProperties;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Chat mit dem KI-Assistenten (Phase 3: nur lesen). Jede Person hat ihren eigenen Verlauf.
@RestController
@RequestMapping("/api/assistant/conversation")
@Tag(name = "KI-Assistent")
public class ChatController {

    private final AssistantChat chat;
    private final AssistantProperties properties;
    private final AssistantAccess access;
    private final CurrentMember currentMember;

    public ChatController(AssistantChat chat, AssistantProperties properties, AssistantAccess access,
            CurrentMember currentMember) {
        this.chat = chat;
        this.properties = properties;
        this.access = access;
        this.currentMember = currentMember;
    }

    public record Conversation(List<ChatTurn> turns) {
    }

    public record Question(
            @NotBlank(message = "Bitte eine Frage eingeben")
            @Size(max = 1000, message = "Die Frage darf höchstens 1000 Zeichen lang sein") String text) {
    }

    @GetMapping
    @Operation(summary = "Eigener Chat-Verlauf", description = "Fragen und Antworten, älteste zuerst. Wird 30 Tage "
            + "nach der letzten Nachricht gelöscht.")
    public Conversation get() {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        return new Conversation(chat.conversationOf(viewer).turns());
    }

    @PostMapping("/messages")
    @Operation(summary = "Frage stellen", description = "Der Assistent schlägt in Kalender, Aufgaben, Einkauf, "
            + "Essensplan, Wetter und Müllabfuhr nach, immer nur, was die fragende Person sehen darf, und antwortet. "
            + "Er ändert nichts. sources nennt die Bereiche, in denen er nachgeschlagen hat.")
    public ChatTurn ask(@Valid @RequestBody Question question) {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        if (!properties.isConfigured()) {
            throw ApiException.conflict("Der KI-Assistent ist nicht eingerichtet.");
        }
        try {
            return chat.ask(viewer, question.text().strip());
        } catch (LanguageModelException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Neues Gespräch", description = "Löscht den eigenen Verlauf.")
    public void clear() {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        chat.clear(viewer);
    }
}
