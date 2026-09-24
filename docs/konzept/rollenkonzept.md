# Rollen- und Berechtigungskonzept

Fachliche Basis für die spätere Umsetzung von Anmeldung, Rollen und Rechten.
Noch keine technische Implementierung.

**Maßgebliche Übersicht:** [rechtekonzept.drawio](rechtekonzept.drawio). Die Matrix
dort beruht auf der finalen Fassung des Rollenkonzept-Dokuments (22.09.2026) und
ergänzt Zusatzregeln, Freigabe-Workflow und die Umsetzung laut C4-Diagramm.
Weitere Diagramme: [Use Cases](usecase-diagramm.drawio),
[Klassendiagramm](klassendiagramm-rollenmodell.drawio),
[Sequenz Freigabe](sequenzdiagramm-freigabe.drawio).

## 1. Ausgangslage

- **PoC (Kap. 5)** skizziert nur 3 grobe Rollen: Eltern, Kinder, Agent. Das reicht
  als Leitplanke, ist aber zu grob für eine Familie mit bis zu 5 Kindern
  unterschiedlichen Alters.
- **Rollenkonzept-Dokument** ist granularer (Administrator, Jugendlicher, Kind,
  Gast, KI-Agent) und deckt Fälle ab, die im PoC nur implizit vorkommen, etwa
  Großeltern oder Babysitter als Gast und ältere Kinder mit mehr Eigenständigkeit.
- **Lücken in beiden Dokumenten**, die dieses Konzept schließt:
  - mehrere gleichberechtigte Administratoren
  - technische Struktur von Berechtigungen (eigene Daten, Familiendaten,
    freigegebene Daten)
  - Freigabe-Workflow für KI-Vorschläge
  - Zuordnung von Messenger- und Sprachkanal-Identitäten zu einer Rolle

## 2. Rollen

| Rolle | Entspricht PoC | Typische Nutzer |
|---|---|---|
| Administrator | „Eltern" | beide Erwachsene, gleichrangig |
| Jugendlicher | Erweiterung von „Kinder" | Kinder ca. 13–17 |
| Kind | „Kinder" | Kinder im Kindergarten- und Grundschulalter |
| Gast | nicht im PoC | Großeltern, Babysitter |
| KI-Agent | „Agent" | Systemakteur, kein Mensch |

Kind und Jugendlicher sind getrennt, weil ein 5-Jähriger und ein 16-Jähriger
sehr unterschiedliche Autonomie brauchen.

## 3. Architekturprinzip: Rollen als Berechtigungsbündel

- **Berechtigung = Tripel** aus *Modul* (z. B. Kalender), *Aktion* (z. B. Erstellen)
  und *Geltungsbereich* (eigene Daten, Familiendaten, freigegebene Daten).
- Die 5 Rollen sind **Vorlagen**, keine festen Werte. Der Administrator kann laut
  Rollenkonzept Rollen erstellen, bearbeiten und zuweisen.
- **Einzelrechte pro Person** sind möglich, z. B. darf ein 11-jähriges Kind
  zusätzlich die Einkaufsliste bearbeiten, ohne Jugendlicher zu werden.
- **Neue Module bringen eigene Berechtigungen mit**, ohne das Rollenmodell zu ändern.

## 4. Berechtigungsmatrix (ursprünglicher Vorschlag vom 18.09.2026)

Stand vor der finalen Fassung des Rollenkonzepts. Bei Abweichungen gilt
[rechtekonzept.drawio](rechtekonzept.drawio).

