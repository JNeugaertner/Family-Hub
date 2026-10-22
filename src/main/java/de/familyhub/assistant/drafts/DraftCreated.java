package de.familyhub.assistant.drafts;

// Ergebnis eines Entwurfs-Werkzeugs: der gespeicherte Entwurf (kommt als Karte an die Antwort) und was das Modell
// davon erfährt
public record DraftCreated(Draft draft, Object forModel) {
}
