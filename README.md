# FamilyHub AI

KI-gestützter Familienplaner für Schule, Kindergarten, Alltag, Einkauf und Haushalt.

FamilyHub AI bündelt Kalender, Aufgaben, Einkauf, Essensplanung, Nachrichten,
Wetter und Müllabfuhr in einer gemeinsamen Oberfläche für die ganze Familie.
Leitprinzip: **Der Agent informiert und bereitet vor, die Eltern entscheiden.**

**Status:** Proof of Concept, Version 0.6.0 vom 20.10.2026. Was sich in welcher
Version geändert hat, steht in [CHANGELOG.md](CHANGELOG.md).

## Funktionen

| Bereich | Was FamilyHub kann |
|---|---|
| Kalender | Monats-, Wochen- und Tagesansicht; Termine für eine oder mehrere Personen („Ganze Familie“), private Termine, Vorschläge von Jugendlichen mit Freigabe durch die Eltern |
| Google Kalender | Termine aus dem eigenen Google Kalender übernehmen (schreibgeschützt); auf Wunsch die eigenen FamilyHub-Termine in einen Google-Kalender „FamilyHub“ eintragen |
| Aufgaben und Punkte | Eltern vergeben Aufgaben mit Punkten, Kinder haken ab, nach der Bestätigung gibt es Punkte; Bonus-Aufgaben für alle Kinder; Erwachsene erledigen Aufgaben ohne Punkte |
| Belohnungen | Shop, in dem Kinder und Jugendliche Punkte einlösen; die Eltern genehmigen oder lehnen ab |
| Erfolge | Automatische Erfolge (z. B. „7 Tage hintereinander“) mit einmaligen Bonuspunkten |
| Einkaufsliste | Gemeinsame Liste mit Kategorien und „dringend“; Kinder schlagen Artikel vor, die Eltern übernehmen |
| Essensplan | Wochenplan mit Gerichte-Sammlung, Zutaten und Kochanleitung; Kinder äußern Wünsche; Zutaten per Knopf auf die Einkaufsliste |
| Nachrichten | Familiengruppe und Einzelchats; aus einer Nachricht wird ein Artikel, ein Termin oder eine Aufgabe |
| WhatsApp (in Arbeit) | Nachrichten an die WhatsApp-Nummer der Familie empfangen; jede Person sieht die von ihrer eigenen Handynummer |
| Telegram (in Arbeit) | Bot der Familie: Nachrichten an den Bot landen in FamilyHub; Benachrichtigungen zu dringenden Aufgaben, kurzfristigen Terminen, offenen Bestätigungen und Chat-Nachrichten |
| Müllabfuhr | Abfuhrkalender der Gemeinde (ICS-Datei) importieren; Termine im Kalender und eine Aufgabe für den Vorabend |
| Wetter | Wetter am Wohnort mit Kleidungsempfehlung (OpenWeather) |
| Rollen und Rechte | Administrator, Jugendliche, Kind, Gast und KI-Agent, dazu Einzelrechte je Person |
| Sonstiges | Änderungen anderer erscheinen ohne Neuladen; als App installierbar; Konto-Sperre nach fünf falschen Passwörtern |

Noch nicht umgesetzt sind der KI-Assistent, die Glocke mit Benachrichtigungen
in der App (Benachrichtigungen kommen bisher nur über Telegram), die
Sprachsteuerung sowie Fahrzeiten und Terminkonflikte. Die Oberfläche
kennzeichnet sie als „Vorschau“ oder „Demnächst“ oder blendet sie aus
(`frontend/src/placeholders.ts`).

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (Sitzungs-Cookie, CSRF), Maven Wrapper |
| Datenbank | MongoDB 8.0 (lokal) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4 (ursprünglich aus Figma Make) |
| Tests | JUnit 5, MockMvc, eingebettete MongoDB (Flapdoodle) |

## Schnellstart

**Voraussetzungen:** JDK 25, MongoDB 8.0 (oder Docker) und für die Oberfläche
Node.js 24 mit pnpm 10. Maven muss nicht installiert sein, der Maven Wrapper lädt
es beim ersten Aufruf selbst. Unter Windows lässt sich MongoDB ohne
Admin-Rechte als ZIP nach `%LOCALAPPDATA%\Programs\mongodb` entpacken.

