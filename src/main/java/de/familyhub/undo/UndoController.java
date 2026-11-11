package de.familyhub.undo;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Je Bereich ein eigener Pfad, damit die Live-Aktualisierung (ChangeNotifier, erstes Pfadsegment) den richtigen
// Bereich meldet und offene Browser die Liste neu laden.
@RestController
@Tag(name = "Rückgängig")
public class UndoController {

    public record Restored(int restored) {
    }

    private final UndoService undo;
    private final CurrentMember currentMember;

    public UndoController(UndoService undo, CurrentMember currentMember) {
        this.undo = undo;
        this.currentMember = currentMember;
    }

    @PostMapping("/api/events/undo/{token}")
    @Operation(summary = "Gelöschten Termin wiederherstellen",
            description = "Mit der Marke aus dem Header X-Undo-Token der Löschung; nur wer gelöscht hat, 5 Minuten lang.")
    public Restored events(@PathVariable String token) {
        return new Restored(undo.restore("events", token, currentMember.get()));
    }

    @PostMapping("/api/tasks/undo/{token}")
    @Operation(summary = "Gelöschte Aufgaben wiederherstellen",
            description = "Auch nach „Erledigte löschen“. Marke aus dem Header X-Undo-Token, nur wer gelöscht hat, "
                    + "5 Minuten lang.")
    public Restored tasks(@PathVariable String token) {
        return new Restored(undo.restore("tasks", token, currentMember.get()));
    }

    @PostMapping("/api/shopping/undo/{token}")
    @Operation(summary = "Gelöschte Artikel wiederherstellen",
            description = "Auch nach „Abgehakte entfernen“ (wiederkehrende Artikel bekommen ihren alten Stand zurück). "
                    + "Marke aus dem Header X-Undo-Token, nur wer gelöscht hat, 5 Minuten lang.")
    public Restored shopping(@PathVariable String token) {
        return new Restored(undo.restore("shopping", token, currentMember.get()));
    }
}
