package de.familyhub.sampledata;

import java.util.List;

// Beispielnachrichten zum Ausprobieren: ein Verlauf in der Familiengruppe und ein Einzelchat der Eltern. Einige
// Nachrichten eignen sich für die Knöpfe "Auf die Einkaufsliste", "Als Termin" und "Als Aufgabe".
final class SampleMessages {

    // with: null = Familiengruppe, sonst Einzelchat zwischen from und with (Benutzernamen)
    record SampleMessage(String from, String with, String text, int minutesAgo) {
    }

    static final List<SampleMessage> MESSAGES = List.of(
            new SampleMessage("sarah", null, "Denkt dran: Samstag ist Familienessen um 18 Uhr 🍝", 26 * 60),
            new SampleMessage("lucas", null, "Darf ich danach noch Fußball schauen?", 25 * 60 + 40),
            new SampleMessage("mike", null, "Klar, wenn die Hausaufgaben fertig sind 😉", 25 * 60 + 30),
            new SampleMessage("emma", null, "Kann mich heute jemand nach dem Training abholen? Geht bis 18 Uhr.", 180),
            new SampleMessage("sarah", null, "Mach ich!", 170),
            new SampleMessage("lily", null, "Ich hab ein Bild für Oma gemalt 🎨", 95),
            new SampleMessage("lucas", null, "Hausaufgaben fertig 🙌", 60),
            new SampleMessage("sarah", "mike", "Kannst du auf dem Heimweg Milch mitbringen? Wir haben keine mehr.", 120),
            new SampleMessage("mike", "sarah", "Mach ich. Brauchen wir sonst noch was?", 110));

    private SampleMessages() {
    }
}
