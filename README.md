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
| Messenger-Integration | Freigaben erteilen, Kanäle konfigurieren | Anfragen vorbereiten | Eigene Wünsche einreichen | – | Verfügbarkeit prüfen, Antwortentwurf (kein Versand ohne Freigabe) |
| Sprachassistent (Tablet) | Konfiguration, Stimmprofile | Nutzung, eigenes Profil | Nutzung, eigenes Profil | – | Ausführung (Erkennung/Vorlesen), keine Entscheidung |
| Fahrzeitberechnung | Konfiguration | Ansehen | Ansehen | – | Berechnung/Anzeige |
| Rollen & Berechtigungen | Vollzugriff | – | – | – | – |
| Systemeinstellungen/externe Dienste | Vollzugriff | – | – | – | – |

### 5. Zusätzliche Mechanismen, die das Rollenmodell braucht

1. **Freigabe-Workflow** (Status Entwurf/Vorschlag → Freigegeben/Abgelehnt) quer
   über Module – notwendig für KI-Essensvorschläge, Rezeptzutaten,
   Messenger-Antwortentwürfe und ggf. von Jugendlichen vorgeschlagene
   Familientermine.
2. **Sichtbarkeits-Flag pro Datensatz** (privat / Familie / für Gast freigegeben) –
   die Gast- und teilweise Kind-Rechte sind nicht rollenweit, sondern *pro
   Termin/Aufgabe* gesteuert („freigegebene Termine").
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

### 7. Offene Entscheidungen

- Soll „Jugendlicher" schon Teil des PoC-MVP sein? (Kap. 8 des PoC nennt diese
  Rolle nicht.)
- Sind beide Erwachsenen zwingend gleichberechtigt Administrator, oder braucht es
  eine abgestufte Erwachsenen-Rolle?
- Wie granular soll die Gast-Freigabe sein (pro Termin, pro Kategorie, pauschal)?
- Soll der Altersübergang Kind→Jugendlicher automatisiert oder rein manuell
  erfolgen?

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
| `uml/` | `klassendiagramm.drawio` (Domänenmodell), `usecase-diagramm.drawio` (5 Rollen × Anwendungsfälle inkl. KI-Agent), `c4-kontextdiagramm.drawio` (C4 Level 1), `c4-containerdiagramm.drawio` (Level 2), `c4-komponentendiagramm.drawio` (Level 3, Zoom in die Kernanwendung), `c4-codediagramm.drawio` (Level 4, Zoom in Rollenmodell/Freigabe-Workflow, basiert auf dem Prototyp-Code), `sequenzdiagramm-freigabe.drawio` (Ablauf des wöchentlichen KI-Essensvorschlags inkl. Alt-Zweig „nicht berechtigt"). Jede `.drawio`-Datei hat eine passende `.png`-Vorschau. |

Alle Diagramme wurden vor Auslieferung gerendert und visuell geprüft (per
draw.io-Viewer in headless Edge). Die `.drawio`-Dateien wurden inzwischen auch
erfolgreich in der draw.io-Desktop-App geöffnet (erkennbar an den dabei
entstehenden `.bkp`-Sicherungsdateien im `architektur/`- und `uml/`-Ordner).

### Figma-Anbindung (in Arbeit)

Es existiert ein fertiges High-Fidelity-UI in Figma Make
(„FamilyHub AI High Fidelity UI"). Damit Claude Code es lesen kann, wurde ein
Figma-MCP-Server eingerichtet:
- Plugin-Installation (`figma@claude-plugins-official`) schlug fehl, da kein
  Plugin-Marketplace konfiguriert ist.
- Stattdessen wurde der Remote-MCP-Server manuell eingetragen:
  `claude mcp add --transport http --scope user figma https://mcp.figma.com/mcp`
  (Änderung liegt in `C:\Users\jneugart\.claude.json`, nicht im Repo.)
- Status zuletzt: Server eingetragen, aber **Autorisierung steht noch aus**
  (`/mcp` zeigt „Needs authentication"). Der Login bei Figma muss vom Nutzer
  selbst im interaktiven Chat über `/mcp` erledigt werden.
- Fallback, falls die Make-Datei über MCP nicht lesbar sein sollte: Code-Export
  aus Figma Make oder PNG/PDF-Export der Screens.

### Offene nächste Schritte

- Figma-Autorisierung abschließen und das UI mit dem Rollenkonzept abgleichen
  (Rollen-Sichten, Freigabe-Ansicht für KI-Vorschläge, Tablet-Sprachfunktion).
- Aus der ursprünglich vorgeschlagenen Diagrammliste fehlen noch: Sequenzdiagramm
  Messenger/Sprache (Identität → Rolle), Zustandsdiagramm für den Vorschlags-
  Status, ER-Diagramm der lokalen Datenbank, Deployment-Diagramm, Roadmap/
  MVP-Abgrenzung als Diagramm.
- Die 4 offenen Entscheidungsfragen aus Abschnitt 7 sind weiterhin ungeklärt.
- Technologiewahl für die eigentliche Umsetzung (Web-Framework, Datenbank,
  Backend-Sprache) ist bewusst noch offen; alle C4-Container sind aktuell mit
  „Technologie offen" markiert.

### Wichtige Hinweise für die Fortsetzung

- **Der Prototyp-Ordner (`FamilyHub-Rollenkonzept-Prototyp`) liegt absichtlich
  außerhalb dieses Git-Repos** und wird nicht gepusht — er dient nur als
  lokaler Konzeptnachweis, bis über die tatsächliche Projektstruktur und
  Technologie entschieden ist.
- In diesem Repo selbst gibt es eine unveränderte, unfertige lokale Bearbeitung
  in `src/Main.java` (IntelliJ-Vorlage, TIP-Kommentare entfernt) — das ist nicht
  Teil dieser Konzeptarbeit und wurde bewusst nicht angefasst.
- Diese README wird nach Abschluss größerer Arbeitsschritte fortgeschrieben;
  sie ist der einzige Ort, der garantiert zwischen Chat-Sitzungen erhalten
  bleibt und gepusht wird.
