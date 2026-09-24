# FamilyHub AI

KI-gestützter Familienplaner für Schule, Kindergarten, Alltag, Einkauf und Haushalt.

FamilyHub AI ist ein modularer Familienplaner für Familien mit Kindern in Schule oder
Kindergarten. Der PoC bündelt Kalender, Aufgaben, Einkauf, Essensplanung, Wetter,
Müllabfuhr, Fahrzeiten, Messenger-Eingaben und Sprachfunktionen in einer gemeinsamen
Oberfläche. Leitprinzip: Der Agent informiert und bereitet vor, die Eltern entscheiden.

Status: Proof of Concept / Konzeptphase. Es existiert noch keine produktive
Implementierung; dieser Stand dokumentiert die fachliche Konzeption.

## Rollen- und Berechtigungskonzept (Stand 2026-09-18)

Grundlage: `FamilyHub_AI_PoC` (Kap. 5) und das separate Rollenkonzept-Dokument.
Im Folgenden der abgeglichene Vorschlag für die Rollenstruktur inklusive
Berechtigungen als fachliche Basis für die spätere Umsetzung. **Es handelt sich
ausdrücklich noch nicht um eine technische Implementierung.**

### 1. Kurzabgleich der Vorgaben

- **PoC (Kap. 5)** skizziert nur 3 grobe Rollen: Eltern, Kinder, Agent – reicht als
  Leitplanke, ist aber zu grob für ein Familienmodell mit bis zu 5 Kindern
  unterschiedlichen Alters.
- **Rollenkonzept.pdf** ist bereits deutlich granularer (Administrator, Jugendlicher,
  Kind, Gast, KI-Agent) und deckt Anwendungsfälle ab, die im PoC nur implizit
  vorkommen (z. B. Babysitter/Großeltern als „Gast", ältere Kinder mit mehr
  Eigenständigkeit).
