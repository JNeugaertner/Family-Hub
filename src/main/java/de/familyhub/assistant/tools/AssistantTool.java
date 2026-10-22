package de.familyhub.assistant.tools;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;

// Ein Werkzeug, das der KI-Assistent aufrufen darf (Phase 3: nur lesen). Was es tut, entscheidet allein FamilyHub;
// das Modell kann es nur anfragen. Alle Werkzeuge sehen nie mehr als die fragende Person (Entscheidung vom
// 22.10.2026); Werkzeuge zum Ändern, Senden, Punkte vergeben oder Zuweisen gibt es bewusst nicht.
public interface AssistantTool {

    ToolSpec spec();

    // Name des Bereichs für die Oberfläche ("Nachgeschlagen in: Kalender")
    String label();

    // Steht das Werkzeug dieser Person zur Verfügung? Ihre Rechte UND die der Rolle KI-Agent (AgentRights)
    boolean availableFor(FamilyMember viewer);

    // Ergebnis als Objekt, das als JSON an das Modell geht. ToolException, wenn die Argumente nicht passen; die
    // Meldung bekommt das Modell, damit es es anders versuchen kann.
    Object run(FamilyMember viewer, ToolArguments args);
}
