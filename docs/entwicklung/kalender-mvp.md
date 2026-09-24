# Entwicklungsplan: Kalender-MVP

**Ziel:** Ein Backend, das Familienmitglieder und Termine verwaltet und per REST
an das React-UI liefert. Grundlage sind User Story 1 (Kalender) und die C4-Seite
`C3_Component_MVP` (Kalender API, Termin API, Familienverwaltung API) in
[c4-modell.drawio](../architektur/c4-modell.drawio).

**Nicht enthalten:** externe APIs (Google Kalender, Messenger, Fahrzeiten,
Müllabfuhr), Anmeldung, Rollen und Rechte, KI.

## Technische Entscheidungen (23.09.2026)

| Thema | Entscheidung |
|---|---|
| Sprache | Java 25 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Bean Validation) |
| Build | Maven über den Maven Wrapper (`mvnw`), keine Maven-Installation nötig |
| Struktur | Maven-Standard, Paket `de.familyhub`, Startklasse `Main` |
| Datenbank | MongoDB 8.0, lokal; Zugriff über Spring Data MongoDB |
| Tests | JUnit 5 und MockMvc; eingebettete MongoDB (Flapdoodle) für Datenbanktests |
| Termin-Zuordnung | genau ein Familienmitglied pro Termin, wie im Figma-UI |
| Zeitformat | Beginn und Ende als Datum mit Uhrzeit (`2026-09-25T10:00`), nicht getrennt wie im Figma-UI; Ende ist Pflicht und muss nach dem Beginn liegen |
| API-Doku | Swagger UI über springdoc-openapi 3.1 |
| Noch offen | Lombok (Empfehlung: weglassen, Java-Records reichen) |

**Warum nicht Redis als Hauptdatenbank?** Redis ist im Kern ein Cache im
Arbeitsspeicher. Abfragen wie „alle Termine von Lucas in dieser Woche" müssten
von Hand über zusätzliche Datenstrukturen gebaut werden, und Redis läuft nicht
nativ auf Windows. Später denkbar als Cache für externe APIs oder für die
Echtzeit-Einkaufsliste.

## Phasen

| Phase | Inhalt | Status |
|---|---|---|
| 1 | Spring-Boot-Grundgerüst mit Maven Wrapper, `GET /api/health` | erledigt |
| 1b | lokale MongoDB, Startskript `scripts/start-mongodb.ps1` | erledigt |
| 2 | Datenmodell `FamilyMember` (id, name, color) und `CalendarEvent` (id, title, start, end, memberId, category, location, description) mit Validierung | erledigt |
| 3 | Spring Data MongoDB, Repositories, Beispieldaten aus dem Figma-UI beim ersten Start | erledigt |
| 4 | REST: `/api/members` und `/api/events?from=&to=&memberId=` (CRUD), einheitliche Fehlerantworten, CORS für `localhost:5173`, Swagger UI | erledigt |
| 5 | optional: Terminüberschneidungen erkennen (`conflict: true`) | offen |
| 6 | Frontend nach `frontend/` ins Repo, Kalender und Dashboard lesen aus dem Backend, Formular zum Anlegen, Ändern und Löschen von Terminen | erledigt |

Kategorien für Termine entsprechen dem Figma-UI: `school`, `sports`,
`appointment`, `family`, `work`, `reminder`.

Ob das zugeordnete Familienmitglied wirklich existiert, lässt sich erst mit der
Datenbank prüfen und kommt deshalb in Phase 4.

## Datenhaltung (Phase 3)

- Collections `members` und `events` in der Datenbank `familyhub`.
- Zeitraumabfrage `findOverlapping(from, to)` liefert alle Termine, die den
  Zeitraum berühren, auch solche, die vorher beginnen oder danach enden. Grundlage
  für Tages-, Wochen- und Monatsansicht.
- Beispieldaten: Beim Start mit leerer Datenbank werden die 5 Familienmitglieder
  und 16 Termine aus dem Figma-UI angelegt. Termine ohne Endzeit dauern eine
  Stunde. Abschaltbar mit `familyhub.sample-data.enabled=false`. Zum Zurücksetzen
  die Datenbank löschen: `mongosh mongodb://127.0.0.1:27017/familyhub --eval "db.dropDatabase()"`.
- MongoDB speichert Zeiten intern in UTC und rechnet mit der Zeitzone des Servers
  um. Solange Backend und Datenbank auf demselben Rechner laufen, ist das
  unsichtbar. Für einen späteren Betrieb über Zeitzonen hinweg muss das neu
  bewertet werden.

## REST-Schnittstelle (Phase 4)

Ausprobieren im Browser: **http://localhost:8080/swagger-ui.html** (Backend und
MongoDB müssen laufen).

| Methode und Pfad | Zweck | Antworten |
|---|---|---|
| `GET /api/members` | alle Familienmitglieder | 200 |
| `GET /api/members/{id}` | ein Mitglied | 200, 404 |
| `POST /api/members` | Mitglied anlegen | 201 mit `Location`-Header, 400 |
| `PUT /api/members/{id}` | Mitglied ändern | 200, 400, 404 |
| `DELETE /api/members/{id}` | Mitglied löschen | 204, 404, 409 wenn noch Termine zugeordnet sind |
| `GET /api/events` | alle Termine, nach Beginn sortiert | 200 |
| `GET /api/events?from=…&to=…` | Termine, die den Zeitraum berühren (`to` exklusiv) | 200, 400 |
| `GET /api/events?memberId=…` | zusätzlich nach Person filtern, auch mit `from`/`to` kombinierbar | 200 |
| `GET /api/events/{id}` | ein Termin | 200, 404 |
| `POST /api/events` | Termin anlegen | 201, 400 (auch bei unbekanntem Mitglied) |
| `PUT /api/events/{id}` | Termin ändern | 200, 400, 404 |
| `DELETE /api/events/{id}` | Termin löschen | 204, 404 |

**Fehlerformat** (RFC 9457, `application/problem+json`), Feldfehler unter `errors`:

```json
{
  "status": 400,
  "title": "Ungültige Eingaben",
  "detail": "Mindestens ein Feld ist ungültig, Details unter \"errors\".",
  "instance": "/api/events",
  "errors": {
    "endAfterStart": "Ende muss nach dem Beginn liegen",
    "title": "Titel darf nicht leer sein"
  }
}
```

Eine mitgeschickte `id` wird beim Anlegen ignoriert; beim Ändern gilt die `id`
aus dem Pfad. CORS erlaubt nur `http://localhost:5173` (änderbar über
`familyhub.cors.allowed-origins`).

## Tests

- Datenbanktests nutzen eine eingebettete MongoDB (Flapdoodle, Version 8.0.23,
  die neueste 8.0-Version, die Flapdoodle kennt). Beim **ersten** Testlauf lädt sie
  rund 840 MB herunter (Cache in `~/.embedmongo`), das dauert einige Minuten.
- Unter Windows nutzen die Tests den Windows-Zertifikatsspeicher (Maven-Profil
  `windows-truststore`). Sonst scheitert der Download im Firmennetz, weil Java
  das Zertifikat der HTTPS-Prüfung nicht kennt.

## Danach

Echte Anmeldung, dann Rollen und Rechte gemäß
[Rollenkonzept](../konzept/rollenkonzept.md).
