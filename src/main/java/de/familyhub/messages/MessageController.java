package de.familyhub.messages;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.messages.MessageService.ConversationSummary;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Nachrichten")
public class MessageController {

    private final MessageService service;
    private final ChatMessageRepository messages;
    private final MessageAccess access;
    private final CurrentMember currentMember;

    public MessageController(MessageService service, ChatMessageRepository messages, MessageAccess access,
            CurrentMember currentMember) {
        this.service = service;
        this.messages = messages;
        this.access = access;
        this.currentMember = currentMember;
    }

    public record MessageRequest(
            @NotBlank(message = "Unterhaltung ist Pflicht") String conversation,
            @NotBlank(message = "Nachricht darf nicht leer sein")
            @Size(max = 2000, message = "Nachricht darf höchstens 2000 Zeichen lang sein") String text) {
    }

    public record ReadRequest(@NotBlank(message = "Unterhaltung ist Pflicht") String conversation) {
    }

    @GetMapping("/conversations")
    @Operation(summary = "Unterhaltungen", description = "Familiengruppe und Einzelchats mit allen, die Nachrichten "
            + "haben, jeweils mit letzter Nachricht und Zahl der ungelesenen Nachrichten.")
    public List<ConversationSummary> conversations() {
        FamilyMember viewer = currentMember.get();
        access.requireMessenger(viewer);
        return service.conversationsOf(viewer);
    }

    @GetMapping
    @Operation(summary = "Nachrichten einer Unterhaltung", description = "Die letzten 200, älteste zuerst. "
            + "Einzelchats sehen nur die beiden Beteiligten.")
    public List<ChatMessage> list(@RequestParam String conversation) {
        access.requireRead(currentMember.get(), conversation);
        return service.latest(conversation);
    }

    @PostMapping
    @Operation(summary = "Nachricht senden", description = "conversation: family oder die Kennung eines Einzelchats "
            + "aus GET /api/messages/conversations.")
    public ResponseEntity<ChatMessage> send(@Valid @RequestBody MessageRequest request) {
        FamilyMember viewer = currentMember.get();
        access.requireWrite(viewer, request.conversation());
        ChatMessage saved = service.send(viewer, request.conversation(), request.text());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Nachricht löschen", description = "Eigene Nachrichten; Administratoren auch fremde in der "
            + "Familiengruppe.")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireMessenger(viewer);
        ChatMessage message = messages.findById(id)
                .filter(m -> access.canRead(viewer, m.conversation()))
                .orElseThrow(() -> ApiException.notFound("Nachricht " + id + " existiert nicht."));
        access.requireDelete(viewer, message);
        messages.deleteById(id);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unterhaltung als gelesen markieren")
    public void markRead(@Valid @RequestBody ReadRequest request) {
        FamilyMember viewer = currentMember.get();
        access.requireRead(viewer, request.conversation());
        service.markRead(viewer, request.conversation());
    }
}
