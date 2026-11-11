# Mitmachen bei FamilyHub

Schön, dass du mitarbeiten willst. Diese Seite fasst zusammen, wie du FamilyHub
zum Laufen bringst, wie wir arbeiten und was vor einem Merge geprüft wird.
Was FamilyHub kann und wie man es betreibt, steht im [README](README.md).

## 1. Einrichten

Du brauchst Git, JDK 25, Node.js 24 mit pnpm und MongoDB 8 (oder Docker für
MongoDB). Die Installation je Betriebssystem beschreibt das README unter
[„FamilyHub starten“](README.md#familyhub-starten).

```bash
git clone https://github.com/JNeugaertner/Family-Hub.git
cd Family-Hub
git switch develop
```

Dann drei Teile starten, jeweils in einem eigenen Terminal:

| Teil | Befehl | Adresse |
|---|---|---|
| Datenbank | `scripts/start-mongodb.sh` (Windows: `scripts\start-mongodb.ps1`) oder `docker compose -f compose.dev-mongodb.yaml up -d` | localhost:27017 |
| Backend | `./mvnw spring-boot:run` | http://localhost:8080 (Swagger UI unter `/swagger-ui.html`) |
| Oberfläche | `cd frontend && pnpm install && pnpm dev` | http://localhost:5173 |

Beim ersten Start legt FamilyHub eine Beispielfamilie an (Anmeldung z. B. mit
`sarah` / `familyhub`). Zugangsdaten für Google, Wetter, Fahrzeit, Telegram
oder WhatsApp gehören in `local.properties` im Projektordner. Diese Datei ist
in `.gitignore` und wird **nie** eingecheckt.

## 2. Arbeitsweise

- **Branches:** Neue Features auf `feature/...`, Fehlerbehebungen auf `fix/...`,
  immer von `develop` aus. Nie direkt auf `develop` oder `main` committen.
- **Fremde Branches** änderst du nicht. Hast du Anmerkungen zu der Arbeit von
  jemand anderem, sag es der Person.
- **Größere Features** in zwei Schritten: erst das Backend mit Tests
  („…, Phase 1“), dann die Oberfläche mit der Doku („…, Phase 2“).
- **Commit-Nachrichten auf Deutsch**, erste Zeile kurz im Imperativ, z. B.
  „Termin-API um Zeitraumfilter ergänzen“. Bei Bedarf darunter eine Leerzeile
  und das Warum.
- **Merge nach develop** als Merge-Commit („… nach develop uebernehmen“),
  nachdem die Prüfungen unten grün sind.
- **Release:** siehe README, Abschnitt [„Zusammenarbeit“](README.md#zusammenarbeit).

## 3. So sieht der Code aus

- **Backend:** ein Paket je Bereich (`calendar`, `task`, `shopping`, …). Ein
  Bereich hat meist ein Dokument (Record), ein Repository, einen Controller und
  eine `…Access`-Klasse mit den Rechteregeln. Rechte prüft immer das Backend;
  die Oberfläche blendet nur aus, was ohnehin verboten ist.
- **Fehler** gibt der Controller als `ApiException` zurück (403, 404, 409 oder
  ein Feldfehler mit 400). Die Meldungen sind auf Deutsch und für die Familie
  verständlich.
- **Live-Aktualisierung** passiert von selbst: Jede erfolgreiche Änderung unter
  `/api/<bereich>/…` meldet `<bereich>` an alle offenen Browser.
- **Oberfläche:** React mit TypeScript und Tailwind. Daten holen und ändern die
  `…DataContext`-Provider, die Seiten liegen in `frontend/src/components`.
- **Kommentare auf Deutsch** und kurz. Sie erklären das Warum, nicht das Was.
  Fachliche Entscheidungen bekommen ihr Datum („Entscheidung vom 03.11.2026“),
  damit man sie später wiederfindet.
- **Barrierefreiheit:** Jedes Bedienelement hat einen sichtbaren oder
  vorgelesenen Namen, Farben erfüllen WCAG 2.2 AA. Die Regressionstests prüfen
  das mit axe.

## 4. Vor dem Merge prüfen

```bash
./mvnw test                         # Backend-Tests (mit eingebetteter MongoDB)
cd frontend && pnpm exec tsc -b     # Typen der Oberfläche
cd regression && ./regression.sh    # Regressionstests im Browser (braucht Docker)
```

`regression.sh` (Windows: `regression.ps1`) baut FamilyHub aus deinem Stand,
startet es mit einer leeren Wegwerf-Datenbank auf Port 8081, klickt sich durch
232 und mehr Szenarien und räumt danach auf. Dein laufendes FamilyHub bleibt
unberührt. Einzelne Bereiche gehen mit `--tags`, z. B. `--tags @calendar`.

Neue Funktionen bekommen Tests: Backend-Tests für Regeln und Rechte, bei
sichtbaren Abläufen zusätzlich ein Szenario in `regression/features/`. Wenn
sich für Nutzer etwas ändert, gehört es ins README und beim nächsten Release in
den [CHANGELOG](CHANGELOG.md).

## 5. Fragen

Konzepte, Diagramme, User Stories und das Figma-UI liegen im Team-Ordner, nicht
im Repository. Bei Fragen zur Fachlichkeit oder zu einer Entscheidung: im Team
nachfragen, bevor du etwas umbaust.
