# Roadmap

Wohin sich FamilyHub AI entwickeln soll, von Version 0.8 bis zu langfristigen
Ideen. Grundlage ist die Planung des Teams vom November 2026. Die Roadmap ist
eine Absicht, kein Versprechen: Reihenfolge und Umfang können sich ändern, wenn
Rückmeldungen aus der Familie oder der Community etwas anderes nahelegen. Was
tatsächlich in einer Version steckt, steht in [CHANGELOG.md](CHANGELOG.md).

**Stand:** 11.11.2026, Version 0.8.0 in Vorbereitung.

| Zeichen | Bedeutung |
|---|---|
| ✅ | umgesetzt (mit Version) |
| 🔄 | in Arbeit |
| ⬜ | geplant |
| 💭 | Idee, noch in Prüfung |

## Überblick

| Version | Schwerpunkt | Stand |
|---|---|---|
| [0.8](#v08--erweiterung-der-kernfunktionen) | Erweiterung der Kernfunktionen | fast fertig, Open Route Service folgt |
| [0.9](#v09--vorbereitung-auf-version-10) | Geburtstage, Feiertags-Countdowns, Tests und Doku vor 1.0 | geplant |
| [1.0](#v10--erstes-offizielles-release) | Erstes offizielles Release | geplant |
| [1.1](#v11--komfortfunktionen-und-familienerlebnis) | Komfortfunktionen, Wunschlisten | geplant |
| [1.5](#v15--plattformausbau) | Apps für Android und iOS | geplant |
| [2.0](#v20--freemium-modell) | Freemium-Modell | geplant |
| [2.1](#v21--werbung) | Werbung in der kostenlosen Version | Idee |
| [Danach](#langfristige-vision) | WGs, Lerngruppen, Kooperationen | Vision |

## v0.8 – Erweiterung der Kernfunktionen

Bestehende Bereiche werden erweitert und Komfortfunktionen kommen dazu, damit
der Alltag mit FamilyHub einfacher wird.

**Kalender**

- ✅ Vorlagen für häufige Termine (0.8)
- ✅ Wiederkehrende Termine (Serien) (0.7)
- ✅ Ganztägige Termine, auch über mehrere Tage (0.8)

**Aufgaben**

- ✅ Wiederholung im Rhythmus ab dem tatsächlichen Erledigungsdatum (0.7)
- ✅ Wiederkehrende Aufgaben, deren nächste Runde automatisch entsteht, für
  eine feste Person oder „Wer zuerst kommt“ (0.7)

**Weitere Funktionen**

- ✅ „Rückgängig“ für versehentlich gelöschte Termine, Aufgaben und
  Einkaufsartikel (0.8)
- ✅ Wiederkehrende Einkaufsartikel (0.7)

**Projekt**

- ✅ Einfacher Start für Nutzer (Docker, Startfenster per Doppelklick) und
  Entwickler (0.8)
- ✅ Überarbeitete README (0.8)
- ✅ Leichterer Einstieg für weitere Entwickler mit
  [CONTRIBUTING.md](CONTRIBUTING.md) (0.8)
- ✅ Regressionstests auf Knopfdruck (0.8)

**Open Route Service**

- 🔄 Integration überarbeiten und optimieren
- 🔄 Ortssuche und Fahrzeitberechnung verbessern
- 🔄 Bedienung vereinfachen
- 🔄 Fehler beheben und bestehende Funktionen erweitern

Die Überarbeitung von Open Route Service kommt mit einer späteren Version
(0.8.x oder 0.9).

## v0.9 – Vorbereitung auf Version 1.0

Die letzten geplanten Kernfunktionen kommen dazu, außerdem Qualitätssicherung,
Dokumentation und Optimierungen.

**Geburtstage**

- ⬜ Geburtsdatum im eigenen Profil hinterlegen
- ⬜ Daraus automatisch ein jährlicher Geburtstag im Familienkalender
- ⬜ Für alle Familienmitglieder sichtbar
- ⬜ Optisch von normalen Terminen abgehoben
- ⬜ Ändert sich das Geburtsdatum, ändert sich der Kalendereintrag mit

**Countdowns für Feiertage und besondere Anlässe**

- 🔄 Verbleibende Tage bis zu wichtigen Feiertagen
- 🔄 Anzeige auf der Übersicht und im Kalender
- 🔄 Neujahr, Ostern, Christi Himmelfahrt, Pfingsten, Tag der Deutschen
  Einheit, Weihnachten
- 🔄 Jedes Jahr automatisch aktualisiert

**Tests vor Version 1.0**

- ⬜ Funktionstests aller Kernfunktionen
- ⬜ Integrationstests für externe Schnittstellen und Dienste
- ⬜ Usability-Tests auf Desktop, Tablet und Handy
- ⬜ Tests auf Sicherheitslücken
- ⬜ Prüfung der verwendeten Open-Source-Lizenzen
- ⬜ Gefundene Fehler und Stabilitätsprobleme beheben

**Dokumentation**

- ⬜ README, Installationsanleitung und Projektbeschreibung
- ⬜ Changelog und GitHub-Dokumentation
- ⬜ Screenshots und Anwendungsbeispiele

## v1.0 – Erstes offizielles Release

Mit Version 1.0 wird aus dem Entwicklungsprojekt ein stabiler, öffentlich
nutzbarer Open-Source-Familienplaner.

**Enthaltene Kernfunktionen**

- ✅ Familienkalender mit Tages-, Wochen- und Monatsansicht
- ✅ Wiederkehrende Termine und Vorlagen für Termine
- ✅ Aufgaben mit Punktesystem
- ✅ Wiederkehrende Aufgaben
- ✅ Gemeinsame Einkaufsliste mit wiederkehrenden Artikeln
- ✅ Essensplan mit Gerichte-Sammlung
- ✅ Wetter
- ✅ Erinnerungen an die Müllabfuhr
- ✅ Familien-Chat
- ✅ Google Kalender
- 🔄 Telegram
- 🔄 Open Route Service (Fahrzeit)
- ✅ Rollen und Rechte
- ⬜ Geburtstage
- 🔄 Feiertags-Countdowns

**Nach dem Release**

- ⬜ Rückmeldungen der Nutzer sammeln
- ⬜ Fehler beheben und stabilisieren
- ⬜ Leistung verbessern
- ⬜ Bedienung verbessern
- ⬜ Weitere Funktionen nach den Wünschen der Community vorbereiten

## v1.1 – Komfortfunktionen und Familienerlebnis

Nach dem ersten Release stehen Komfortfunktionen im Mittelpunkt, die den
Familienalltag weiter vereinfachen.

**Wünsche zu Geburtstagen und Festen** 💭

Ob diese Funktion kommt, wird noch geprüft. Die Idee:

- Geburtstagswünsche im eigenen Profil hinterlegen
- Wünsche hängen automatisch am Geburtstag im Kalender
- Die Familie sieht sie direkt im Kalendereintrag
- Wünsche auch für Weihnachten, Ostern und andere Familienfeiern
- Geschenkideen an einer Stelle sammeln

**Weitere Verbesserungen**

- ⬜ Bestehende Funktionen und die Oberfläche verbessern
- ⬜ Häufig genutzte Abläufe erweitern
- ⬜ Rückmeldungen der Community umsetzen
- ⬜ Mehr Automatisierung in Kalender und Aufgaben

## v1.5 – Plattformausbau

Wenn die Browser-Version etabliert ist, kommt FamilyHub auf weitere Plattformen.

**Handy**

- ⬜ App für Android
- ⬜ App für iOS
- ⬜ Bessere Bedienung auf dem Handy
- ⬜ Push-Benachrichtigungen zu Terminen, Aufgaben und Erinnerungen

FamilyHub lässt sich schon heute im Browser „als App installieren“ (siehe
README).

**Open Source**

- ⬜ Ausführlichere Entwicklerdokumentation
- ⬜ Einfacherer Einstieg für neue Mitwirkende
- ⬜ Bessere Dokumentation der Schnittstelle (API)
- ⬜ Beiträge und Pull Requests aus der Community

**Weitere Verbesserungen**

- ⬜ Abgleich mit anderen Diensten verbessern
- ⬜ Mehr Benachrichtigungen
- ⬜ Weitere Automatisierungen und Komfortfunktionen
- ⬜ Leistung verbessern

## v2.0 – Freemium-Modell

Geld verdienen gehört nicht zu Version 1.0, soll das Projekt aber später
langfristig finanzieren.

**Kostenlos** bleiben alle wichtigen Kernfunktionen, damit FamilyHub auch ohne
Bezahlung vollen Nutzen bringt:

- Familienkalender, wiederkehrende Termine und Geburtstage
- Aufgaben und Einkaufsliste
- Essensplan
- Rollen und Rechte
- Bis zu 2 Erwachsene und 4 Kinder oder Jugendliche

**Premium** bringt zusätzliche Komfort- und Automatisierungsfunktionen:

- Beliebig viele Familienmitglieder
- KI-Assistent und Sprachassistent
- Wetter und Erinnerungen an die Müllabfuhr
- Abgleich mit Google Kalender
- WhatsApp
- Keine Werbung
- Zugang zu künftigen Premium-Funktionen

**Testphase:** einen Monat kostenlos mit allen Premium-Funktionen, danach
Wechsel zu Premium.

## v2.1 – Werbung

💭 Als zusätzliche Einnahme könnte die kostenlose Version Werbung zeigen, so
unauffällig wie möglich.

- **Mögliche Stellen:** Startseite, Übersicht, Einkaufsliste, Einstellungen
- **Nicht vorgesehen:** Video-Werbung, Vollbild-Anzeigen, Pop-ups und Werbung
  beim Anlegen von Terminen oder Bearbeiten von Aufgaben

## Langfristige Vision

Außer Familien könnten künftig auch andere Gruppen FamilyHub nutzen.

- **Wohngemeinschaften:** gemeinsame Einkaufslisten, Putzpläne, Aufgaben und
  Kalender, Organisation des WG-Alltags
- **Lerngruppen:** Prüfungs- und Projektplanung, Aufgaben, Erinnerungen an
  Abgabefristen, gemeinsame Termine, Gruppenarbeiten organisieren
- **Allgemein:** neue Funktionen nach Rückmeldungen der Community, regelmäßige
  Fehlerbehebungen, bessere Anbindungen, einfachere Bedienung

## Zukunftsideen

💭 Diese Ideen haben derzeit keine Priorität und werden erst bei einer größeren
Nutzerzahl interessant.

- **Supermärkte und Lieferdienste:** die Einkaufsliste direkt in den Warenkorb
  eines Partners übertragen und dort bestellen, mit Provision für erfolgreiche
  Bestellungen
- **Gesponserte Angebote im Essensplan:** Produkte und Rezepte von Partnern
  passend zu den geplanten Gerichten
