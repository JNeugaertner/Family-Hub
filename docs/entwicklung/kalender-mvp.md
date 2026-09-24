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
| Noch offen | springdoc-openapi (Swagger UI) ja/nein; Lombok (Empfehlung: weglassen, Java-Records reichen) |

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
| 4 | REST: `/api/members` und `/api/events?from=&to=&memberId=` (CRUD), einheitliche Fehlerantworten, CORS für `localhost:5173` | offen |
| 5 | optional: Terminüberschneidungen erkennen (`conflict: true`) | offen |
| 6 | Frontend an das Backend anbinden | offen |

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
