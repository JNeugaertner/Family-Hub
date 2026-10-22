package de.familyhub.assistant.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

// Familienmitglieder für die Werkzeuge: Das Modell nennt Personen beim Namen, FamilyHub arbeitet mit IDs
@Component
public class People {

    private static final Set<String> ME = Set.of("ich", "mir", "mich", "mein", "meine");

    private final FamilyMemberRepository members;

    public People(FamilyMemberRepository members) {
        this.members = members;
    }

    // Name je ID für eine Antwort; gelöschte Mitglieder heißen "ehemaliges Mitglied"
    public Map<String, String> names() {
        Map<String, String> names = new HashMap<>();
        members.findAll().forEach(m -> names.put(m.id(), m.name()));
        return names;
    }

    public static List<String> namesOf(List<String> ids, Map<String, String> names) {
        return ids.stream().map(id -> names.getOrDefault(id, "ehemaliges Mitglied")).toList();
    }

    // Person aus dem Argument "person": Name (Groß-/Kleinschreibung egal) oder "ich" für die fragende Person
    public Optional<FamilyMember> resolve(FamilyMember viewer, Optional<String> name) {
        if (name.isEmpty()) {
            return Optional.empty();
        }
        String wanted = name.get().toLowerCase(Locale.ROOT);
        if (ME.contains(wanted)) {
            return Optional.of(viewer);
        }
        List<FamilyMember> all = members.findAll();
        return Optional.of(all.stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .orElseThrow(() -> new ToolException("Unbekannte Person „" + name.get() + "“. In der Familie sind: "
                        + all.stream().map(FamilyMember::name).collect(Collectors.joining(", ")) + ".")));
    }
}
