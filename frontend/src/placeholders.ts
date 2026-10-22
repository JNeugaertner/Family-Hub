// Funktionen, die noch Platzhalter sind (feste Beispieldaten, keine echte Funktion). Die Oberfläche kennzeichnet sie
// mit „🚧 Demnächst“ oder blendet sie aus. Wird eine Funktion echt, hier ihre Zeile auf false setzen: Die
// Kennzeichnung verschwindet dann überall.
export const PLACEHOLDER = {
  // KI-Assistent: seit Phase 3 echt (lesender Chat über Groq, Hinweise im KI-Streifen der Übersicht)
  assistant: false,
  // Sprachassistent: schwebender Mikrofon-Knopf ohne Spracherkennung (ausgeblendet)
  voice: true,
  // Glocke: Beispielmeldungen
  notifications: true,
  // Fahrzeiten: werden nicht berechnet (Legende im Kalender ausgeblendet); Terminkonflikte sind echt (KI-Assistent)
  travelTimes: true,
} as const;
