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

Danach **http://localhost:5173** öffnen.

Das Backend wird unter `http://localhost:8080` erwartet. Eine andere Adresse
lässt sich in einer Datei `frontend/.env.local` setzen:

```
VITE_API_URL=http://localhost:9090
```

Weitere Befehle: `pnpm build` (Produktions-Build nach `dist/`),
`pnpm exec tsc --noEmit` (Typprüfung).

## Was ans Backend angeschlossen ist

| Bereich | Datenquelle |
|---|---|
| Kalender (Monat, Woche, Tag), Personenfilter | Backend: `/api/members`, `/api/events` |
| Termine anlegen, ändern, löschen | Backend, Formular `src/components/EventFormModal.tsx` |
| Dashboard: Mini-Kalender und „Heute“ | Backend |
| Aufgaben, Punkte, Einkauf, Essen, Nachrichten, Profile, Wetter, Müllabfuhr | noch feste Beispieldaten in `src/components/data.ts` |

Fahrzeiten und Konflikt-Markierungen liefert das Backend noch nicht, sie werden
daher im Kalender nicht angezeigt. Das UI rechnet weiterhin mit einem festen
„Heute“ (21.09.2026), passend zu den Beispieldaten.

## Aufbau

- `src/calendar/api.ts`: Aufrufe an das Backend, Fehler als `ApiError` mit den Feldfehlern des Backends
- `src/calendar/CalendarDataContext.tsx`: lädt Mitglieder und Termine einmal und stellt sie Kalender und Dashboard bereit
- `src/components/`: die Seiten (Dashboard, Calendar, Tasks, Rewards, Shopping, MealPlanning, AIAssistant, Messenger, Profiles)
- `src/roles/`: Rollen- und Berechtigungsmodell (Prototyp, noch nicht mit dem Backend verbunden)

Styling mit Tailwind CSS v4 über `@tailwindcss/vite`, keine separate
Tailwind-Konfiguration.