```bash
# 1. MongoDB starten (lauscht nur auf 127.0.0.1:27017, beenden mit Strg+C)
powershell -ExecutionPolicy Bypass -File scripts/start-mongodb.ps1
#    oder mit Docker (läuft im Hintergrund, beenden mit: docker compose down)
docker compose up -d

# 2. Backend starten (Windows-Eingabeaufforderung: mvnw.cmd statt ./mvnw)
./mvnw spring-boot:run
# Prüfen: http://localhost:8080/api/health
# API im Browser ausprobieren: http://localhost:8080/swagger-ui.html

# 3. Oberfläche starten (eigenes Terminal)
cd frontend
pnpm install
pnpm dev
# Öffnen: http://localhost:5173

# 4. Backend-Tests ausführen (braucht keine laufende MongoDB)
./mvnw test
```

Backend und Oberfläche beendet man jeweils mit Strg+C im eigenen Terminal.

Beim ersten Start mit leerer Datenbank legt das Backend eine Beispielfamilie an.
Der erste Testlauf lädt einmalig eine Test-MongoDB herunter (ca. 840 MB, einige
Minuten).

**Anmeldung:** Die API ist nur nach Login nutzbar. Beispielkonten (nur für die
Entwicklung, alle mit dem Passwort `familyhub`):

| Benutzername | Rolle |
|---|---|
| `sarah`, `mike` | Administrator |
| `emma` | Jugendliche (als Kind hinterlegt, ab 13 automatisch Jugendliche) |
| `lucas`, `lily` | Kind |
| `oma` | Gast |

Ohne Beispieldaten (`familyhub.sample-data.enabled=false`) legt man beim ersten
Start in der Oberfläche („Familie einrichten“) oder über `POST /api/auth/setup`
den ersten Administrator an.

## Einstellungen

Die Einstellungen stehen in `src/main/resources/application.properties`.
Zugangsdaten für Google, OpenWeather, WhatsApp, Telegram und Groq gehören **nie** dorthin,
sondern in eine Datei `local.properties` im Projektordner. Sie steht in
`.gitignore` und wird beim Start automatisch gelesen.

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `familyhub.sample-data.enabled` | `true` | Beispielfamilie bei leerer Datenbank anlegen |
| `familyhub.roles.teen-age` | `13` | Ab diesem Alter wird ein Kind automatisch Jugendlicher |
| `familyhub.login.max-failures` / `lock-duration` | `5` / `PT5M` | So viele Fehlversuche sperren ein Konto so lange |
| `server.servlet.session.timeout` | `8h` | Dauer einer Anmeldung |
| `familyhub.waste.task-sync-cron` | `0 50 7 * * *` | Täglich um 07:50 Uhr Müll-Aufgaben für die nächsten sieben Tage anlegen |
| `familyhub.google.sync-interval` | `PT15M` | Takt des Abgleichs mit Google |
| `familyhub.google.push-delay` | `PT10S` | So schnell kommen geänderte Termine in den Google-Kalender „FamilyHub“ |
| `familyhub.weather.cache-duration` | `PT10M` | So lange merkt sich das Backend eine Wetterabfrage |
| `familyhub.telegram.polling` | `true` | Nachrichten an den Telegram-Bot selbst abholen |
| `familyhub.notifications.due-reminder-cron` | `0 0 7 * * *` | Täglich um 07:00 Uhr an dringende Aufgaben erinnern, die heute fällig sind |
| `familyhub.notifications.short-notice` | `PT24H` | Termine, die so bald beginnen, gelten als kurzfristig |
| `familyhub.notifications.pending-after` | `PT24H` | So lange darf eine Bestätigung offen sein, bevor FamilyHub erinnert |
| `familyhub.assistant.model` | `openai/gpt-oss-120b` | Sprachmodell bei Groq für den KI-Assistenten; muss Werkzeuge aufrufen können |
| `familyhub.assistant.base-url` | `https://api.groq.com/openai/v1` | Adresse des Anbieters (OpenAI-kompatibel) |
| `familyhub.assistant.timeout` | `PT2M` | So lange darf eine Antwort des Sprachmodells dauern |
| `familyhub.cors.allowed-origins` | `http://localhost:5173` | Adresse der Oberfläche |

## Google Kalender einrichten (optional)

Jedes Familienmitglied außer Gästen kann unter „Familie“ seinen Google Kalender
verbinden. Das geht in zwei Richtungen:

- **Termine aus Google übernehmen:** FamilyHub liest die gewählten Google-Kalender
  (ein Jahr zurück und ein Jahr voraus) und zeigt die Termine schreibgeschützt
  mit einem „G“ an; geändert wird weiter in Google. Abgeglichen wird beim
  Verbinden, per Knopf und alle 15 Minuten.
