package de.familyhub.assistant.drafts;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Ein Entwurf des KI-Assistenten (Phase 4, Entscheidungen vom 22.10.2026): Der Assistent ändert nie selbst etwas,
// sondern legt Entwürfe an. Die Person sieht ihn als Karte unter der Antwort, kann die Felder anpassen, einzelne
// Einträge abwählen und übernimmt ihn selbst; erst dann ändert sich etwas, mit ihren Rechten. Mehrere Sachen
// derselben Art stehen in einem Entwurf (Sammelkarte). MongoDB löscht Entwürfe nach 30 Tagen, wie den Chat-Verlauf.
@Document("assistantDrafts")
public record Draft(
        @Id String id,
        @JsonIgnore @Indexed String memberId,
        DraftKind kind,
        List<DraftItem> items,
        @Indexed(expireAfter = "30d") Instant createdAt,
        // Während des Übernehmens gesperrt, damit ein Doppelklick nichts doppelt anlegt
        @JsonIgnore Instant lockedUntil) {

    @PersistenceCreator
    public Draft {
        items = items == null ? List.of() : List.copyOf(items);
    }

    static Draft of(String memberId, DraftKind kind, List<DraftItem> items, Instant now) {
        return new Draft(null, memberId, kind, items, now, null);
    }

    Draft withItems(List<DraftItem> newItems) {
        return new Draft(id, memberId, kind, newItems, createdAt, null);
    }

    @JsonIgnore
    public boolean isOpen() {
        return items.stream().anyMatch(item -> item.state() == DraftItem.State.OPEN);
    }
}
