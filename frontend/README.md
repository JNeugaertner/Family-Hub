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
| Termine anlegen, ändern, löschen, Vorschläge freigeben oder ablehnen | Backend, Formular `src/components/EventFormModal.tsx` |
| Dashboard: Mini-Kalender und „Heute“ (nur freigegebene Termine) | Backend |
| Profile: Mitglieder, Rollen, Einzelrechte, Freigaben für Gäste, eigenes Passwort | Backend: `/api/members`, `/api/roles`, `/api/settings`, `/api/auth/password` |
| Aufgaben: anlegen, abhaken, bestätigen oder zurückgeben; Dashboard „Dringende Aufgaben“ | Backend: `/api/tasks` |
| Punkte: Punktestände, Rangliste, Historie, Belohnungsanimation; Dashboard „Familienpunkte“ | Backend: `/api/points` |
| Belohnungsshop, Erfolge, Einkauf, Essen, Nachrichten, Wetter, Müllabfuhr | noch feste Beispieldaten in `src/components/data.ts` bzw. in der jeweiligen Seite |

Fahrzeiten und Konflikt-Markierungen liefert das Backend noch nicht, sie werden
daher im Kalender nicht angezeigt. Kalender und Dashboard rechnen mit dem
heutigen Datum; Beispieltermine und -aufgaben legt das Backend relativ zum
heutigen Datum an.

**Aufgaben und Punkte:** Eltern (Administratoren) legen Aufgaben mit Punkten an.
Das Kind hakt ab, die Aufgabe wartet dann auf Bestätigung. Erst wenn ein
Administrator bestätigt, werden die Punkte gutgeschrieben, genau einmal. Beim
nächsten Anmelden sieht das Kind eine kurze Animation für neue Punkte.
Jugendliche legen sich eigene Aufgaben ohne Punkte an; Aufgaben, die ihnen die
Eltern zuweisen, können sie nur abhaken. Kinder sehen nur ihre eigenen Aufgaben
und Punkte, Gäste keine.

## Rollen in der Oberfläche

Welche Rechte jemand hat, liefert das Backend nach der Anmeldung
(`GET /api/auth/me`). Die Oberfläche blendet damit nur Schaltflächen aus oder
zeigt Formulare schreibgeschützt; geprüft wird jede Aktion im Backend.

| Rolle | Kalender | Profile |
|---|---|---|
| Administrator | alles, auch private Termine; gibt Vorschläge frei | Mitglieder, Rollen, Einzelrechte und Gäste-Freigaben verwalten |
| Jugendliche | eigene Termine direkt, für andere als Vorschlag | ansehen, eigenes Passwort |
| Kind | nur ansehen | ansehen, eigenes Passwort |
| Gast | nur freigegebene Kategorien ansehen | ansehen, eigenes Passwort |

Private Termine sehen nur die Person selbst, wer sie angelegt hat, und
Administratoren. Offene Vorschläge sehen nur die vorschlagende Person und
Administratoren; sie sind im Kalender gestrichelt mit ⏳ markiert.

## Aufbau

- `src/api/client.ts`: gemeinsamer Aufruf ans Backend (Cookies, CSRF-Header, Fehler als `ApiError` mit Feldfehlern)
- `src/auth/`: Anmeldung (`AuthContext` mit `useAuth()`/`useMe()`), Anmelde- und Einrichtungsseite
- `src/calendar/`: Termin-API, `CalendarDataContext` (lädt Mitglieder und Termine für Kalender und Dashboard), `permissions.ts` (was die angemeldete Person im Kalender darf)
- `src/family/`: API für Mitglieder, Rollen und Einstellungen, Farbpalette
- `src/tasks/`: Aufgaben-API, `TaskDataContext` (lädt Aufgaben und Punktestände), `permissions.ts` (was die angemeldete Person bei Aufgaben darf)
- `src/points/`: Punkte-API, Punktestände je Kind, Belohnungsanimation und Hinweis auf neue Punkte
- `src/roles/`: gemeinsame Typen des Rechtemodells (Modul, Aktion, Geltungsbereich) und `hasPermission()`
- `src/components/`: die Seiten (Dashboard, Calendar, Tasks, Rewards, Shopping, MealPlanning, AIAssistant, Messenger, Profiles)

Styling mit Tailwind CSS v4 über `@tailwindcss/vite`, keine separate
Tailwind-Konfiguration.