- **Termine nach Google eintragen (wahlweise):** Mit „Meine FamilyHub-Termine in
  Google eintragen“ legt FamilyHub im Google-Konto einen eigenen Kalender
  „FamilyHub“ an. Dort stehen alle freigegebenen Termine, an denen die Person
  beteiligt ist, auch gemeinsame; private sind in Google als privat markiert,
  offene Vorschläge fehlen. Änderungen in FamilyHub erscheinen nach wenigen
  Sekunden in Google. FamilyHub bleibt führend: Wer einen dieser Termine in
  Google ändert, findet beim nächsten Abgleich wieder den Stand aus FamilyHub.
  Die übrigen Google-Kalender bleiben unberührt.

Beim Trennen entfernt FamilyHub die übernommenen Termine und löscht den Kalender
„FamilyHub“ in Google. Das Recht zum Eintragen fragt Google erst ab, wenn jemand
das Eintragen einschaltet.

Dafür braucht das Backend eigene Zugangsdaten von Google:

1. In der [Google Cloud Console](https://console.cloud.google.com) ein Projekt
   anlegen und die **Google Calendar API** aktivieren
   (`calendar-json.googleapis.com`, nicht die ähnlich benannte „CalDAV API“).
2. Unter **Google Auth Platform** (früher „OAuth-Zustimmungsbildschirm“):
   - **Zielgruppe:** Typ *Extern*; alle Google-Konten, die testen sollen, als
     **Testnutzer** eintragen.
   - **Datenzugriff:** die Bereiche `.../auth/calendar.readonly` und, für das
     Eintragen, `.../auth/calendar.app.created` hinzufügen.
     `calendar.app.created` erlaubt nur Kalender, die FamilyHub selbst anlegt.
   - **Clients:** einen OAuth-Client vom Typ „Webanwendung“ anlegen mit der
     Weiterleitungs-URI `http://localhost:8080/api/google/callback`.
3. In `local.properties` eintragen:

   ```properties
   familyhub.google.client-id=<Client-ID aus Schritt 2>
   familyhub.google.client-secret=<Clientschlüssel aus Schritt 2>
   # Beliebige lange Zufallszeichenkette; damit werden die Google-Zugänge in
   # der Datenbank verschlüsselt. Wird sie geändert, muss man neu verbinden.
   familyhub.google.token-key=<Zufallszeichenkette>
   ```

   Eine Zufallszeichenkette erzeugt z. B. PowerShell:
   `$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`
4. Backend neu starten.

Wer Google schon vor Version 0.6.0 für das Übernehmen eingerichtet hat, ergänzt
nur den Bereich `calendar.app.created` unter „Datenzugriff“.

Solange die Google-App im Status „Testing“ ist, laufen die Zugänge nach
7 Tagen ab; FamilyHub zeigt dann „Bitte neu verbinden“ an.

## Wetter einrichten (optional)

Die Übersicht zeigt das Wetter am Wohnort der Familie (aktuell, heute, die
nächsten vier Tage und eine Kleidungsempfehlung). Die Daten kommen von
[OpenWeather](https://openweathermap.org); ein kostenloser Zugang genügt.

1. Bei OpenWeather unter „My API keys“ einen Schlüssel anlegen. Neue Schlüssel
   werden erst nach bis zu zwei Stunden aktiv.
2. In `local.properties` eintragen:

   ```properties
   familyhub.weather.api-key=<API-Schlüssel>
   ```
3. Backend neu starten und als Administrator unter „Familie“ → „Wohnort für das
   Wetter“ den Ort suchen und auswählen.

## Müllabfuhr einrichten (optional)

Als Administrator unter „Familie“ den Abfuhrkalender der Gemeinde als ICS-Datei
importieren und festlegen, wer die Tonne rausbringt (eine feste Person oder
offen für Kinder und Jugendliche) und wie viele Punkte es dafür gibt. Die
Abholtermine erscheinen im Kalender. Bis zu sieben Tage vor jeder Abholung legt
FamilyHub die Aufgabe „… rausbringen“ für den Vorabend an. Für die Müllabfuhr
gibt es keine Beispieldaten.

## WhatsApp einrichten (optional)

FamilyHub nimmt Nachrichten an die WhatsApp-Nummer der Familie über die
offizielle **WhatsApp Cloud API** von Meta entgegen und speichert sie. Ausgewertet
oder beantwortet werden sie noch nicht; das übernimmt später der KI-Assistent.

**Wer was sieht:** Jede Person sieht die Nachrichten von ihrer eigenen
Handynummer, Administratoren sehen alle, Gäste keine. Die Handynummern tragen
Administratoren unter „Familie“ beim Familienmitglied ein (mit Ländervorwahl;
ohne gilt Deutschland). Abrufen lassen sich die Nachrichten über den gemeinsamen
Eingang `GET /api/inbox?channel=whatsapp`, die Anzahl über `GET /api/whatsapp/status`.

Der Webhook `/api/whatsapp` ist öffentlich, weil Meta ihn ohne Anmeldung
aufruft. Er nimmt nur Aufrufe an, die Meta mit dem App-Secret signiert hat
(Header `X-Hub-Signature-256`); alle anderen lehnt er ab.

1. Unter [developers.facebook.com](https://developers.facebook.com) eine App
   anlegen und das Produkt **WhatsApp** hinzufügen. Dort brauchst du vier Werte:
   - **Zugriffstoken** (`WHATSAPP_ACCESS_TOKEN`): unter „WhatsApp → API Setup“.
     Der Token dort läuft nach 24 Stunden ab; dauerhaft geht es mit dem Token
     eines System-Users.
   - **Phone Number ID** (`WHATSAPP_PHONE_NUMBER_ID`): ebenfalls unter „API Setup“.
   - **App-Secret** (`WHATSAPP_APP_SECRET`): unter „App-Einstellungen → Allgemein“
     („App-Geheimcode“).
   - **Verify-Token** (`WHATSAPP_VERIFY_TOKEN`): einen beliebigen Wert selbst
     wählen, z. B. eine lange Zufallszeichenkette.
2. Die Werte als Umgebungsvariablen setzen oder in `local.properties` eintragen
   (`local.properties` gewinnt):

   ```properties
   familyhub.whatsapp.access-token=<Zugriffstoken>
   familyhub.whatsapp.phone-number-id=<Phone Number ID>
   familyhub.whatsapp.app-secret=<App-Secret>
   familyhub.whatsapp.verify-token=<Verify-Token>
   ```
3. Backend neu starten. Der Webhook muss über HTTPS von außen erreichbar sein;
   lokal geht das mit einem Tunnel wie ngrok, z. B.
   `https://<tunnel>.ngrok.io/api/whatsapp`.
4. In der Meta Developer Platform unter „WhatsApp → Configuration“ die
   **Callback URL** (die Tunnel-Adresse mit `/api/whatsapp`) und denselben
   **Verify-Token** eintragen und das Webhook-Feld `messages` abonnieren. Meta
   prüft die Adresse sofort; FamilyHub antwortet nur, wenn der Token stimmt.
5. Als Administrator unter „Familie“ bei jedem Familienmitglied die Handynummer
   eintragen, damit es seine Nachrichten sieht.

## Telegram einrichten (optional)

Die Familie bekommt einen eigenen Telegram-Bot. Wer sein Telegram-Konto unter
„Familie“ verbindet, kann dem Bot schreiben; die Nachrichten landen wie die von
WhatsApp im gemeinsamen Eingang (`GET /api/inbox?channel=telegram`). Umgekehrt
schickt FamilyHub ihm Benachrichtigungen (siehe unten). Ausgewertet oder
beantwortet werden eingehende Nachrichten noch nicht, auch keine Antworten auf
Benachrichtigungen; die landen ebenfalls im Eingang.

FamilyHub holt die Nachrichten selbst bei Telegram ab (Long Polling). Anders als
bei WhatsApp braucht es deshalb keine öffentliche Adresse und keinen Tunnel, nur
ausgehende Verbindungen zu `api.telegram.org`. Nachrichten von Konten, die mit
niemandem in der Familie verbunden sind, verwirft FamilyHub, ohne sie zu
speichern. **Wer was sieht**, regelt der Eingang wie bei WhatsApp: jede Person
ihre eigenen, Administratoren alle, Gäste keine; Gäste können Telegram auch
nicht verbinden.

1. In Telegram den Chat mit **@BotFather** öffnen, `/newbot` senden und einen
   Namen sowie einen Benutzernamen wählen (endet auf `bot`). BotFather antwortet
   mit dem **Bot-Token**.
2. Den Token als Umgebungsvariable `TELEGRAM_BOT_TOKEN` setzen oder in
   `local.properties` eintragen:

   ```properties
   familyhub.telegram.bot-token=<Bot-Token>
   ```
3. Backend neu starten. Im Log steht dann „Telegram: Nachrichten an den Bot
   werden abgeholt“.
4. Jede Person öffnet unter „Familie“ die Karte **Telegram**, klickt auf „Mit
   Telegram verbinden“ und „In Telegram öffnen“ und tippt dort auf **Starten**.
   Der Bot bestätigt die Verbindung; der Link gilt zehn Minuten.

Ein Bot darf nur von einem FamilyHub gleichzeitig abgeholt werden. Laufen zwei,
etwa auf zwei Rechnern, meldet Telegram einen Konflikt; dann für jeden Rechner
einen eigenen Bot anlegen. Chats mit Bots sind bei Telegram nicht
Ende-zu-Ende-verschlüsselt.

### Benachrichtigungen

Wer Telegram verbunden hat, bekommt dort Nachrichten von FamilyHub. Was man
selbst auslöst, meldet FamilyHub einem nicht.

| Art | Wann | An wen |
|---|---|---|
| Dringende Aufgaben | Eine Aufgabe mit Priorität „hoch“ wird jemandem zugewiesen oder auf „hoch“ gestellt; außerdem um 07:00 Uhr am Fälligkeitstag, solange sie offen ist (auch für die Müll-Aufgaben) | Die zuständige Person |
| Kurzfristige Termine | Ein Termin, der in den nächsten 24 Stunden beginnt, wird angelegt, freigegeben oder verschoben (auch aus diesem Zeitraum heraus) | Alle Beteiligten; wer neu dazukommt, allein |
| Offene Bestätigungen | Eine erledigte Aufgabe, ein Termin-, Einkaufs- oder Essensvorschlag oder eine eingelöste Belohnung wartet seit 24 Stunden; einmal als Sammelnachricht | Alle, die bestätigen dürfen |
| Chat-Nachrichten | Jede neue Nachricht in der Familiengruppe oder im Einzelchat | Alle, die den Chat lesen dürfen |

Jede Person schaltet einzelne Arten in der Karte **Telegram** unter
„Benachrichtigungen“ ab. Termine aus Google melden die Termin-Benachrichtigungen
nicht, Terminvorschläge erst mit der Freigabe. Die Wartezeit für Bestätigungen
zählt ab dem Moment, in dem FamilyHub den offenen Punkt bemerkt (alle
15 Minuten); was beim ersten Start schon offen ist, meldet FamilyHub also erst
einen Tag später. Ist Telegram nicht erreichbar, steht das im Log; nachgeholt
wird die Nachricht nicht.

## KI-Assistent einrichten (optional, in Arbeit)

Der KI-Assistent entsteht in mehreren Schritten. Bisher steht nur die Verbindung
zum Sprachmodell; Fragen stellen kann man noch nicht, und die KI bekommt noch
keine Familiendaten. Als Anbieter nutzt FamilyHub **Groq** über dessen
OpenAI-kompatible Schnittstelle. Der Assistent kennt im Code nur eine eigene
Schnittstelle (`LanguageModel`); ein anderer Anbieter wäre ein weiterer Adapter.

Leitlinien für alle weiteren Schritte: Der Assistent liest nur, was die fragende
Person selbst sehen darf, und ändert nie selbst etwas. Er schlägt Entwürfe vor
(Termin, Einkaufsartikel, Essen, Aufgabe ohne Person und Punkte), die ein Mensch
übernimmt. Senden, Punkte vergeben, Aufgaben zuweisen und Löschen kann er nicht.
Benutzen dürfen ihn alle außer Gästen, einrichten und testen nur
Administratoren (Recht „Sprachassistent“).

1. Unter [console.groq.com](https://console.groq.com) ein Konto anlegen und
   unter „API Keys“ einen Schlüssel erzeugen.
2. Unter „Data Controls“ **Zero Data Retention** einschalten, dann speichert Groq
   keine Anfragen. Gespeicherte Daten liegen laut Groq in den USA; eine
   Verarbeitung nur in der EU gibt es dort bisher nur für Enterprise-Kunden.
3. Den Schlüssel als Umgebungsvariable `GROQ_API_KEY` setzen oder in
   `local.properties` eintragen:

   ```properties
   familyhub.assistant.api-key=<API-Schlüssel>
   # optional, Standard: openai/gpt-oss-120b (Liste: https://console.groq.com/docs/models)
   familyhub.assistant.model=<Modell>
   ```
4. Backend neu starten, in der Swagger UI als Administrator anmelden und
   `POST /api/assistant/test` ausführen. Der Test stellt eine kurze Frage mit
   einem Test-Werkzeug, das nur das heutige Datum liefert. Steht in der Antwort
   `"toolCalling": true`, kann das Modell Werkzeuge aufrufen und ist bereit.

Der Rechner muss `api.groq.com` erreichen können; in Firmennetzen ist das nicht
selbstverständlich. Jeder Aufruf des Sprachmodells wird ohne Inhalte
protokolliert (Zeit, Person, Modell, Tokens, Dauer, Fehler). MongoDB löscht die
Einträge nach 30 Tagen von selbst, beim Löschen eines Mitglieds sofort.

## Als App installieren

FamilyHub lässt sich wie eine App installieren (eigenes Fenster, Symbol im
Startmenü bzw. auf dem Startbildschirm). In Edge oder Chrome
`http://localhost:5173` öffnen und in der Adressleiste auf „App installieren“
klicken (Edge: Menü „Apps“ → „Diese Website als App installieren“).

Auf dem Handy funktioniert das erst, wenn FamilyHub dort über HTTPS erreichbar
ist, also auf einem Server läuft; im lokalen Entwicklungsbetrieb ist die
Oberfläche nur auf dem eigenen Rechner erreichbar.

## Projektstruktur

```
├── src/main/java/de/familyhub/   Backend (Spring Boot), ein Paket je Bereich
├── src/test/java/de/familyhub/   Backend-Tests, gleiche Pakete
├── frontend/                     Oberfläche (React, siehe frontend/README.md)
├── scripts/                      Hilfsskripte (z. B. MongoDB starten)
└── docker-compose.yml            MongoDB alternativ per Docker
```

Die Backend-Pakete heißen wie die Bereiche: `calendar`, `google`, `task`,
`points`, `rewards`, `achievements`, `shopping`, `meals`, `messages`, `waste`,
`weather`, `whatsapp`, `telegram`, `family` und `settings`. Querschnittsthemen
liegen in `security` (Anmeldung), `permission` (Rollen und Rechte), `inbox`
(gemeinsamer Eingang für Nachrichten von WhatsApp und Telegram), `notifications`
(Benachrichtigungen über Telegram), `assistant` (KI-Assistent; das Sprachmodell
steckt hinter der Schnittstelle in `assistant.llm`, der Groq-Adapter in
`assistant.groq`), `live` (Live-Aktualisierung), `web`
(Fehlerbehandlung, CORS, Swagger) und `sampledata` (Beispielfamilie).

## Dokumentation

Die REST-Schnittstelle ist bei laufendem Backend in der Swagger UI beschrieben
und lässt sich dort ausprobieren: **http://localhost:8080/swagger-ui.html**
(zuerst unter „Anmeldung“ einloggen).

Konzepte, Diagramme, User Stories, Personas und das Figma-UI liegen im
Team-Ordner, nicht im Repository.

## Zusammenarbeit

- **Branches:**
  - `main` enthält nur geprüfte, lauffähige Stände; jede Version hat einen Tag
    (`v0.1.0`, …). Nie direkt auf `main` committen.
  - `develop` ist die Basis für die Weiterentwicklung. Neue Features auf
    `feature/...`, Fehlerbehebungen auf `fix/...` von `develop` abzweigen und
    wieder nach `develop` mergen („… nach develop uebernehmen“).
  - Größere Features entstehen in zwei Schritten: erst das Backend mit Tests
    („…, Phase 1“), dann die Oberfläche mit der Doku („…, Phase 2“).
- **Release:** Von `develop` einen Branch `release/vX.Y.Z` abzweigen und dort
  die Patchnotes in `CHANGELOG.md` schreiben. Dann in `main` mergen („Version
  X.Y.Z: …“), den Merge mit dem Tag `vX.Y.Z` versehen und den Release-Branch
  zurück nach `develop` mergen („Release X.Y.Z nach develop uebernehmen“).
- **Commit-Nachrichten auf Deutsch**, erste Zeile als kurze Zusammenfassung im
  Imperativ, z. B. „Termin-API um Zeitraumfilter ergänzen“. Bei Bedarf darunter
  eine Leerzeile und das Warum.
- **Zugangsdaten** stehen nur in `local.properties` und werden nie eingecheckt.
- **IDE-Dateien** (`.idea/`, `*.iml`, `.vscode/`) werden nicht eingecheckt.
  IntelliJ importiert das Projekt über die `pom.xml`.
- Im Repository liegt nur, was das Programm braucht. Konzepte und Diagramme
  gehören in den Team-Ordner.
