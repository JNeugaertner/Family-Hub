# FamilyHub Frontend

React-Oberfläche von FamilyHub AI. Ursprung ist der Figma-Make-Export
„FamilyHub AI High Fidelity UI“, seitdem eigenständig weiterentwickelt.

## Starten

Voraussetzungen: Node.js 24 und pnpm 10 (`npm install -g pnpm@10.34.3`).
Backend und MongoDB müssen laufen (siehe README im Hauptordner).

```bash
cd frontend
pnpm install
pnpm dev
```

Danach **http://localhost:5173** öffnen und mit einem Beispielkonto anmelden
(z. B. `sarah`, Passwort `familyhub`; alle Konten stehen im README im
Hauptordner und als Hinweis auf der Anmeldeseite). Ist die Datenbank leer und
sind die Beispieldaten abgeschaltet, zeigt die Seite stattdessen „Familie
einrichten“ für den ersten Administrator.

Der Dev-Server leitet alle Aufrufe unter `/api` an das Backend auf
`http://localhost:8080` weiter (`vite.config.ts`). So laufen Oberfläche und API
unter derselben Adresse, und Anmelde- und CSRF-Cookie funktionieren ohne
Cross-Origin-Einstellungen. Läuft das Backend auf einem anderen Port, das
Proxy-Ziel in `vite.config.ts` anpassen.

Weitere Befehle: `pnpm build` (Produktions-Build nach `dist/`),
`pnpm exec tsc --noEmit` (Typprüfung).

## Was ans Backend angeschlossen ist

| Bereich | Datenquelle |
|---|---|
| Anmelden, Abmelden, Familie einrichten | Backend: `/api/auth/...` (Sitzungs-Cookie) |
| Kalender (Monat, Woche, Tag), Personenfilter | Backend: `/api/members`, `/api/events` |
| Termine anlegen (für eine oder mehrere Personen, auch „Ganze Familie“), ändern, löschen, Vorschläge freigeben oder ablehnen | Backend, Formular `src/components/EventFormModal.tsx` |
| Dashboard: Mini-Kalender und „Heute“ (nur freigegebene Termine) | Backend |
| Profile: Mitglieder, Rollen, Einzelrechte, Freigaben für Gäste, eigenes Passwort | Backend: `/api/members`, `/api/roles`, `/api/settings`, `/api/auth/password` |
| Aufgaben: anlegen, abhaken, bestätigen oder zurückgeben; Dashboard „Dringende Aufgaben“ | Backend: `/api/tasks` |
| Punkte: Punktestände, Rangliste, Historie, Belohnungsanimation; Dashboard „Familienpunkte“ | Backend: `/api/points` |
| Erfolge: Fortschritt je Kind, Animation bei neuen Erfolgen; Anpassen (Admins) | Backend: `/api/achievements` |
| Belohnungen: Shop, einlösen, zurückziehen; Genehmigen/Ablehnen und Belohnungen verwalten (Admins) | Backend: `/api/rewards`, `/api/redemptions` |
| Google Kalender verbinden, Kalender auswählen, abgleichen, trennen (Karte unter „Familie“) | Backend: `/api/google` |
| Einkaufsliste: hinzufügen, vorschlagen, abhaken, bearbeiten, Vorschläge übernehmen; Dashboard „Einkaufsliste“ | Backend: `/api/shopping` |
| Essensplan: Wochenplan, Gerichte-Sammlung, Wünsche der Kinder, Zutaten auf die Einkaufsliste; Dashboard „Mahlzeiten heute“ | Backend: `/api/meals`, `/api/dishes` |
| Wetter mit Kleidungsempfehlung (Dashboard); Wohnort einstellen (Karte unter „Familie“, Admins) | Backend: `/api/weather` |
| Nachrichten, Müllabfuhr | noch feste Beispieldaten in `src/components/data.ts` bzw. in der jeweiligen Seite |

Fahrzeiten und Konflikt-Markierungen liefert das Backend noch nicht, sie werden
daher im Kalender nicht angezeigt. Kalender und Dashboard rechnen mit dem
heutigen Datum; Beispieltermine und -aufgaben legt das Backend relativ zum
heutigen Datum an.

**Übersicht:** Ein Klick auf einen Termin unter „Heute“ öffnet den Kalender in der
Tagesansicht dieses Tages, ein Klick auf eine dringende Aufgabe die Aufgabenseite;
der Termin bzw. die Aufgabe leuchtet dort kurz auf (`src/navigation/focus.ts`,
Klasse `focus-flash`; bei „Bewegung reduzieren“ ein ruhiger Rahmen). Wer die
Einkaufsliste bearbeiten darf, hakt Artikel direkt in der Übersicht ab; gerade
Abgehaktes bleibt durchgestrichen stehen und lässt sich zurücknehmen.

