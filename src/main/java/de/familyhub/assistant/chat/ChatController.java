package de.familyhub.assistant.chat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import de.familyhub.assistant.drafts.Draft;
import de.familyhub.assistant.drafts.Drafts;
import de.familyhub.assistant.llm.LanguageModelException;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Chat mit dem KI-Assistenten (Phase 3: nachschlagen, Phase 4: Entwürfe vorbereiten). Jede Person hat ihren eigenen
// Verlauf.
@RestController
@RequestMapping("/api/assistant/conversation")
@Tag(name = "KI-Assistent")
public class ChatController {

    private final AssistantChat chat;
    private final Drafts drafts;
    private final AssistantProperties properties;
    private final AssistantAccess access;
    private final CurrentMember currentMember;

    public ChatController(AssistantChat chat, Drafts drafts, AssistantProperties properties, AssistantAccess access,
            CurrentMember currentMember) {
        this.chat = chat;
        this.drafts = drafts;
        this.properties = properties;
        this.access = access;
        this.currentMember = currentMember;
    }

    // Eine Nachricht, wie die Oberfläche sie bekommt: Antworten mit ihren Entwürfen (abgelaufene fehlen)
    public record Turn(
            @Schema(description = "user (Frage) oder assistant (Antwort)") String role,
            String text,
            Instant at,
            @Schema(description = "Bei Antworten: Bereiche, in denen der Assistent nachgeschlagen hat, z. B. Kalender")
            List<String> sources,
            @Schema(description = "Bei Antworten: Entwürfe, die die Person übernehmen oder verwerfen kann")
            List<Draft> drafts) {
    }

    public record Conversation(List<Turn> turns) {
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
        return new Conversation(withDrafts(viewer, chat.conversationOf(viewer).turns()));
    }

    @PostMapping("/messages")
    @Operation(summary = "Frage stellen", description = "Der Assistent schlägt in Kalender, Aufgaben, Einkauf, "
            + "Essensplan, Wetter und Müllabfuhr nach, immer nur, was die fragende Person sehen darf, und antwortet. "
            + "Er ändert nichts selbst, sondern bereitet Entwürfe vor (drafts), die die Person über "
            + "/api/assistant/drafts übernimmt. sources nennt die Bereiche, in denen er nachgeschlagen hat.")
    public Turn ask(@Valid @RequestBody Question question) {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        if (!properties.isConfigured()) {
            throw ApiException.conflict("Der KI-Assistent ist nicht eingerichtet.");
        }
        try {
            return withDrafts(viewer, List.of(chat.ask(viewer, question.text().strip()))).getFirst();
        } catch (LanguageModelException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Neues Gespräch", description = "Löscht den eigenen Verlauf und die Entwürfe darin.")
    public void clear() {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        chat.clear(viewer);
    }

    private List<Turn> withDrafts(FamilyMember viewer, List<ChatTurn> turns) {
        Map<String, Draft> found = drafts.of(viewer, turns.stream().flatMap(t -> t.draftIds().stream()).toList());
        return turns.stream()
                .map(t -> new Turn(t.role(), t.text(), t.at(), t.sources(),
                        t.draftIds().stream().map(found::get).filter(Objects::nonNull).toList()))
                .toList();
    }
}
