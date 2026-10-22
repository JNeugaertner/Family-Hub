package de.familyhub.inbox;

// Eine neue Nachricht ist im Eingang gespeichert (nicht bei erneuter Zustellung). Der KI-Assistent wertet sie auf
// Wunsch aus, ohne dass der Eingang ihn kennt.
public record InboundMessageReceived(InboundMessage message) {
}
