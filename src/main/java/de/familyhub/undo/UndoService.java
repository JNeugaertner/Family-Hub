package de.familyhub.undo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.family.FamilyMember;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.task.Task;
import de.familyhub.web.ApiException;

// „Rückgängig“ für Löschungen (Termine, Aufgaben, Einkaufsartikel, auch „Abgehakte entfernen“ und „Erledigte löschen“):
// Vor der Löschung merkt sich FamilyHub die betroffenen Dokumente und gibt dem Browser eine Marke im Header
// X-Undo-Token zurück. Mit ihr stellt dieselbe Person den alten Stand kurz darauf wieder her, mit denselben Ids,
// Erstellern, Status und Punkten. Wiederhergestellt wird über MongoTemplate.save, damit die Listener (z. B. der
// Google-Abgleich) den Termin wie jede andere Änderung mitbekommen.
@Service
public class UndoService {

    public static final String HEADER = "X-Undo-Token";

    // So lange gilt „Rückgängig“; der Hinweis im Browser steht deutlich kürzer da
    static final Duration WINDOW = Duration.ofMinutes(5);

    // Welche Art von Dokument zu welchem Bereich gehört; nur diese lassen sich wiederherstellen
    static final Map<String, Class<?>> TYPES = Map.of(
            "events", CalendarEvent.class,
            "tasks", Task.class,
            "shopping", ShoppingItem.class);

    private final UndoSnapshotRepository snapshots;
    private final MongoTemplate mongo;
    private final Clock clock;

    public UndoService(UndoSnapshotRepository snapshots, MongoTemplate mongo, Clock clock) {
        this.snapshots = snapshots;
        this.mongo = mongo;
        this.clock = clock;
    }

    // Merkt sich den Stand der Dokumente vor der Änderung; null, wenn es nichts zu merken gibt
    public String remember(String area, FamilyMember who, List<?> before) {
        Class<?> type = TYPES.get(area);
        if (type == null) {
            throw new IllegalArgumentException("Unbekannter Bereich: " + area);
        }
        if (before.isEmpty()) {
            return null;
        }
        List<Document> documents = before.stream().map(entity -> {
            Document document = new Document();
            mongo.getConverter().write(type.cast(entity), document);
            return document;
        }).toList();
        String token = UUID.randomUUID().toString();
        snapshots.save(new UndoSnapshot(token, area, who.id(), Instant.now(clock), documents));
        return token;
    }

    // Stellt den gemerkten Stand wieder her und gibt zurück, wie viele Dokumente es waren
    public int restore(String area, String token, FamilyMember who) {
        UndoSnapshot snapshot = snapshots.findById(token)
                .filter(s -> s.area().equals(area) && s.memberId().equals(who.id()))
                .orElseThrow(() -> ApiException.notFound("Das lässt sich nicht mehr rückgängig machen."));
        if (snapshot.createdAt().plus(WINDOW).isBefore(Instant.now(clock))) {
            snapshots.delete(snapshot);
            throw ApiException.notFound("Das lässt sich nicht mehr rückgängig machen.");
        }
        // Erst die Marke verbrauchen: Ein doppelter Klick stellt nicht zweimal her
        snapshots.delete(snapshot);
        Class<?> type = TYPES.get(area);
        snapshot.documents().forEach(document -> mongo.save(mongo.getConverter().read(type, document)));
        return snapshot.documents().size();
    }
}
