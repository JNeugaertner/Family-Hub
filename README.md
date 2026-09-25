# FamilyHub AI

KI-gestützter Familienplaner für Schule, Kindergarten, Alltag, Einkauf und Haushalt.

FamilyHub AI bündelt Kalender, Aufgaben, Einkauf, Essensplanung, Wetter,
Müllabfuhr, Fahrzeiten, Messenger-Eingaben und Sprachfunktionen in einer
gemeinsamen Oberfläche. Leitprinzip: **Der Agent informiert und bereitet vor,
die Eltern entscheiden.**

**Status:** Proof of Concept. Umgesetzt sind der Familienkalender, Anmeldung,
Rollen und Rechte sowie Aufgaben mit Punktesystem (Punkte nach Bestätigung
durch die Eltern). Die übrigen Bereiche der Oberfläche zeigen noch feste
Beispieldaten.

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Spring Security (Sitzungs-Cookie, CSRF), Maven Wrapper |
| Datenbank | MongoDB 8.0 (lokal) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4 (ursprünglich aus Figma Make) |
| Tests | JUnit 5, MockMvc, eingebettete MongoDB (Flapdoodle) |

## Schnellstart

**Voraussetzungen:** JDK 25, MongoDB 8.0 und für die Oberfläche Node.js 24 mit
pnpm 10. Maven wird nicht benötigt, der Maven Wrapper lädt es beim ersten Aufruf
selbst. Unter Windows kann MongoDB ohne Admin-Rechte als ZIP nach
`%LOCALAPPDATA%\Programs\mongodb` entpackt werden.

```bash
# 1. MongoDB starten (lauscht nur auf 127.0.0.1:27017, beenden mit Strg+C)
powershell -ExecutionPolicy Bypass -File scripts/start-mongodb.ps1

# 2. Backend starten (Windows: mvnw.cmd statt ./mvnw)
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

Beim ersten Start mit leerer Datenbank legt das Backend die Beispielfamilie aus
dem Figma-UI an. Der erste Testlauf lädt einmalig eine Test-MongoDB herunter
(ca. 840 MB, einige Minuten).

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

## Projektstruktur

```
├── src/main/java/de/familyhub/   Backend (Spring Boot)
├── src/test/java/de/familyhub/   Backend-Tests
├── frontend/                     Oberfläche (React, siehe frontend/README.md)
└── scripts/                      Hilfsskripte (z. B. MongoDB starten)
```

## Dokumentation

Die REST-Schnittstelle ist bei laufendem Backend in der Swagger UI beschrieben
und lässt sich dort ausprobieren: **http://localhost:8080/swagger-ui.html**
(zuerst unter „Anmeldung“ einloggen).

Konzepte, Diagramme, User Stories, Personas und das Figma-UI liegen im
Team-Ordner, nicht im Repository.

## Zusammenarbeit

- **Branches:**
  - `main` enthält nur geprüfte, lauffähige Stände. Jede Version bekommt einen
    Tag (`v0.1.0`, …); `release/v0.1.0` sichert den ersten Stand zusätzlich.
  - `develop` ist die Basis für die Weiterentwicklung. Neue Features auf
    `feature/...`, Fehlerbehebungen auf `fix/...` von `develop` abzweigen und
    per Pull Request in `develop` mergen.
  - Ist `develop` getestet und stabil, wird `develop` in `main` gemergt und ein
    neuer Tag gesetzt. Nie direkt auf `main` oder `release/...` committen.
- **Commit-Nachrichten auf Deutsch**, erste Zeile als kurze Zusammenfassung im
  Imperativ, z. B. „Termin-API um Zeitraumfilter ergänzen". Bei Bedarf darunter
  eine Leerzeile und das Warum.
- **IDE-Dateien** (`.idea/`, `*.iml`, `.vscode/`) werden nicht eingecheckt.
  IntelliJ importiert das Projekt über die `pom.xml`.
- Im Repository liegt nur, was das Programm braucht. Konzepte und Diagramme
  gehören in den Team-Ordner.
