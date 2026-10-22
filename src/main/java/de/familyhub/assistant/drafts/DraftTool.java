package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.familyhub.assistant.tools.AssistantTool;
import de.familyhub.assistant.tools.Problems;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.web.ApiException;

// Ein Werkzeug, mit dem der Assistent einen Entwurf anlegt (Phase 4). Es ändert selbst nichts: Jeder Eintrag wird
// schon beim Entwerfen mit den Regeln des Bereichs geprüft (darf die Person das?), gespeichert wird nur der Entwurf.
// Übernimmt die Person ihn, führt apply die Einträge über die bestehenden Controller aus, also mit ihren Rechten.
public abstract class DraftTool implements AssistantTool {

    static final int MAX_ENTRIES = 20;

    static final String NOTE = "Noch ist nichts geändert. Die Person sieht den Entwurf als Karte unter deiner Antwort, "
            + "kann ihn anpassen und übernimmt ihn selbst. Sag also nicht, dass es schon erledigt ist, und zähl die "
            + "Einzelheiten nicht auf, die stehen auf der Karte. Erwähne nur Wichtiges wie Überschneidungen.";

    // Was das Modell von einem vorbereiteten Eintrag erfährt
    protected record Prepared(DraftItem item, Map<String, Object> summary) {
    }

    private final DraftRepository drafts;
    private final Clock clock;

    protected DraftTool(DraftRepository drafts, Clock clock) {
        this.drafts = drafts;
        this.clock = clock;
    }

    public abstract DraftKind kind();

    // Einen Eintrag aus den Argumenten des Modells vorbereiten und prüfen. ToolException oder ApiException, wenn er
    // nicht passt oder die Person das nicht darf.
    protected abstract Prepared prepare(FamilyMember viewer, ToolArguments entry);

    // Felder, die die Person auf der Karte ändern darf
    public abstract Set<String> editable(DraftItem.Action action);

    // Einen Eintrag übernehmen, als die angemeldete Person. Liefert approved oder proposed (wurde ein Vorschlag).
    public abstract String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data);

    @Override
    public final Object run(FamilyMember viewer, ToolArguments args) {
        args.requireOnly(Set.of("eintraege"));
        List<ToolArguments> entries = args.objects("eintraege");
        if (entries.isEmpty()) {
            throw new ToolException("eintraege fehlt: mindestens ein Eintrag.");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new ToolException("Höchstens " + MAX_ENTRIES + " Einträge in einem Entwurf.");
        }
        List<DraftItem> items = new ArrayList<>();
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            try {
                Prepared prepared = prepare(viewer, entries.get(i));
                items.add(prepared.item());
                summaries.add(prepared.summary());
            } catch (ToolException | ApiException e) {
                throw new ToolException((entries.size() > 1 ? "Eintrag " + (i + 1) + ": " : "")
                        + Problems.message(e));
            }
        }
        Draft draft = drafts.save(Draft.of(viewer.id(), kind(), items, clock.instant()));
        Map<String, Object> forModel = new LinkedHashMap<>();
        forModel.put("entwurf", "bereit");
        forModel.put("eintraege", summaries);
        forModel.put("hinweis", NOTE);
        return new DraftCreated(draft, forModel);
    }

    // Angaben "aktion" eines Eintrags: neu, aendern oder loeschen
    protected static String action(ToolArguments entry, List<String> allowed) {
        return entry.choice("aktion", allowed)
                .orElseThrow(() -> new ToolException("aktion fehlt (" + String.join(", ", allowed) + ")."));
    }

    protected static String id(ToolArguments entry) {
        return entry.text("id").orElseThrow(() -> new ToolException(
                "id fehlt. Zum Ändern oder Löschen zuerst nachschlagen; dort steht die id."));
    }

    protected static String outcome(boolean proposal) {
        return proposal ? "proposed" : "approved";
    }

    // Zuordnung in fester Reihenfolge (Wert für das Modell -> Wert der API), damit gleiche Anfragen gleich aussehen
    protected static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
