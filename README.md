# FamilyHub AI

KI-gestützter Familienplaner für Schule, Kindergarten, Alltag, Einkauf und Haushalt.

FamilyHub AI bündelt Kalender, Aufgaben, Einkauf, Essensplanung, Wetter,
Müllabfuhr, Fahrzeiten, Messenger-Eingaben und Sprachfunktionen in einer
gemeinsamen Oberfläche. Leitprinzip: **Der Agent informiert und bereitet vor,
die Eltern entscheiden.**

**Status:** Proof of Concept. Aktuell entsteht das Backend für das Kalender-MVP
(siehe [Entwicklungsplan](docs/entwicklung/kalender-mvp.md)).

## Tech-Stack

| Bereich | Technologie |
|---|---|
| Backend | Java 25, Spring Boot 4.1, Maven Wrapper |
| Datenbank | MongoDB 8.0 (lokal) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4 (ursprünglich aus Figma Make) |
| Tests | JUnit 5, MockMvc |

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

## Projektstruktur

```
├── src/main/java/de/familyhub/   Backend (Spring Boot)
├── src/test/java/de/familyhub/   Backend-Tests
├── frontend/                     Oberfläche (React, siehe frontend/README.md)
├── scripts/                      Hilfsskripte (z. B. MongoDB starten)
└── docs/
    ├── architektur/              C4-Modell, Schichtenmodell, Übersichten
    ├── konzept/                  Rollen- und Rechtekonzept mit Diagrammen
    └── entwicklung/              Entwicklungspläne und technische Entscheidungen
```

## Dokumentation

- [Entwicklungsplan Kalender-MVP](docs/entwicklung/kalender-mvp.md): Ziel, Phasen,
  technische Entscheidungen
- [Rollen- und Berechtigungskonzept](docs/konzept/rollenkonzept.md): 5 Rollen,
  Rechte, Freigabe-Workflow, offene Fragen
- [Rechtekonzept als Diagramm](docs/konzept/rechtekonzept.drawio)
- [C4-Modell](docs/architektur/c4-modell.drawio): Systemkontext, Container,
  Komponenten, MVP-Komponenten

Die `.drawio`-Dateien lassen sich mit der draw.io-Desktop-App, auf
[app.diagrams.net](https://app.diagrams.net) oder mit der VS-Code-Erweiterung
„Draw.io Integration" öffnen. User Stories, Personas und das Figma-UI liegen im
Team-Ordner.

## Zusammenarbeit

- **Nicht direkt auf `main` committen.** Für jede Aufgabe einen Branch anlegen
  (`feature/...`, `fix/...`, `docs/...`) und per Pull Request mergen.
- **Commit-Nachrichten auf Deutsch**, erste Zeile als kurze Zusammenfassung im
  Imperativ, z. B. „Termin-API um Zeitraumfilter ergänzen". Bei Bedarf darunter
  eine Leerzeile und das Warum.
- **IDE-Dateien** (`.idea/`, `*.iml`, `.vscode/`) werden nicht eingecheckt.
  IntelliJ importiert das Projekt über die `pom.xml`.
- Diagramme als `.drawio` unter `docs/` ablegen, nicht als Einzelbilder.
