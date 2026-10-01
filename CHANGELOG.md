# Änderungen

Alle Versionen von FamilyHub AI mit den wichtigsten Neuerungen. Jede Version ist
auf `main` mit einem Tag markiert (`v0.1.0`, …).

## v0.5.0 – 01.10.2026

### Neu

- **Müllabfuhr:** Administratoren importieren unter „Familie“ den
  ICS-Abfuhrkalender ihrer Gemeinde. Die Abholtermine erscheinen im Kalender
  (Leiste „Müllabfuhr“, Monats-, Wochen- und Tagesansicht). Bis zu fünf Tage vor
  einer Abholung entsteht automatisch die Aufgabe „… rausbringen“ für den
  Vorabend, mit einstellbaren Punkten, für eine feste Person oder offen für
  Kinder und Jugendliche.
- **Live-Aktualisierung:** Änderungen anderer Familienmitglieder erscheinen ohne
  Neuladen. Zusätzlich lädt die Seite beim Zurückkehren in den Tab und jede
  Minute nach.
- **Kochanleitung:** Gerichte haben eine Anleitung (ein Schritt pro Zeile),
  Zubereitungszeit und Portionen. Die Rezeptansicht öffnet sich aus dem Wochenplan
  und aus der Gerichte-Sammlung; die Beispielgerichte bringen Rezepte mit.
- **Als App installieren:** FamilyHub lässt sich auf Handy und PC als App mit
  eigenem Symbol installieren (siehe README).
- **Anmeldesperre:** Nach fünf falschen Passwörtern ist das Konto fünf Minuten
  gesperrt.

### Geändert

- Alle Texte sind auf Deutsch, die Woche beginnt am Montag, die Beispieldaten
  sind deutsch.
- Belohnungen lösen nur Kinder und Jugendliche für sich selbst ein; Eltern lösen
  nicht mehr für ein Kind ein. Der Link auf der Übersicht heißt „Alle Aufgaben“.
- Kalender: Die Wochenansicht zeigt 0 bis 24 Uhr und startet bei 06:00. Ein Klick
  auf einen Tag in der Monatsansicht oder im Minikalender der Übersicht öffnet die
  Tagesansicht. Die Wochenüberschrift stimmt auch über einen Monatswechsel.
- Noch nicht fertige Bereiche sind gekennzeichnet: KI-Assistent und Nachrichten
  als „Vorschau“, Benachrichtigungen als „Demnächst“. Mikrofon-Knopf und
  Verkehrsmittel-Legende sind ausgeblendet.
- Wird ein Mitglied gelöscht, verschwinden auch seine offenen Essenswünsche und
  Einkaufsvorschläge.

### Hinweise zum Update

- Neue Endpunkte: `/api/live` (Server-Sent Events), `/api/waste` und
  `/api/waste/import` (Details in der Swagger UI).
- Neue Einstellungen mit Standardwerten: `familyhub.login.max-failures` (5),
  `familyhub.login.lock-duration` (PT5M) und `familyhub.waste.task-sync-cron`
  (täglich 00:10 Uhr).
- Für die Müllabfuhr gibt es keine Beispieldaten; die Termine erscheinen erst nach
  dem Import einer ICS-Datei.
- Bestehende Gerichte haben noch keine Kochanleitung; sie lässt sich beim
  Bearbeiten des Gerichts ergänzen.
- 243 automatisierte Backend-Tests, dazu Browser-Tests je Rolle.

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
- 230 automatisierte Backend-Tests, dazu Browser-Tests je Rolle.

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
