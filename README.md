# FamilyHub AI

KI-gestützter Familienplaner für Schule, Kindergarten, Alltag, Einkauf und Haushalt.

FamilyHub AI bündelt Kalender, Aufgaben, Einkauf, Essensplanung, Nachrichten,
Wetter und Müllabfuhr in einer gemeinsamen Oberfläche für die ganze Familie.
Leitprinzip: **Der Agent informiert und bereitet vor, die Eltern entscheiden.**

**Status:** Proof of Concept, Version 0.7.0 vom 09.11.2026. Was sich in welcher
Version geändert hat, steht in [CHANGELOG.md](CHANGELOG.md).

## Funktionen

| Bereich | Was FamilyHub kann |
|---|---|
| Kalender | Monats-, Wochen- und Tagesansicht; Termine für eine oder mehrere Personen („Ganze Familie“), private Termine, Serientermine (z. B. jeden Dienstag Training), Vorschläge von Jugendlichen mit Freigabe durch die Eltern |
| Google Kalender | Termine aus dem eigenen Google Kalender übernehmen (schreibgeschützt); auf Wunsch die eigenen FamilyHub-Termine in einen Google-Kalender „FamilyHub“ eintragen |
| Aufgaben und Punkte | Eltern vergeben Aufgaben mit Punkten, Kinder haken ab, nach der Bestätigung gibt es Punkte; Bonus-Aufgaben für alle Kinder; Aufgaben, die sich nach Erledigung wiederholen (z. B. Blumen gießen alle 3 Tage); Erwachsene erledigen Aufgaben ohne Punkte |
| Belohnungen | Shop, in dem Kinder und Jugendliche Punkte einlösen; die Eltern genehmigen oder lehnen ab |
| Erfolge | Automatische Erfolge (z. B. „7 Tage hintereinander“) mit einmaligen Bonuspunkten |
| Einkaufsliste | Gemeinsame Liste mit Kategorien und „dringend“; Kinder schlagen Artikel vor, die Eltern übernehmen; wiederkehrende Artikel (bei jedem Einkauf oder einige Tage nach dem Kauf) |
| Essensplan | Wochenplan mit Gerichte-Sammlung, Zutaten und Kochanleitung; Kinder äußern Wünsche; Zutaten per Knopf auf die Einkaufsliste |
| Nachrichten | Familiengruppe und Einzelchats; aus einer Nachricht wird ein Artikel, ein Termin oder eine Aufgabe |
| WhatsApp (in Arbeit) | Nachrichten an die WhatsApp-Nummer der Familie empfangen; jede Person sieht die von ihrer eigenen Handynummer |
| Telegram (in Arbeit) | Bot der Familie: Nachrichten an den Bot landen in FamilyHub; Benachrichtigungen zu dringenden Aufgaben, kurzfristigen Terminen, offenen Bestätigungen und Chat-Nachrichten |
| Müllabfuhr | Abfuhrkalender der Gemeinde (ICS-Datei) importieren; Termine im Kalender und eine Aufgabe für den Vorabend |
| Fahrzeit | Ort eines Termins aus der Ortssuche, Fahrzeit vom Wohnort mit Auto, Fahrrad oder zu Fuß und Abfahrtszeit (OpenRouteService) |
| Wetter | Wetter am Wohnort mit Kleidungsempfehlung (OpenWeather) |
| Rollen und Rechte | Administrator, Jugendliche, Kind, Gast und KI-Agent, dazu Einzelrechte je Person |
| Sonstiges | Änderungen anderer erscheinen ohne Neuladen; als App installierbar; Konto-Sperre nach fünf falschen Passwörtern |

Noch nicht umgesetzt sind der KI-Assistent, die Glocke mit Benachrichtigungen
in der App (Benachrichtigungen kommen bisher nur über Telegram), die
Sprachsteuerung sowie Warnungen, wenn sich Fahrzeit und andere Termine
überschneiden. Die Oberfläche
kennzeichnet sie als „Vorschau“ oder „Demnächst“ oder blendet sie aus
(`frontend/src/placeholders.ts`).

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (Sitzungs-Cookie, CSRF), Maven Wrapper |
| Datenbank | MongoDB 8.0 (lokal) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4 (ursprünglich aus Figma Make) |
| Tests | JUnit 5, MockMvc, eingebettete MongoDB (Flapdoodle) |