**Profil-Overlay:** Ein Klick auf einen Familien-Kreis in der Seitenleiste oder auf
einen Avatar an Terminen und Aufgaben öffnet ein kleines Profil (Rolle, Farbe,
Alter, bei Kindern und Jugendlichen Punkte und Erfolge mit dem nächsten Ziel).
Punkte und Erfolge erscheinen nur, wenn die angemeldete Person sie sehen darf
(Kinder nur die eigenen, Gäste keine). Esc, ein Klick daneben oder Scrollen
schließt es (`src/profiles/`).

**Aufgaben und Punkte:** Eltern (Administratoren) legen Aufgaben mit Punkten an.
Das Kind hakt ab, die Aufgabe wartet dann auf Bestätigung. Erst wenn ein
Administrator bestätigt, werden die Punkte gutgeschrieben, genau einmal. Beim
nächsten Anmelden sieht das Kind eine kurze Animation für neue Punkte.
Jugendliche legen sich eigene Aufgaben ohne Punkte an; Aufgaben, die ihnen die
Eltern zuweisen, können sie nur abhaken. Kinder sehen nur ihre eigenen Aufgaben
und Punkte, Gäste keine. Wer eine Aufgabe ändern darf, sieht an der Karte einen
Stift (auch ein Klick auf die Karte öffnet das Formular); bestätigte Aufgaben
lassen sich nur ansehen. Administratoren räumen mit „Erledigte löschen“ in der
Spalte Done auf: Das entfernt bestätigte Aufgaben und erledigte ohne Punkte,
wartende Bestätigungen bleiben stehen, die Punkte-Historie bleibt erhalten.

**Bonus-Aufgaben:** Eltern legen Aufgaben ohne feste Person an (Punkte Pflicht,
Frist optional, auf Wunsch wiederkehrend). Sie stehen oben auf der Aufgabenseite
im Bereich „⭐ Bonus-Aufgaben“; Kinder und Jugendliche übernehmen sie mit einem
Klick (wer zuerst kommt). Danach stehen sie mit „⭐ Bonus“ im Board der Person
und laufen wie jede Aufgabe: abhaken, Eltern bestätigen, Punkte. Zurückgeben geht,
solange sie nicht erledigt sind. Wiederkehrende sind nach der Bestätigung wieder
offen.

**Belohnungen:** Kinder und Jugendliche lösen im Belohnungsshop Punkte für
sich selbst ein; die Punkte werden sofort abgezogen und die Einlösung wartet
auf die Eltern. Eltern genehmigen oder lehnen (mit optionalem Grund) ab; beim
Ablehnen und beim Zurückziehen einer offenen Einlösung kommen die Punkte
zurück. Eltern können auch direkt für ein Kind einlösen, das gilt dann sofort
als genehmigt. Je Belohnung ist einstellbar, ob sie mehrfach einlösbar ist.
Offene Genehmigungen zählt ein Hinweis an „Belohnungen“ in der Seitenleiste.

**Erfolge:** Kinder und Jugendliche erreichen automatisch Erfolge, z. B. „Erste
Aufgabe“, „5 Schulaufgaben“, „7 Tage hintereinander“ oder „200 Punkte
verdient“. Gezählt werden bestätigte Aufgaben ab Einführung der Erfolge, nicht
rückwirkend. Jeder Erfolg bringt einmalig Bonuspunkte. Beim nächsten Öffnen
erscheint nach „Neue Punkte“ die Animation „Neuer Erfolg“. Eltern können im
Tab „Erfolge“ einzelne Erfolge abschalten und Ziel und Bonus anpassen.

**Einkaufsliste:** Eltern und Jugendliche setzen Artikel direkt auf die Liste,
haken ab und bearbeiten. Kinder sehen die ganze Liste und schlagen Artikel vor;
ihre Vorschläge sehen nur sie selbst und die Eltern, die sie übernehmen oder
ablehnen (Zähler an „Einkauf“). Abgehakte Artikel bleiben stehen, bis jemand
„Abgehakte entfernen“ klickt.

**Essensplan:** Der Wochenplan hat Frühstück, Mittagessen, Abendessen und
Snacks; je Mahlzeit steht ein Gericht aus der Gerichte-Sammlung oder Freitext.
Eltern und Jugendliche planen und pflegen die Sammlung (Gerichte mit Zutaten,
Menge und Kategorie). Kinder sehen den Plan und wünschen sich etwas; der Wunsch
steht gestrichelt im Plan, nur die Eltern übernehmen ihn (er ersetzt dann den
bisherigen Eintrag) oder lehnen ab (Zähler an „Essensplan“). Mit dem
Einkaufswagen an einem Gericht oder „Zutaten der Woche auf die Einkaufsliste“
kommen die Zutaten auf die Einkaufsliste; was dort schon offen steht, wird nicht
doppelt angelegt.

