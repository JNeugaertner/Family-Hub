package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import de.familyhub.assistant.tools.Problems;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.live.LiveUpdates;
import de.familyhub.web.ApiException;

// Entwürfe übernehmen oder verwerfen. Übernommen wird Eintrag für Eintrag mit den Rechten der angemeldeten Person;
// was nicht klappt, bleibt mit der Meldung offen, damit sie es auf der Karte korrigieren und erneut übernehmen kann.
// Abgewählte Einträge gelten als verworfen.
@Service
public class Drafts {

    // So lange ist ein Entwurf beim Übernehmen gesperrt, falls der Server dabei ausfällt
    static final Duration LOCK = Duration.ofMinutes(1);

    // Ein Eintrag, den die Person übernehmen möchte, mit den Feldern, die sie auf der Karte geändert hat
    public record Choice(int index, Map<String, Object> changes) {
    }

    private final DraftRepository drafts;
    private final Map<DraftKind, DraftTool> tools;
    private final MongoTemplate mongo;
    private final LiveUpdates live;
    private final Clock clock;

    public Drafts(DraftRepository drafts, List<DraftTool> tools, MongoTemplate mongo, LiveUpdates live, Clock clock) {
        this.drafts = drafts;
        this.tools = tools.stream().collect(Collectors.toMap(DraftTool::kind, Function.identity()));
        this.mongo = mongo;
        this.live = live;
        this.clock = clock;
    }

    // Die eigenen Entwürfe zu den ids, z. B. für die Antworten im Chat-Verlauf
    public Map<String, Draft> of(FamilyMember viewer, Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return drafts.findByIdInAndMemberId(ids, viewer.id()).stream()
                .collect(Collectors.toMap(Draft::id, Function.identity()));
    }

    public Draft accept(FamilyMember viewer, String id, List<Choice> choices) {
        Draft draft = lock(viewer, id);
        List<DraftItem> items = new ArrayList<>(draft.items());
        boolean applied = false;
        try {
            if (!draft.isOpen()) {
                throw ApiException.conflict("Dieser Entwurf ist schon erledigt.");
            }
            Map<Integer, Choice> chosen = byIndex(choices, items.size());
            DraftTool tool = tools.get(draft.kind());
            for (int i = 0; i < items.size(); i++) {
                DraftItem item = items.get(i);
                if (item.state() != DraftItem.State.OPEN) {
                    continue;
                }
                Choice choice = chosen.get(i);
                if (choice == null) {
                    items.set(i, item.discarded());
                    continue;
                }
                Map<String, Object> data = new LinkedHashMap<>(item.data());
                try {
                    data.putAll(changes(tool, item, choice.changes()));
                    items.set(i, item.applied(data, tool.apply(viewer, item, data)));
                    applied = true;
                } catch (ToolException | ApiException e) {
                    items.set(i, item.failed(data, Problems.message(e)));
                }
            }
        } finally {
            draft = drafts.save(draft.withItems(items));
        }
        // Die Änderungen liefen nicht über die API des Bereichs: dessen Seiten selbst zum Neuladen auffordern
        if (applied) {
            live.publish(draft.kind().area());
        }
        return draft;
    }

    public Draft discard(FamilyMember viewer, String id) {
        Draft draft = lock(viewer, id);
        return drafts.save(draft.withItems(draft.items().stream()
                .map(item -> item.state() == DraftItem.State.OPEN ? item.discarded() : item)
                .toList()));
    }

    // Neues Gespräch, oder das Familienmitglied wird gelöscht
    public void forgetMember(String memberId) {
        drafts.deleteByMemberId(memberId);
    }

    // Nur die eigenen Entwürfe; während des Übernehmens gesperrt, damit ein Doppelklick nichts doppelt anlegt
    private Draft lock(FamilyMember viewer, String id) {
        Instant now = clock.instant();
        Query unlocked = Query.query(Criteria.where("id").is(id).and("memberId").is(viewer.id())
                .orOperator(Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lt(now)));
        Draft draft = mongo.findAndModify(unlocked, Update.update("lockedUntil", now.plus(LOCK)),
                FindAndModifyOptions.options().returnNew(true), Draft.class);
        if (draft != null) {
            return draft;
        }
        if (drafts.findByIdAndMemberId(id, viewer.id()).isPresent()) {
            throw ApiException.conflict("Der Entwurf wird gerade übernommen.");
        }
        throw ApiException.notFound("Entwurf " + id + " existiert nicht.");
    }

    private static Map<Integer, Choice> byIndex(List<Choice> choices, int size) {
        Map<Integer, Choice> chosen = new HashMap<>();
        for (Choice choice : choices) {
            if (choice.index() < 0 || choice.index() >= size) {
                throw ApiException.invalidField("items", "Den Eintrag " + choice.index() + " gibt es nicht");
            }
            if (chosen.put(choice.index(), choice) != null) {
                throw ApiException.invalidField("items", "Eintrag " + choice.index() + " ist doppelt angegeben");
            }
        }
        return chosen;
    }

    // Auf der Karte geänderte Felder; nur solche, die man dort ändern darf (z. B. nie Punkte)
    private static Map<String, Object> changes(DraftTool tool, DraftItem item, Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            return Map.of();
        }
        for (String field : changes.keySet()) {
            if (!tool.editable(item.action()).contains(field)) {
                throw new ToolException("„" + field + "“ lässt sich hier nicht ändern.");
            }
        }
        return changes;
    }
}