## FamilyHub starten

Es gibt zwei Wege:

- **Nur benutzen:** mit Docker, ein Befehl, außer Docker ist nichts zu
  installieren. Siehe [Mit Docker betreiben](#mit-docker-betreiben).
- **Entwickeln oder ohne Docker:** wie hier beschrieben. FamilyHub besteht dann
  aus drei Teilen, die gleichzeitig laufen, jeder in einem **eigenen
  Terminal-Fenster**:

```
 Browser  →  Oberfläche (Port 5173)  →  Backend (Port 8080)  →  Datenbank MongoDB (Port 27017)
```

Die Anleitung unten gilt für Windows, macOS und Linux. Wo sich die Befehle
unterscheiden, steht es dabei. Mit „Terminal“ ist unter Windows die
**PowerShell** gemeint, unter macOS die App **Terminal**.

### Schritt 1: Programme einmal installieren

| Programm | Wofür | Windows | macOS | Linux |
|---|---|---|---|---|
| **JDK 25** (Java) | Backend | [Temurin 25](https://adoptium.net/temurin/releases/?version=25), Installer (.msi) | [Temurin 25](https://adoptium.net/temurin/releases/?version=25) (.pkg) oder `brew install --cask temurin@25` | Paket `temurin-25-jdk` oder Archiv von Adoptium |
| **Node.js 24** | Oberfläche | [nodejs.org](https://nodejs.org), Installer | [nodejs.org](https://nodejs.org) oder `brew install node@24` | Paketquelle von nodejs.org |
| **pnpm 10** | Oberfläche | nach Node: `npm install -g pnpm@10` | wie Windows | wie Windows |
| **MongoDB 8.0** | Datenbank | [ZIP herunterladen](https://www.mongodb.com/try/download/community) und nach `%LOCALAPPDATA%\Programs\mongodb` entpacken (keine Admin-Rechte nötig) | `brew tap mongodb/brew && brew install mongodb-community@8.0` | Paket `mongodb-org` (Anleitung auf mongodb.com) |
| **Git** | Projekt holen | [git-scm.com](https://git-scm.com) | ist meist schon da (`git --version`) | Paket `git` |

Maven muss nicht installiert werden, das Projekt bringt es mit (Maven Wrapper).

Ohne Homebrew unter macOS: Die Programme lassen sich auch als Archiv in einen
eigenen Ordner entpacken, z. B. `~/.local/opt/jdk`, `~/.local/opt/node` und
`~/.local/opt/mongodb`. Dann diese Zeilen ans Ende von `~/.zshrc` schreiben und
das Terminal neu öffnen:

```bash
export JAVA_HOME="$HOME/.local/opt/jdk"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/node/bin:$HOME/.local/opt/mongodb/bin:$PATH"
```

**Prüfen** (in einem neuen Terminal), ob alles gefunden wird:

```bash
java -version      # muss 25 zeigen
node -v            # muss v24 zeigen
pnpm -v            # muss 10 zeigen
mongod --version   # muss 8.0 zeigen (unter Windows mit ZIP: nicht nötig, das Startskript findet es)
```

### Schritt 2: Projekt holen

```bash
git clone https://github.com/JNeugaertner/Family-Hub.git
cd Family-Hub
```

Unter Windows den Ordner nicht in OneDrive legen (z. B. nicht auf den Desktop,
wenn der synchronisiert wird), sondern etwa nach `C:\Users\<Name>\Projekte`.

### Schritt 3: Die drei Teile starten

Jedes Mal, wenn du FamilyHub benutzen willst. Alle Befehle im Projektordner
`Family-Hub`, jeweils in einem eigenen Terminal-Fenster:

**Terminal 1, Datenbank:**

```bash
# Windows (PowerShell)
powershell -ExecutionPolicy Bypass -File scripts\start-mongodb.ps1
# macOS und Linux
scripts/start-mongodb.sh
```

Fertig, wenn viele Zeilen durchlaufen und das Fenster offen bleibt. Die Daten
bleiben zwischen den Starts erhalten.

**Terminal 2, Backend:**

```bash
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run
# macOS und Linux
./mvnw spring-boot:run
```

Fertig, wenn `Started Main` erscheint. Beim allerersten Mal lädt Maven einige
Minuten lang alles Nötige herunter. Zur Kontrolle im Browser
http://localhost:8080/api/health öffnen: Dort muss `"status":"UP"` stehen.

**Terminal 3, Oberfläche:**

```bash
cd frontend
pnpm install      # nur beim ersten Mal und nach Updates nötig
pnpm dev
```

Fertig, wenn `Local: http://localhost:5173/` erscheint.

**Öffnen:** http://localhost:5173 im Browser. Beim ersten Start legt FamilyHub
eine Beispielfamilie an; anmelden mit einem dieser Konten, alle mit dem
Passwort `familyhub` (nur zum Ausprobieren):

| Benutzername | Rolle |
|---|---|
| `sarah`, `mike` | Administrator |
| `emma` | Jugendliche (als Kind hinterlegt, ab 13 automatisch Jugendliche) |
| `lucas`, `lily` | Kind |
| `oma` | Gast |

**Beenden:** in jedem der drei Fenster **Strg+C** drücken (auch unter macOS
Strg, nicht Cmd). Am besten in umgekehrter Reihenfolge: Oberfläche, Backend,
Datenbank.

### Wenn etwas nicht klappt

| Meldung | Ursache und Lösung |
|---|---|
| `java`, `node`, `pnpm` oder `mongod` „nicht gefunden“ / „command not found“, unter macOS „Unable to locate a Java Runtime“ | Programm fehlt oder steht nicht im Suchpfad. Installation prüfen; ohne Installer die `PATH`-Zeilen von oben eintragen und das Terminal **neu öffnen**. |
| `permission denied: ./mvnw` (macOS, Linux) | Einmal `chmod +x mvnw scripts/*.sh` ausführen. |
| PowerShell: „Ausführung von Skripts ist auf diesem System deaktiviert“ | Den Befehl genau wie oben mit `powershell -ExecutionPolicy Bypass -File …` aufrufen. |
| Datenbank: `address already in use` (Port 27017) | Es läuft schon eine MongoDB, z. B. aus einem früheren Start. Dann ist Schritt „Terminal 1“ schon erledigt. |
| Backend: `Port 8080 was already in use` | FamilyHub läuft schon in einem anderen Fenster (oder ein anderes Programm nutzt den Port). Das andere Fenster suchen und mit Strg+C beenden. |
| Backend: Fehler mit `MongoSocketOpenException` / `Connection refused` | Die Datenbank läuft nicht: Terminal 1 starten. |
| Oberfläche zeigt „Failed to fetch“, „HTTP 500“ oder lädt nichts | Das Backend läuft nicht oder ist noch nicht fertig: Terminal 2 prüfen (`Started Main`). |
| Anmeldung schlägt mit „HTTP 403“ fehl | Die Oberfläche über **http://localhost:5173** öffnen, nicht über `127.0.0.1` oder eine andere Adresse. |
| „Zu viele falsche Passwörter für dieses Konto“ | Fünfmal falsches Passwort: so lange warten wie angegeben (höchstens fünf Minuten). |

### Für Entwickler

```bash
./mvnw test        # Backend-Tests (Windows: .\mvnw.cmd test), braucht keine laufende MongoDB
```

Der erste Testlauf lädt einmalig eine Test-MongoDB herunter (ca. 840 MB, einige
Minuten). Die REST-Schnittstelle lässt sich bei laufendem Backend unter
http://localhost:8080/swagger-ui.html ausprobieren. Statt `scripts/start-mongodb…`
geht die Datenbank auch per Docker: `docker compose -f compose.dev-mongodb.yaml up -d`
im Projektordner (beenden mit `docker compose -f compose.dev-mongodb.yaml down`).

Ohne Beispieldaten (`familyhub.sample-data.enabled=false`) legt man beim ersten
Start in der Oberfläche („Familie einrichten“) oder über `POST /api/auth/setup`
den ersten Administrator an. Zugangsdaten für Google, Wetter, Fahrzeit, Telegram
und WhatsApp kommen in eine Datei `local.properties` (siehe „Einstellungen“ und
die Abschnitte „… einrichten“ weiter unten); ohne sie läuft FamilyHub, die
jeweilige Funktion meldet dann „nicht eingerichtet“.

## Mit Docker betreiben

Für alle, die FamilyHub nur benutzen wollen: Oberfläche, Backend und MongoDB
laufen zusammen in Docker, ohne JDK, Node oder pnpm. Die Oberfläche kommt dabei
vom Backend selbst, FamilyHub läuft also unter **http://localhost:8080**.

**Voraussetzung:** [Docker Desktop](https://www.docker.com/products/docker-desktop/)
(Windows braucht dafür WSL 2: einmal `wsl --install` in einer
Administrator-Eingabeaufforderung, danach neu starten) oder unter Linux Docker
Engine mit Compose.
Docker Desktop muss laufen (Wal-Symbol in der Menüleiste bzw. Taskleiste).
Findet das Terminal unter macOS `docker` nicht („command not found“), liegt es
in `~/.docker/bin`: einmal
`echo 'export PATH="$HOME/.docker/bin:$PATH"' >> ~/.zshrc` und das Terminal neu
öffnen. Prüfen mit `docker version` (zeigt Client und Server).

**Am einfachsten: per Doppelklick.** Im Ordner `deploy` liegen Startdateien,
die alles Nötige selbst erledigen:

| System | Starten | Beenden |
|---|---|---|
| Windows | `FamilyHub starten.bat` | `FamilyHub beenden.bat` |
| macOS | `FamilyHub starten.command` | `FamilyHub beenden.command` |
| Linux (oder im Terminal) | `./familyhub.sh start` | `./familyhub.sh stop` |

Beim Start prüfen sie, ob Docker bzw. Podman läuft (Docker Desktop starten sie
bei Bedarf selbst). Beim ersten Mal legen sie `deploy/.env` mit einem
zufälligen Datenbank-Passwort an und bauen das Image. Danach starten sie
FamilyHub, warten, bis es bereit ist, und öffnen den Browser. Das Fenster
zeigt, was passiert, und bleibt bei Fehlern mit einer verständlichen Meldung
offen. Weitere Befehle: `./familyhub.sh update` baut bzw. lädt die neueste
Version, `./familyhub.sh logs` zeigt das Protokoll (unter Windows
`familyhub.ps1 update` bzw. `logs` in PowerShell).

macOS fragt beim ersten Doppelklick eventuell nach, ob die Datei geöffnet
werden darf: dann mit Rechtsklick › „Öffnen“ bestätigen. Die `.env` danach
gut aufheben, sie gehört zur Sicherung: Ohne das Passwort darin kommt
FamilyHub nicht mehr an die Daten. Deshalb legen die Startdateien keine neue
`.env` an, solange es schon Daten gibt.

**Von Hand** geht es so:

1. Im Projektordner das Image bauen. **Der Punkt am Ende gehört zum Befehl**
   (er steht für „dieser Ordner“); fehlt er, meldet Docker „requires 1
   argument“. Mit Podman `podman build -t familyhub:lokal .`:

   ```bash
   docker build -t familyhub:lokal .
   ```

   Sobald eine Version mit Tag veröffentlicht ist, entfällt das: GitHub baut
   das Image dann selbst (`ghcr.io/jneugaertner/family-hub`), und vom
   Repository braucht man nur die beiden Dateien aus `deploy/`.
2. Im Ordner `deploy` die Vorlage kopieren:

   ```bash
   cd deploy
   cp .env.example .env
   ```

   In `.env` mindestens `MONGO_PASSWORD` setzen und für das selbst gebaute
   Image die Zeile `FAMILYHUB_IMAGE=familyhub:lokal` einkommentieren (mit
   Podman: `FAMILYHUB_IMAGE=localhost/familyhub:lokal`). Unter Windows statt
   `cp` den Befehl `copy` verwenden.
3. Starten:

   ```bash
   docker compose up -d
   ```

   Dann http://localhost:8080 öffnen und unter „Familie einrichten“ den ersten
   Administrator anlegen. Eine Beispielfamilie gibt es hier bewusst nicht
   (`FAMILYHUB_SAMPLEDATA_ENABLED` in `.env`).

   Wichtig: im Ordner `deploy`. Im Projektordner selbst meldet `docker compose`
   „no configuration file provided“. Die Datenbank des einfachen Starts hat
   keinen Port nach außen; eine für die Entwicklung gestartete MongoDB auf
   Port 27017 stört also nicht. Nur Port 8080 muss frei sein, das
   Entwicklungs-Backend (`./mvnw spring-boot:run`) also vorher beenden.

| Aufgabe | Befehl (im Ordner `deploy`) |
|---|---|
| Beenden | `docker compose down` |
| Protokoll ansehen | `docker compose logs -f familyhub` |
| Update (veröffentlichtes Image) | `docker compose pull`, danach `docker compose up -d` |
| Update (selbst gebaut) | `docker build -t familyhub:lokal ..`, danach `docker compose up -d` |

Die Daten liegen im Docker-Volume `familyhub_familyhub-data` und bleiben bei
`down` und bei Updates erhalten; erst `docker compose down -v` löscht sie.

Zugangsdaten für Wetter, Google, Telegram und WhatsApp stehen hier nicht in
`local.properties`, sondern in `.env`. Die Namen folgen den Einstellungen:
Punkte werden zu `_`, Bindestriche fallen weg, alles in Großbuchstaben, z. B.
`familyhub.weather.api-key` → `FAMILYHUB_WEATHER_APIKEY`. So lässt sich auch
jede Einstellung aus der Tabelle unten setzen. Uhrzeiten gelten in der Zeitzone
Europe/Berlin.

So eingerichtet ist FamilyHub nur vom eigenen Rechner aus erreichbar. Für Handys
und andere Geräte braucht es eine Adresse mit HTTPS, siehe unten.

### Sichern und Wiederherstellen

`deploy/backup.sh` sichert die Datenbank, während FamilyHub läuft, als eine
Datei nach `deploy/backups/` und löscht Sicherungen, die älter als 14 Tage sind
(`BACKUP_KEEP_DAYS`). Läuft unter Linux und macOS; auf einem Server täglich per
cron, z. B. um 03:30 Uhr:

```bash
30 3 * * * /pfad/zu/deploy/backup.sh >> /pfad/zu/deploy/backups/backup.log 2>&1
```

Eine Sicherung auf demselben Rechner hilft nicht bei einem Plattenschaden oder
einem verlorenen Server: die Dateien regelmäßig woandershin kopieren.

Zurückspielen (ersetzt alle Daten in FamilyHub durch den Stand der Sicherung):

```bash
cd deploy
docker compose stop familyhub
docker compose exec -T mongodb mongorestore --archive --gzip --drop \
  --username familyhub --password '<MONGO_PASSWORD>' --authenticationDatabase admin \
  < backups/familyhub-<Datum>.archive.gz
docker compose start familyhub
```

`.env` gehört mit zur Sicherung: Ohne `MONGO_PASSWORD` kommt man nicht an die
Daten, ohne `FAMILYHUB_GOOGLE_TOKENKEY` sind die gespeicherten Google-Zugänge
nicht mehr lesbar. Sie enthält aber alle Zugangsdaten, also getrennt und sicher
aufbewahren.

### Auf einem Server mit HTTPS betreiben

Damit Handys und andere Geräte FamilyHub erreichen (und die App sich dort
installieren lässt), läuft es auf einem Server mit eigener Domain. Vor FamilyHub
steht dann [Caddy](https://caddyserver.com): Caddy holt das HTTPS-Zertifikat
selbst bei Let's Encrypt, erneuert es und leitet http auf https um.

**Voraussetzungen:** ein Linux-Server mit Docker Engine und Compose (z. B. ein
kleiner vServer in der EU), eine Domain oder Subdomain, deren DNS-Eintrag
(A bzw. AAAA) auf den Server zeigt, und von außen offene Ports 80 und 443.

1. Auf dem Server einen Ordner mit `compose.yaml`, `compose.server.yaml`,
   `Caddyfile`, `backup.sh` und `.env.example` aus `deploy/` anlegen,
   `.env.example` nach `.env` kopieren und ausfüllen. Zusätzlich zu oben:

   ```properties
   FAMILYHUB_DOMAIN=familyhub.example.de
   FAMILYHUB_URL=https://familyhub.example.de
   ```
2. Starten:

   ```bash
   docker compose -f compose.yaml -f compose.server.yaml up -d
   ```

   Beim ersten Start holt Caddy das Zertifikat (einige Sekunden, Protokoll mit
   `docker compose -f compose.yaml -f compose.server.yaml logs -f caddy`).
   Danach https://familyhub.example.de öffnen und die Familie einrichten.
3. Sicherung per cron einrichten (siehe oben).

Hinter Caddy setzt FamilyHub Anmelde-Cookies nur noch für https
(`SERVER_SERVLET_SESSION_COOKIE_SECURE`), deshalb klappt die Anmeldung dort nur
über die Domain, nicht direkt über Port 8080. Für Google Kalender in der Google
Cloud Console `https://<Domain>/api/google/callback` als Weiterleitungs-URI
eintragen. Für WhatsApp ist dieselbe Adresse mit `/api/whatsapp` die Callback
URL; ein Tunnel wie ngrok ist dann nicht mehr nötig.

## Wiederkehrende Aufgaben

Im Aufgaben-Formular wiederholt „↻ Nach Erledigung wiederholen“ eine Aufgabe
im Abstand von Tagen, Wochen oder Monaten, gezählt ab dem Abhaken (Beispiel in
den Beispieldaten: „Blumen gießen“ alle 3 Tage). „Zuständig“ legt fest, wer die
nächsten Runden bekommt:

- **Eine Person:** Jede neue Runde geht wieder an sie. Das darf jeder, der die
  Aufgabe auch ohne Wiederholung anlegen dürfte, z. B. Jugendliche für sich
  selbst („Gitarre üben“ jeden Tag).
- **Wer zuerst kommt:** Jede neue Runde ist für alle offen. Kinder,
  Jugendliche und Erwachsene sehen sie, übernehmen sie mit „Ich mach's“ oder
  haken sie direkt ab; mit „Wieder für alle freigeben“ geben sie sie zurück.
  Gäste sehen sie nicht. Einrichten darf das nur, wer Aufgaben für die ganze
  Familie anlegt (in den Standardrollen die Administratoren).

Für beide gilt:

- Beim Abhaken entsteht sofort die nächste Runde: fällig am Tag des Abhakens
  plus Abstand, mit Titel, Punkten und Priorität der alten. Wer früher abhakt,
  ist früher wieder dran; bleibt eine Runde liegen, kommen keine neuen dazu.
  Die Karte zeigt, wer zuletzt wann erledigt hat.
- Ein geänderter Abstand gilt ab der nächsten Runde.
- Punkte gibt es wie immer nach der Bestätigung und nur für Kinder und
  Jugendliche. Hakt bei „Wer zuerst kommt“ ein Erwachsener ab, hat nur diese
  Runde keine Punkte.
- Wird der Haken zurückgenommen oder die Erledigung zurückgegeben, verschwindet
  die nächste Runde wieder, solange sie noch niemand übernommen hat.
- Beenden: im Formular die Wiederholung abschalten oder die offene Runde löschen.
  Bonus-Aufgaben behalten ihr eigenes „↻ Wiederkehrend“ (nach der Bestätigung
  wieder offen, ohne Frist).

## Einstellungen

Die Einstellungen stehen in `src/main/resources/application.properties`.
Zugangsdaten für Google, OpenWeather, WhatsApp und Telegram gehören **nie** dorthin,
sondern in eine Datei `local.properties` im Projektordner. Sie steht in
`.gitignore` und wird beim Start automatisch gelesen.

| Einstellung | Standard | Bedeutung |
|---|---|---|
| `familyhub.sample-data.enabled` | `true` | Beispielfamilie bei leerer Datenbank anlegen |
| `familyhub.roles.teen-age` | `13` | Ab diesem Alter wird ein Kind automatisch Jugendlicher |
| `familyhub.login.max-failures` / `lock-duration` | `5` / `PT5M` | So viele Fehlversuche sperren ein Konto so lange |
| `server.servlet.session.timeout` | `8h` | Dauer einer Anmeldung |
| `familyhub.waste.task-sync-cron` | `0 50 7 * * *` | Täglich um 07:50 Uhr Müll-Aufgaben für die nächsten sieben Tage anlegen |
| `familyhub.shopping.return-cron` | `0 1 0 * * *` | Täglich um 00:01 Uhr fällige wiederkehrende Artikel zurück auf die Einkaufsliste holen |
| `familyhub.calendar.series-horizon` | `P12M` | So weit im Voraus legt FamilyHub die Termine einer Serie an |
| `familyhub.calendar.series-extend-cron` | `0 20 3 * * *` | Täglich um 03:20 Uhr die Serien bis zu diesem Vorlauf verlängern |
| `familyhub.google.sync-interval` | `PT15M` | Takt des Abgleichs mit Google |
| `familyhub.google.push-delay` | `PT10S` | So schnell kommen geänderte Termine in den Google-Kalender „FamilyHub“ |
| `familyhub.weather.cache-duration` | `PT10M` | So lange merkt sich das Backend eine Wetterabfrage |
| `familyhub.telegram.polling` | `true` | Nachrichten an den Telegram-Bot selbst abholen |
| `familyhub.notifications.due-reminder-cron` | `0 0 7 * * *` | Täglich um 07:00 Uhr an dringende Aufgaben erinnern, die heute fällig sind |
| `familyhub.notifications.short-notice` | `PT24H` | Termine, die so bald beginnen, gelten als kurzfristig |
| `familyhub.notifications.pending-after` | `PT24H` | So lange darf eine Bestätigung offen sein, bevor FamilyHub erinnert |
| `familyhub.cors.allowed-origins` | `http://localhost:5173` | Adresse der Oberfläche |

## Wiederkehrende Einkäufe

Beim Hinzufügen oder Bearbeiten eines Artikels legt die Auswahl neben
„Dringend“ fest, ob er wiederkommt:

- **Einmalig:** wie bisher, „Abgehakte entfernen“ löscht ihn.
- **↻ Bei jedem Einkauf:** „Abgehakte entfernen“ löscht ihn nicht, er steht
  danach gleich wieder offen auf der Liste (Beispiel: Bio-Milch).
- **↻ Nach Abstand:** Er kommt einige Tage, Wochen oder Monate nach dem Kauf
  (dem Tag des Abhakens) offen zurück. Nach „Abgehakte entfernen“ steht er bis
  dahin unten unter „Kommt wieder“ (Beispiel: Klopapier alle 2 Wochen). Kurz
  nach Mitternacht holt FamilyHub fällige Artikel zurück, auch wenn sie noch
  abgehakt auf der Liste stehen.

Unter „Kommt wieder“ holt „Jetzt auf die Liste“ einen Artikel früher zurück,
✕ nimmt ihn ganz von der Liste. Ein geänderter Abstand zählt ab dem letzten
Kauf. „Dringend“ gilt nur bis zum nächsten Einkauf. Wiederkehrende Artikel legt
an, wer direkt auf die Liste schreibt (Eltern und Jugendliche); Kinder schlagen
nur einzelne Artikel vor. Zutaten aus dem Essensplan wiederholen sich nicht,
die setzt der Essensplan selbst wieder auf die Liste.

## Serientermine

Im Termin-Formular unter „Wiederholen“ wird aus einem Termin eine Serie:
täglich, wöchentlich (an einem oder mehreren Wochentagen), monatlich am selben
Tag oder jährlich, jeweils auch „alle 2 Wochen“ usw., ohne Ende, bis zu einem
Datum oder für eine Anzahl von Terminen. Tage, die es nicht gibt, entfallen wie
bei Google (monatlich am 31., jährlich am 29. Februar). Serientermine tragen im
Kalender ein ↻.

- FamilyHub legt die einzelnen Termine zwölf Monate im Voraus an und verlängert
  die Serie jede Nacht. Für Rechte, Benachrichtigungen und den Google-Export
  sind es ganz normale Termine.
- Beim Ändern oder Löschen fragt FamilyHub: **nur diesen Termin** oder **diesen
  und alle folgenden** (beim ersten Termin: die ganze Serie). Einzeln geänderte
  Termine bleiben, wenn später die Serie geändert wird; einzeln gelöschte, etwa
  in den Ferien, kommen nicht wieder.
- Eine gemeinsame Serie von Jugendlichen ist ein Vorschlag mit nur dem ersten
  Termin; erst die Freigabe legt die übrigen an.
- Telegram meldet eine neue Serie einmal mit ihrem Rhythmus, wenn ihr nächster
  Termin innerhalb von 24 Stunden liegt.

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

## Fahrzeit einrichten (optional)

Ortssuche und Fahrzeit nutzen [openrouteservice](https://openrouteservice.org)
(HeiGIT, Kartendaten von OpenStreetMap). Ohne Schlüssel startet FamilyHub
trotzdem; Termine bekommen dann nur frei eingetippte Orte ohne Fahrzeit.

1. Unter [openrouteservice.org](https://openrouteservice.org/dev/#/signup) ein
   kostenloses Konto anlegen und einen API-Schlüssel erzeugen.
2. In `local.properties` im Projektordner eintragen (steht in `.gitignore`,
   **nie einchecken**):

   ```properties
   familyhub.ors.api-key=<API-Schlüssel>
   ```
3. Backend neu starten. Als Administrator unter „Familie“ in der Karte
   **Adresse** den Wohnort suchen und übernehmen.

Im Termin-Formular sucht „Adresse suchen“ den Ort; mit „individuellen Ort
eingeben“ geht auch ein Ort ohne Fahrzeit. Aus Wohnort, Ort und Verkehrsmittel
berechnet FamilyHub die Fahrzeit und die Abfahrt (Beginn minus Fahrzeit minus
15 Minuten Puffer). Wochen- und Tagesansicht zeigen die Abfahrt am Termin.
Die Quellenangabe unter der Fahrzeit verlangen die Nutzungsbedingungen von
openrouteservice; sie darf nicht entfernt werden.

## Als App installieren

FamilyHub lässt sich wie eine App installieren (eigenes Fenster, Symbol im
Startmenü bzw. auf dem Startbildschirm). In Edge oder Chrome
`http://localhost:5173` (mit Docker: `http://localhost:8080`) öffnen und in der
Adressleiste auf „App installieren“ klicken (Edge: Menü „Apps“ → „Diese Website
als App installieren“).

Auf dem Handy funktioniert das erst, wenn FamilyHub dort über HTTPS erreichbar
ist, also auf einem Server läuft; im lokalen Entwicklungsbetrieb ist die
Oberfläche nur auf dem eigenen Rechner erreichbar.

## Projektstruktur

```
├── src/main/java/de/familyhub/   Backend (Spring Boot), ein Paket je Bereich
├── src/test/java/de/familyhub/   Backend-Tests, gleiche Pakete
├── frontend/                     Oberfläche (React, siehe frontend/README.md)
├── scripts/                      Hilfsskripte (z. B. MongoDB starten)
├── deploy/                       FamilyHub komplett per Docker (compose.yaml, Vorlage für .env)
├── Dockerfile                    Docker-Image: Oberfläche und Backend in einem
└── compose.dev-mongodb.yaml      nur MongoDB für die Entwicklung, per Docker
```

Die Backend-Pakete heißen wie die Bereiche: `calendar`, `google`, `task`,
`points`, `rewards`, `achievements`, `shopping`, `meals`, `messages`, `waste`,
`weather`, `whatsapp`, `telegram`, `family` und `settings`. Querschnittsthemen
liegen in `security` (Anmeldung), `permission` (Rollen und Rechte), `inbox`
(gemeinsamer Eingang für Nachrichten von WhatsApp und Telegram), `notifications`
(Benachrichtigungen über Telegram), `live` (Live-Aktualisierung), `web`
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
