// Funktionen, die noch Platzhalter sind (feste Beispieldaten, keine echte Funktion). Die Oberfläche kennzeichnet sie
// mit „🚧 Demnächst“ oder blendet sie aus. Wird eine Funktion echt, hier ihre Zeile auf false setzen: Die
// Kennzeichnung verschwindet dann überall.
export const PLACEHOLDER = {
  // KI-Assistent: feste Antworten (Seite, Knopf „KI fragen“). Die Hinweise im KI-Streifen der Übersicht sind echt
  // (Terminkonflikte, überfällige Aufgaben; ohne KI ermittelt).
  assistant: true,
  // Sprachassistent: schwebender Mikrofon-Knopf ohne Spracherkennung (ausgeblendet)
  voice: true,
  // Glocke: Beispielmeldungen
  notifications: true,
  // Fahrzeiten und Terminkonflikte: werden nicht berechnet (Legende im Kalender ausgeblendet)
  travelTimes: true,
} as const;