- **Lücken in beiden Dokumenten**, die für ein belastbares Konzept noch fehlen:
  - Kein Konzept für *mehrere gleichberechtigte Administratoren* (PoC: bis zu 2
    Erwachsene, aber keine Aussage zur Gleich-/Ungleichrangigkeit).
  - Kein Modell dafür, wie Berechtigungen technisch strukturiert sind
    (Geltungsbereich „eigene Daten" vs. „Familiendaten" vs. „freigegebene Daten").
  - Kein Freigabe-Workflow für KI-Vorschläge, obwohl der PoC das an mehreren
    Stellen implizit fordert (Essensplanung, Rezepte, Messenger-Antworten).
  - Keine Aussage, wie Messenger- und Sprachkanal-Identitäten auf eine Rolle
    gemappt werden.

### 2. Basis-Rollenkatalog

| Rolle | Entspricht PoC | Typische Nutzer |
|---|---|---|
| Administrator | „Eltern" | Beide Erwachsene, gleichrangig |
| Jugendlicher | Erweiterung von „Kinder" | Kinder ca. 13–17 |
| Kind | „Kinder" | Kinder im Kindergarten-/Grundschulalter |
| Gast | nicht im PoC-Kap. 5 enthalten | Großeltern, Babysitter |
| KI-Agent | „Agent" | Systemakteur, kein Mensch |

Die Aufsplittung von „Kinder" in **Kind** und **Jugendlicher** ist sinnvoll, weil ein
5-Jähriger und ein 16-Jähriger fachlich völlig unterschiedliche Autonomie brauchen
(z. B. eigene Termine anlegen, Einkaufsliste bearbeiten).

### 3. Architekturprinzip: Rollen als Berechtigungs-Bündel

Damit das Modell „flexibel und erweiterbar" bleibt, sollte eine Rolle kein fest
verdrahtetes Konzept, sondern eine benannte Menge einzelner Berechtigungen sein:

- **Berechtigung = Tripel** aus *Modul* (z. B. Kalender), *Aktion* (z. B. Erstellen)
  und *Geltungsbereich* (eigene Daten / Familiendaten / freigegebene Daten).
- Die 5 Rollen sind **Vorlagen**, keine Enum-Werte – der Administrator kann laut
  Rollenkonzept ohnehin „Rollen erstellen, bearbeiten und zuweisen". Das setzt
  voraus, dass das System echte Custom-Rollen aus Einzelberechtigungen
  zusammensetzen kann.
- **Einzel-Overrides pro Person** sollten möglich sein (z. B. ein reiferes
  11-jähriges Kind bekommt zusätzlich „Einkaufsliste bearbeiten", ohne gleich zum
  „Jugendlicher" hochgestuft zu werden).
- **Neue Module registrieren eigene Berechtigungen**, ohne dass das
  Rollen-Kernmodell angefasst werden muss.

### 4. Berechtigungsmatrix (Vorschlag)

| Modul | Administrator | Jugendlicher | Kind | Gast | KI-Agent |
|---|---|---|---|---|---|
| Familie & Rollen | Vollzugriff | – | – | – | – |
| Kalender | Vollzugriff (alle Termine) | Eigene Termine CRUD, Familientermine vorschlagen | Nur eigene ansehen | Nur freigegebene ansehen | Lesen (Konfliktprüfung), keine Änderung |
| Aufgaben & Haushalt | Vollzugriff (zuweisen, ändern, löschen) | Eigene verwalten, Zusatzaufgaben übernehmen | Ansehen, Abhaken | Freigegebene ansehen | Vorlesen, erinnern, Zuordnung vorschlagen |
| Punkte & Belohnungen | Vergeben, korrigieren, Regeln ändern | Eigene + Familienstand einsehen | Eigene einsehen | Kein Zugriff | Kein Zugriff |
| Einkaufsliste | Vollzugriff | Bearbeiten (hinzufügen/entfernen/abhaken) | Wünsche hinzufügen | Kein Zugriff | Vorschlagen (aus Rezepten) |
| Essensplanung & Rezepte | Vorschläge freigeben, Plan bearbeiten | Plan mitbearbeiten | Essenswünsche einreichen | Kein Zugriff | Vorschläge generieren (Status „Vorschlag") |
| Wetter/Tagesempfehlungen | Ansehen | Ansehen | Ansehen | Allg. Tagesinfo | Abrufen & Empfehlung generieren |
| Müllabfuhr | Adresse/Konfig verwalten | Ansehen | Ansehen | Falls freigegeben | Termine ermitteln, Aufgabe vorschlagen |
| Messenger-Integration | Freigaben erteilen, Kanäle konfigurieren | Anfragen vorbereiten *(in Klärung, siehe Abschnitt 7)* | Eigene Wünsche einreichen | – | Verfügbarkeit prüfen, Antwortentwurf (kein Versand ohne Freigabe) |
| Sprachassistent (Tablet) | Konfiguration, Stimmprofile | Nutzung, eigenes Profil | Nutzung, eigenes Profil | – | Ausführung (Erkennung/Vorlesen), keine Entscheidung |
| Fahrzeitberechnung | Konfiguration | Ansehen | Ansehen | – | Berechnung/Anzeige |
| Rollen & Berechtigungen | Vollzugriff | – | – | – | – |
| Systemeinstellungen/externe Dienste | Vollzugriff | – | – | – | – |

### 5. Zusätzliche Mechanismen, die das Rollenmodell braucht

1. **Freigabe-Workflow** (Status Entwurf/Vorschlag → Freigegeben/Abgelehnt) quer
   über Module – notwendig für KI-Essensvorschläge, Rezeptzutaten,
   Messenger-Antwortentwürfe und ggf. von Jugendlichen vorgeschlagene
   Familientermine.
2. **Freigabe pauschal je Kategorie** (Entscheidung vom 2026-09-22): Gäste
   sehen ganze Kategorien, die ein Administrator für sie freigeschaltet hat
   (z. B. „alle Kalendertermine außer als privat markiert"), keine Freigabe
   pro einzelnem Termin/Aufgabe. Ein „privat"-Flag auf einzelnen Datensätzen
   bleibt als Ausnahme-Mechanismus bestehen, um einzelne Einträge trotz
   freigegebener Kategorie zu verbergen.
3. **Altersbasierte Rollenzuordnung mit Übergang**: Geburtsdatum je Kind
   hinterlegen, System schlägt ab konfigurierbarem Alter (z. B. 13) automatisch
   „Jugendlicher" vor, Eltern können manuell überschreiben.
4. **Mehrkanal-Identität**: WhatsApp-/Telegram-Konto und Stimmprofil müssen
   eindeutig einem Familienmitglied zugeordnet sein, damit dieselbe
   Rollenprüfung unabhängig vom Zugriffskanal (Web, Tablet-Stimme, Messenger)
   gilt.
5. **Mehrere gleichrangige Administratoren**: beide Erwachsene erhalten
   identische Rechte, keine Hierarchie zwischen ihnen; Konflikte werden laut
   PoC 4.3 ohnehin nicht vom System aufgelöst, sondern menschlich entschieden.
6. **KI-Agent als Systemakteur, nicht als Nutzerkonto**: sollte technisch getrennt
   von menschlichen Rollen modelliert werden – rein lesend plus das Recht,
   Datensätze im Status „Vorschlag" zu erzeugen.
7. **Rate-Limits/Bestätigungsregeln** (PoC 4.11) sind ein rollenübergreifendes
   Querschnittsthema (Missbrauchsschutz bei Sprachsteuerung), kein Teil der
   Rollen-/Rechte-Struktur selbst.

### 6. Ausblick auf spätere Erweiterungen

Da Rollen = Bündel aus (Modul, Aktion, Geltungsbereich) sind, lassen sich später
ergänzen, ohne die Kernarchitektur zu ändern:

- Zweiter Haushalt / Co-Elternteil außerhalb der 2-Erwachsenen-Grenze
  (Patchwork-Familien)
- Zeitlich befristete Rollen (Babysitter mit Gültigkeitsfenster)
- Institutionelle Gäste (Schule/Kindergarten) mit Schreibrecht nur für bestimmte
  Termintypen

### 7. Entscheidungen zum Rollenmodell

Grundlage: Abgleich mit der finalen Fassung des Rollenkonzept-Dokuments am
2026-09-22. Von den vier ursprünglich offenen Fragen sind drei geklärt.

**Getroffene Entscheidungen:**

- **Administratoren gleichberechtigt.** Beide Erwachsenen sind immer
  gleichberechtigt Administrator, keine abgestufte Erwachsenen-Rolle. Passt
  zum fertigen Rollenkonzept, das nur eine Administrator-Rolle mit den
  typischen Nutzern „Eltern, Erziehungsberechtigte" kennt.
- **Gast-Freigabe pauschal je Kategorie**, nicht pro einzelnem Termin/Aufgabe
  (Details siehe Abschnitt 5, Punkt 2).
- **Altersübergang Kind→Jugendlicher automatisiert**: System schlägt ab
  konfigurierbarem Alter (Richtwert 13, siehe Rollenkonzept) die Rolle
  „Jugendlicher" vor, Eltern können manuell überschreiben.

**Noch offen:**

- **Soll „Jugendlicher" schon Teil des PoC-MVP sein?** Muss mit dem
  Projektleiter abgestimmt werden. Klärung angesetzt für Mittwoch,
  2026-09-23.
- **Messenger-Berechtigung für Jugendliche.** Die aktuelle Fassung des
  Rollenkonzepts hat „Messenger-Anfragen vorbereiten" beim Jugendlichen
  gegenüber einer früheren Fassung gestrichen. Offen, ob das Absicht ist oder
  ein Versehen beim Überarbeiten war – betrifft die Berechtigungsmatrix
  (Abschnitt 4), das Use-Case-Diagramm und `StandardRoles.jugendlicher()` im
  Prototyp.

## Projektstand und Fortschritt (Stand 2026-09-22)

Dieser Abschnitt hält fest, was rund um das Rollenkonzept bereits erarbeitet
wurde, wo die Ergebnisse liegen und was als Nächstes ansteht. Er dient als
Gedächtnisstütze für die Fortsetzung der Arbeit, unabhängig vom Chatverlauf.

### Was bereits existiert

**In diesem Repository (verfolgt, gepusht):**
- Diese README mit dem vollständigen Rollen- und Berechtigungskonzept
  (Abschnitte 1–7 oben).

**Außerhalb dieses Repositories** (bewusst separat, siehe „Wichtige Hinweise"
unten), unter `C:\Users\jneugart\IdeaProjects\FamilyHub-Rollenkonzept-Prototyp\`:

| Ordner | Inhalt |
|---|---|
| `src/` | Lauffähiger Java-Konzeptprototyp des Rollenmodells: `Module`, `Action`, `Scope`, `Permission`, `Role`, `StandardRoles` (die 5 Standardrollen), `FamilyMember` (inkl. Einzel-Overrides), `ApprovalStatus`/`ApprovableItem` (Freigabe-Workflow) und `PrototypeDemo` als ausführbares Beispiel. Mit `javac *.java && java PrototypeDemo` lauffähig; alle Beispielprüfungen liefern das erwartete Ergebnis. |
| `architektur/` | Architekturübersicht als SVG/PNG/`.drawio` (Clients → Zugriff & Sicherheit → Anwendungskern → lokale Daten → optionale externe Dienste). |
| `uml/` | `klassendiagramm.drawio` (Domänenmodell), `usecase-diagramm.drawio` (5 Rollen × Anwendungsfälle inkl. KI-Agent), `c4-kontextdiagramm.drawio` (C4 Level 1), `c4-containerdiagramm.drawio` (Level 2), `c4-komponentendiagramm.drawio` (Level 3, Zoom in die Kernanwendung), `c4-codediagramm.drawio` (Level 4, Zoom in Rollenmodell/Freigabe-Workflow, basiert auf dem Prototyp-Code), `uml-softwarearchitektur.drawio` (UML-Komponentendiagramm, echte UML-Notation statt C4), `sequenzdiagramm-freigabe.drawio` (Ablauf des wöchentlichen KI-Essensvorschlags inkl. Alt-Zweig „nicht berechtigt"). Jede `.drawio`-Datei hat eine passende `.png`-Vorschau (Ausnahme: `uml-softwarearchitektur.drawio` auf Wunsch ohne PNG). |
| `architektur/schichtenmodell.drawio` | Klassisches 5-Schichten-Modell (Präsentation → Zugriff/Sicherheit → Anwendung → Integration/Datenhaltung → externe Dienste), nur `.drawio`, kein PNG. |
| `praesentation/` | `FamilyHub-AI-Projektstand.pptx` – 7-seitige Statuspräsentation (Titel, Ausgangslage, Rollenmodell, Architektur, Figma-UI-Analyse, Nächste Schritte, Diskussion). Erzeugt per PowerPoint-COM-Automatisierung (siehe Hinweis unten), nicht mit dem üblichen pptxgenjs-Skill. |

Unter `C:\Users\jneugart\IdeaProjects\FamilyHub-UI\` (ebenfalls außerhalb des
Git-Repos): Kopie des Figma-Make-Exports „FamilyHub AI High Fidelity UI"
(React 19 + Vite + Tailwind 4 + TypeScript), in die unser Rollenmodell
integriert wurde (`src/roles/index.ts`, `src/roles/ActorContext.tsx`):
5-Rollen-Modell statt der ursprünglichen 2 Rollen (Mom/Dad/Child), automatischer
Altersübergang (Emma, 16, wird dadurch korrekt „Jugendlicher" statt „Child"),
funktionierender Familien-Umschalter in der Sidebar, Freigabe-Workflow in
Essensplanung (`MealPlanning.tsx`) und KI-Assistent (`AIAssistant.tsx`).
`Shopping.tsx` (Einkaufsliste) hat den Freigabe-Workflow noch **nicht** –
ein Integrationsversuch dort wurde probeweise gemacht und auf Nutzerwunsch
wieder verworfen, der Code ist also unverändert im Originalzustand.

Alle Diagramme wurden vor Auslieferung gerendert und visuell geprüft (per
draw.io-Viewer in headless Edge). Die `.drawio`-Dateien wurden inzwischen auch
erfolgreich in der draw.io-Desktop-App geöffnet (erkennbar an den dabei
entstehenden `.bkp`-Sicherungsdateien im `architektur/`- und `uml/`-Ordner).

### Figma-Anbindung (abgeschlossen, Stand 2026-09-22)

Es existiert ein fertiges High-Fidelity-UI in Figma Make
(„FamilyHub AI High Fidelity UI"). Der Zugriff darauf per Claude Code läuft
jetzt vollständig:

- **Bekannter Anthropic-Bug:** Der reservierte Marketplace-Name
  `claude-plugins-official` ist über die normale Installation
  (`claude plugin install figma@claude-plugins-official`) nicht erreichbar
  (siehe [GitHub Issue #22310](https://github.com/anthropics/claude-code/issues/22310),
  Stand: offen/„duplicate", kein offizieller Fix).
- **Workaround:** Marketplace direkt vom GitHub-Repo hinzufügen statt über den
  kaputten reservierten Namen:
  `claude plugin marketplace add anthropics/claude-plugins-official`, danach
  `claude plugin install figma@claude-plugins-official` — das funktioniert.
- **MCP-Server-Alternative** (falls kein Plugin gewünscht ist):
  `claude mcp add --transport http --scope user figma https://mcp.figma.com/mcp`
  (Änderung liegt in `C:\Users\jneugart\.claude.json`, nicht im Repo).
- **Autorisierung**: Login läuft über `/mcp` im interaktiven Chat (OAuth im
  Browser) — das kann nur der Nutzer selbst auslösen, nicht Claude automatisiert.
  Erfolgreich abgeschlossen und per `whoami`-Tool bestätigt (Handle
  `jakob.neugartner`).
- **Verifiziert:** `App.tsx` und `data.ts` direkt aus dem lebenden Figma-Make-File
  abgerufen und mit der lokalen Kopie in `FamilyHub-UI` verglichen — identisch,
  kein Drift.
- **Wichtige Einschränkung:** Die schreibenden Figma-MCP-Tools (`use_figma`,
  `generate_figma_design`) unterstützen laut eigener Doku ausdrücklich nur
  reguläre Design-Dateien (`/design/`), FigJam und Slides — **Figma-Make-Dateien
  (`/make/`) sind explizit ausgeschlossen**, sowohl beim Schreiben als auch bei
  `get_screenshot`/`get_metadata`. Lesen von Make-Dateien geht nur über
  `get_design_context` (liefert Links auf die Quelldateien). Das Make-File selbst
  lässt sich also nur über Figma Make direkt bearbeiten, nicht über diese
  Anbindung.

### Backend-Technologie (entschieden, Stand 2026-09-23)

| Thema | Entscheidung |
|---|---|
| Sprache | Java 25 |
| Framework | Spring Boot 4.1 (Spring Web MVC, Bean Validation) |
| Build | Maven über den Maven Wrapper (`mvnw`), keine Maven-Installation nötig |
| Struktur | Maven-Standard: `src/main/java/de/familyhub/`, Startklasse `Main.java` |
| Datenbank | MongoDB, lokal als ZIP ohne Admin-Rechte installiert, Zugriff über Spring Data MongoDB |
| Tests | JUnit 5 und MockMvc, eingebettete MongoDB (Flapdoodle) für Datenbanktests |
| Termin-Zuordnung | genau ein Familienmitglied pro Termin, wie im Figma-UI |
| Noch offen | springdoc-openapi (Swagger UI) ja/nein, Lombok (Empfehlung: weglassen) |

Redis wurde als Hauptdatenbank verworfen: im Kern ein Cache, Abfragen nach
Zeitraum und Person wären mühsam, und es läuft nicht nativ auf Windows. Später
denkbar als Cache für externe APIs oder für die Echtzeit-Einkaufsliste.

### Entwicklungsplan: Kalender-MVP (Branch `feature/kalender-mvp`)

Ziel: Backend, das Familienmitglieder und Termine verwaltet und per REST an das
React-UI liefert (User Story 1, C4-Seite `C3_Component_MVP`: Kalender API,
Termin API, Familienverwaltung API). **Nicht enthalten:** externe APIs
(Google Kalender, Messenger, Fahrzeiten, Müllabfuhr), Anmeldung, Rollen und
Rechte, KI.

| Phase | Inhalt | Status |
|---|---|---|
| 1 | Spring-Boot-Grundgerüst mit Maven Wrapper, `GET /api/health` | erledigt (2026-09-23) |
| 1b | MongoDB als ZIP in einen Benutzerordner installieren, Startskript `scripts/start-mongodb.ps1` | erledigt (2026-09-23) |
| 2 | Datenmodell `FamilyMember` (id, name, color) und `CalendarEvent` (id, title, start, end, memberId, category, location, description) mit Validierung | offen |
| 3 | Spring Data MongoDB, Repositories, Beispieldaten aus dem Figma-UI beim ersten Start | offen |
| 4 | REST: `/api/members` und `/api/events?from=&to=&memberId=` (CRUD), Fehlerantworten, CORS für `localhost:5173` | offen |
| 5 | optional: Terminüberschneidungen erkennen (`conflict: true`) | offen |
| 6 | `FamilyHub-UI` an das Backend anbinden (braucht Node.js) | offen |

Starten: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`), dann
`http://localhost:8080/api/health`. Tests: `./mvnw test`.

MongoDB starten: `powershell -ExecutionPolicy Bypass -File scripts/start-mongodb.ps1`
(lauscht nur auf `127.0.0.1:27017`, Daten in `%LOCALAPPDATA%\FamilyHub\mongodb\data`,
beenden mit Strg+C). Datenbank ansehen: `mongosh mongodb://127.0.0.1:27017`.

### Entwicklungswerkzeuge (installiert am 2026-09-23, ohne Admin-Rechte)

Alle unter `%LOCALAPPDATA%\Programs` und im **Benutzer-PATH** eingetragen (neue
Terminals bzw. VS Code nach Neustart finden sie automatisch):

| Werkzeug | Version | Ordner |
|---|---|---|
| Java JDK (Temurin) | 25.0.2 | war bereits installiert |
| Maven | 3.9.x | kommt über den Maven Wrapper, keine Installation |
| MongoDB Server | 8.0.32 | `Programs\mongodb` |
| mongosh | 2.12.0 | `Programs\mongosh` |
| Node.js (LTS) | 24.21.0 | `Programs\nodejs` |
| pnpm | 10.34.3 (Version aus `FamilyHub-UI/.mise.toml`) | global in `Programs\nodejs` |

Prüfsummen von MongoDB und Node.js wurden gegen die offiziellen Werte geprüft.
`FamilyHub-UI` lässt sich damit installieren und bauen (`pnpm install`,
`pnpm build`, `tsc --noEmit` ohne Fehler); im Browser wurde es noch nicht angesehen
(`pnpm dev`, dann `http://localhost:5173`).

### Offene nächste Schritte

- Kalender-MVP gemäß Entwicklungsplan oben umsetzen.
- Aus der ursprünglich vorgeschlagenen Diagrammliste fehlen noch: Sequenzdiagramm
  Messenger/Sprache (Identität → Rolle), Zustandsdiagramm für den Vorschlags-
  Status, ER-Diagramm der lokalen Datenbank, Deployment-Diagramm, Roadmap/
  MVP-Abgrenzung als Diagramm.
- Von den 4 Entscheidungsfragen aus Abschnitt 7 sind 3 geklärt (Stand
  2026-09-22); offen sind noch „Jugendlicher im MVP" (Klärung mit
  Projektleiter am 2026-09-23) und die Messenger-Berechtigung für
  Jugendliche.
- `FamilyHub-UI` einmal im Browser prüfen (`pnpm dev`); Build und Typprüfung
  laufen bereits fehlerfrei.

### Wichtige Hinweise für die Fortsetzung

- **Der Prototyp-Ordner (`FamilyHub-Rollenkonzept-Prototyp`) und der UI-Ordner
  (`FamilyHub-UI`) liegen absichtlich außerhalb dieses Git-Repos** und werden
  nicht gepusht — sie dienen nur als lokaler Konzeptnachweis, bis über die
  tatsächliche Projektstruktur und Technologie entschieden ist.
- Auf dem Branch `feature/kalender-mvp` wurde `src/Main.java` nach
  `src/main/java/de/familyhub/Main.java` verschoben und zur Spring-Boot-Startklasse.
  Der Branch `feature/backend-grundgeruest-main` enthält eine ältere Variante mit
  Rollen-Enum; lokale, unkommitierte Stände von `Main.java` liegen im Git-Stash.
- **Node.js, pnpm, MongoDB und mongosh sind seit 2026-09-23 installiert**
  (siehe "Entwicklungswerkzeuge"). Python und Docker gibt es weiterhin nicht.
  Die bestehende PowerPoint-Präsentation wurde damals per COM-Automatisierung
  über die lokale PowerPoint-App erzeugt, weil Node.js noch fehlte.
- **PowerShell 5.1 entfernt doppelte Anführungszeichen** in Argumenten an
  Programme (z. B. `mongosh --eval '...'`). JavaScript für mongosh daher ohne
  innere `"` schreiben oder als Datei übergeben.
- Diese README wird nach Abschluss größerer Arbeitsschritte fortgeschrieben;
  sie ist der einzige Ort, der garantiert zwischen Chat-Sitzungen erhalten
  bleibt und gepusht wird.