**Google Kalender:** Termine aus Google tragen im Kalender und im Dashboard ein
„G“ und lassen sich nur ansehen; geändert werden sie in Google. Ganztägige
Termine stehen in der Wochenansicht oben am Tag und gelten an jedem ihrer
Tage. Nach der Anmeldung bei Google leitet das Backend auf
`http://localhost:5173/?google=verbunden|abgebrochen|fehler` zurück; die
Oberfläche öffnet dann die Profilseite mit einer Meldung. Einrichtung der
Zugangsdaten: siehe README im Hauptordner.

## Rollen in der Oberfläche

Welche Rechte jemand hat, liefert das Backend nach der Anmeldung
(`GET /api/auth/me`). Die Oberfläche blendet damit nur Schaltflächen aus oder
zeigt Formulare schreibgeschützt; geprüft wird jede Aktion im Backend.

| Rolle | Kalender | Profile |
|---|---|---|
| Administrator | alles, auch private Termine; gibt Vorschläge frei | Mitglieder, Rollen, Einzelrechte und Gäste-Freigaben verwalten |
| Jugendliche | eigene Termine direkt, mit anderen Beteiligten als Vorschlag | ansehen, eigenes Passwort |
| Kind | nur ansehen | ansehen, eigenes Passwort |
| Gast | nur freigegebene Kategorien ansehen | ansehen, eigenes Passwort |

Ein Termin kann mehrere Beteiligte haben; „Ganze Familie“ wählt alle außer
Gästen. Gemeinsame Termine haben im Kalender eine eigene Farbe (Indigo, bei
keinem Mitglied vergeben; Legende „Mehrere Personen“ beim Filter), der
Personenfilter findet sie für jede beteiligte Person. Als „eigener“ Termin zählt nur einer, an dem man allein
beteiligt ist. Private Termine sehen nur die Beteiligten, wer sie angelegt hat,
und Administratoren. Offene Vorschläge sehen nur die vorschlagende Person und
Administratoren; sie sind im Kalender gestrichelt mit ⏳ markiert.

## Aufbau

- `src/api/client.ts`: gemeinsamer Aufruf ans Backend (Cookies, CSRF-Header, Fehler als `ApiError` mit Feldfehlern)
- `src/auth/`: Anmeldung (`AuthContext` mit `useAuth()`/`useMe()`), Anmelde- und Einrichtungsseite
- `src/calendar/`: Termin-API, `CalendarDataContext` (lädt Mitglieder und Termine für Kalender und Dashboard), `permissions.ts` (was die angemeldete Person im Kalender darf), `eventStyle.ts` und `ParticipantAvatars.tsx` (Farbe und Beteiligte gemeinsamer Termine)
- `src/family/`: API für Mitglieder, Rollen und Einstellungen, Farbpalette
- `src/tasks/`: Aufgaben-API, `TaskDataContext` (lädt Aufgaben und Punktestände), `permissions.ts` (was die angemeldete Person bei Aufgaben darf)
- `src/points/`: Punkte-API, Punktestände je Kind, Belohnungsanimation und Hinweis auf neue Punkte
- `src/shopping/`: Einkaufslisten-API, `ShoppingDataContext`, Kategorien
- `src/meals/`: Essensplan-API, `MealDataContext` (angezeigte Woche, heutige Mahlzeiten, offene Wünsche, Gerichte), Mahlzeiten
- `src/achievements/`: Erfolge-API (Katalog, Fortschritt je Kind, Anpassen)
- `src/rewards/`: Belohnungs-API und `RewardDataContext` (Belohnungen und Einlösungen, lädt nach Änderungen auch die Punktestände neu)
- `src/weather/`: Wetter-API, Kachel für die Übersicht, Karte „Wohnort für das Wetter“ für die Profilseite
- `src/google/`: Google-API des Backends, Karte „Google Kalender“ für die Profilseite, „G“-Abzeichen
- `src/profiles/`: Profil-Overlay (`ProfileCard`) und der klickbare Avatar (`AvatarButton`)
- `src/roles/`: gemeinsame Typen des Rechtemodells (Modul, Aktion, Geltungsbereich) und `hasPermission()`
- `src/components/`: die Seiten (Dashboard, Calendar, Tasks, Rewards, Shopping, MealPlanning, AIAssistant, Messenger, Profiles)

Styling mit Tailwind CSS v4 über `@tailwindcss/vite`, keine separate
Tailwind-Konfiguration.
