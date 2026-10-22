package de.familyhub.assistant.llm;

// Schnittstelle zum Sprachmodell (Port, Entscheidung vom 22.10.2026). Der Assistent kennt nur diese Schnittstelle
// und die Typen in diesem Paket, nie einen Anbieter. Heute steckt Groq dahinter (GroqLanguageModel); ein anderer
// Anbieter ist ein weiterer Adapter, ohne dass sich der Rest ändert.
public interface LanguageModel {

    // Name des Anbieters für die Anzeige, z. B. "Groq"
    String provider();

    // Eine Runde: Das Modell antwortet mit Text oder möchte Werkzeuge aufrufen (ModelReply.toolCalls).
    // Wirft LanguageModelException mit einer Meldung für Menschen, nie mit Schlüssel oder Adresse.
    ModelReply complete(ModelRequest request);
}
