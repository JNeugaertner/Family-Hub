# Änderungen

Alle Versionen von FamilyHub AI mit den wichtigsten Neuerungen. Jede Version ist
auf `main` mit einem Tag markiert (`v0.1.0`, …).

## v0.4.0 – 29.09.2026

### Neu

- **Essensplan:** Wochenplan mit Frühstück, Mittagessen, Abendessen und Snacks.
  Gerichte kommen aus einer Gerichte-Sammlung mit Zutaten (Menge und Kategorie)
  oder werden frei eingetragen. Kinder wünschen sich Gerichte, die Eltern
  übernehmen oder lehnen ab. Die Zutaten eines Gerichts oder der ganzen Woche
  kommen per Knopf auf die Einkaufsliste; was dort schon offen steht, wird nicht
  doppelt angelegt. Die Übersicht zeigt „Mahlzeiten heute“ aus dem Plan.
- **Wetter:** Echte Wetterdaten von OpenWeather für den Wohnort, den
  Administratoren unter „Familie“ einstellen: aktuell, Höchst- und Tiefstwert,
  die nächsten vier Tage und eine Kleidungsempfehlung mit Begründung (z. B.
  „Regenschirm · Regen ab 15 Uhr“). Alle Rollen sehen das Wetter, auch Gäste.
- **Termine für mehrere Personen:** Ein Termin kann mehrere Beteiligte haben;
  „Ganze Familie“ wählt alle außer Gästen. Gemeinsame Termine haben im Kalender
  eine eigene Farbe (Indigo) und zeigen die Beteiligten als Avatare. Der
  Personenfilter findet sie für jede beteiligte Person.
- **Bonus-Aufgaben:** Eltern legen Aufgaben ohne feste Person an, auf Wunsch
  wiederkehrend. Kinder und Jugendliche übernehmen sie (wer zuerst kommt),
  erledigen sie und bekommen nach der Bestätigung die Punkte. Zurückgeben geht,
  solange sie nicht erledigt sind.
- **Profil-Overlay:** Ein Klick auf eine Person in der Seitenleiste oder auf einen
  Avatar an Terminen und Aufgaben zeigt Rolle, Farbe, Alter und bei Kindern und
  Jugendlichen Punkte und Erfolge.
- **Übersicht:** Termine und dringende Aufgaben sind anklickbar; der Kalender bzw.
  die Aufgabenseite öffnet sich und der Eintrag leuchtet kurz auf. Artikel der
  Einkaufsliste lassen sich direkt abhaken.

### Geändert

- Seitenauswahl und Seitentitel sind auf Deutsch (Übersicht, Kalender,
  Aufgaben, Belohnungen, Einkauf, Essensplan, KI-Assistent, Nachrichten, Familie).
- Jugendliche legen Termine mit anderen Beteiligten als Vorschlag an; als eigener
  Termin zählt nur einer, an dem sie allein beteiligt sind.
- Private gemeinsame Termine sehen alle Beteiligten. Wird ein Mitglied gelöscht,
  wird es aus gemeinsamen Terminen ausgetragen.

### Hinweise zum Update

- Bestehende Termine werden beim ersten Start automatisch umgestellt
  (`memberId` → `memberIds`). Wer die REST-Schnittstelle direkt nutzt: Termine
  haben jetzt die Liste `memberIds` statt `memberId`.
- Für das Wetter braucht das Backend einen OpenWeather-Schlüssel in
  `local.properties` (`familyhub.weather.api-key`, siehe README). Ohne Schlüssel
  zeigt die Übersicht einen Hinweis, alles andere läuft normal.
- Neue Endpunkte: `/api/meals`, `/api/dishes`, `/api/weather`,
  `/api/tasks/{id}/claim` und `/api/tasks/{id}/release` (Details in der Swagger UI).
- 240 automatisierte Backend-Tests, dazu Browser-Tests je Rolle.

## v0.3.0 – 28.09.2026

- **Erfolge:** Kinder und Jugendliche erreichen automatisch Erfolge (z. B. „Erste
  Aufgabe“, „7 Tage hintereinander“) und bekommen einmalig Bonuspunkte; Eltern
  passen Ziel und Bonus an.
- **Einkaufsliste:** Gemeinsame Liste mit Kategorien, Mengen und „dringend“;
  Kinder schlagen Artikel vor, die Eltern übernehmen.
- **MongoDB per Docker:** `docker compose up -d` startet die Datenbank.

## v0.2.0 – 28.09.2026

- **Aufgaben mit Punkten:** Eltern legen Aufgaben mit Punkten an, Kinder haken ab,
  nach der Bestätigung gibt es die Punkte (mit Animation). Aufgaben lassen sich
  bearbeiten, erledigte gesammelt löschen.
- **Belohnungsshop:** Kinder lösen Punkte für Belohnungen ein, Eltern genehmigen
  oder lehnen ab.
- **Google Kalender:** Jede Person verbindet ihren Google Kalender (nur lesen),
  abgeglichen alle 15 Minuten.
- Kalender und Übersicht rechnen mit dem aktuellen Datum.

## v0.1.0 – 25.09.2026

- **Kalender-MVP:** Monats-, Wochen- und Tagesansicht, Termine anlegen, ändern und
  löschen, private Termine.
- **Rollen und Rechte:** Anmeldung, fünf Rollen (Administrator, Jugendlicher, Kind,
  Gast, KI-Agent), Einzelrechte je Person, Vorschläge mit Freigabe durch die Eltern.