| Modul | Administrator | Jugendlicher | Kind | Gast | KI-Agent |
|---|---|---|---|---|---|
| Familie & Rollen | Vollzugriff | – | – | – | – |
| Kalender | Vollzugriff | eigene Termine anlegen/ändern/löschen, Familientermine vorschlagen | eigene ansehen | freigegebene ansehen | lesen (Konfliktprüfung), keine Änderung |
| Aufgaben & Haushalt | Vollzugriff | eigene verwalten, Zusatzaufgaben übernehmen | ansehen, abhaken | freigegebene ansehen | vorlesen, erinnern, Zuordnung vorschlagen |
| Punkte & Belohnungen | vergeben, korrigieren, Regeln ändern | eigene und Familienstand einsehen | eigene einsehen | kein Zugriff | kein Zugriff |
| Einkaufsliste | Vollzugriff | bearbeiten | Wünsche hinzufügen | kein Zugriff | vorschlagen |
| Essensplanung & Rezepte | Vorschläge freigeben, Plan bearbeiten | Plan mitbearbeiten | Essenswünsche einreichen | kein Zugriff | Vorschläge erzeugen |
| Wetter & Tagesempfehlungen | ansehen | ansehen | ansehen | allgemeine Tagesinfo | abrufen, Empfehlung erzeugen |
| Müllabfuhr | Adresse konfigurieren | ansehen | ansehen | falls freigegeben | Termine ermitteln, Aufgabe vorschlagen |
| Messenger | Freigaben erteilen, Kanäle konfigurieren | *in Klärung* | eigene Wünsche einreichen | – | Verfügbarkeit prüfen, Antwortentwurf (kein Versand ohne Freigabe) |
| Sprachassistent (Tablet) | Konfiguration, Stimmprofile | Nutzung, eigenes Profil | Nutzung, eigenes Profil | – | Erkennung und Vorlesen, keine Entscheidung |
| Fahrzeiten | Konfiguration | ansehen | ansehen | – | Berechnung und Anzeige |
| Systemeinstellungen, externe Dienste | Vollzugriff | – | – | – | – |

## 5. Zusätzliche Mechanismen

1. **Freigabe-Workflow** (Entwurf/Vorschlag → Freigegeben/Abgelehnt) über alle
   Module, für KI-Essensvorschläge, Rezeptzutaten, Messenger-Antwortentwürfe und
   Terminvorschläge von Jugendlichen.
2. **Gast-Freigabe pauschal je Kategorie**: Gäste sehen ganze Kategorien, die ein
   Administrator freigibt. Ein „privat"-Flag verbirgt einzelne Einträge trotzdem.
3. **Altersübergang**: Geburtsdatum je Kind, ab konfigurierbarem Alter (Richtwert
   13) wird automatisch „Jugendlicher" vorgeschlagen, Eltern können überschreiben.
4. **Mehrkanal-Identität**: WhatsApp-/Telegram-Konto und Stimmprofil sind genau
   einem Familienmitglied zugeordnet, damit die Rechteprüfung auf jedem Kanal gilt.
5. **Gleichrangige Administratoren** ohne Hierarchie. Konflikte entscheiden
   Menschen, nicht das System (PoC 4.3).
6. **KI-Agent als Systemakteur**, getrennt von Nutzerkonten: nur lesen und
   Datensätze im Status „Vorschlag" anlegen.
7. **Rate-Limits und Bestätigungsregeln** (PoC 4.11) sind ein Querschnittsthema,
   kein Teil des Rollenmodells.

## 6. Spätere Erweiterungen

Weil Rollen Bündel aus Modul, Aktion und Geltungsbereich sind, lassen sich ohne
Umbau ergänzen: zweiter Haushalt bzw. Co-Elternteil, zeitlich befristete Rollen
(Babysitter), institutionelle Gäste wie Schule oder Kindergarten mit Schreibrecht
nur für bestimmte Termintypen.

## 7. Entscheidungen

**Getroffen (22.09.2026):**
- Beide Erwachsene sind gleichberechtigt Administrator.
- Gast-Freigabe pauschal je Kategorie.
- Altersübergang Kind → Jugendlicher automatisch ab Richtwert 13, manuell
  überschreibbar.

**Offen:**
- Ist „Jugendlicher" schon Teil des MVP? Abstimmung mit dem Projektleiter.
- Messenger-Berechtigung für Jugendliche: Die finale Fassung des Rollenkonzepts
  hat „Messenger-Anfragen vorbereiten" gestrichen. Absicht oder Versehen?
- Widersprüche zwischen den Dokumenten: User Story A.2 und die Familienverwaltung
  API im C4-Diagramm kennen nur Eltern, Kind und Agent. Bei User Story A.1
  (höchstens 2 Erwachsene, 5 Kinder) ist offen, ob Jugendliche als Kinder zählen
  und ob Gäste mitzählen.
