# Rollensystem: Anmeldung, Rollen und Rechte

**Ziel:** Jedes Familienmitglied meldet sich mit einem eigenen Konto an und sieht
und darf genau das, was seine Rolle laut [Rollenkonzept](../konzept/rollenkonzept.md)
vorsieht. Umgesetzt für die Familienverwaltung und den Kalender, im Backend und in
der Oberfläche.

**Nicht enthalten:** Rechteprüfung für Aufgaben, Einkauf, Essen und Punkte (diese
Module haben noch kein Backend), Zugang für den KI-Agenten, Messenger und
Sprachassistent.

## Entscheidungen (24.09.2026)

| Thema | Entscheidung |
|---|---|
| Rollen | alle 5 laut Rollenkonzept. Administrator, Jugendlicher, Kind und Gast melden sich an; KI-Agent ist als Rolle angelegt, hat aber noch keinen Zugang |
| Anmeldung | Benutzername und Passwort, danach Sitzungs-Cookie (Spring Security): HttpOnly, SameSite=Lax, 8 Stunden gültig, dazu CSRF-Schutz |
| Passwörter | mindestens 8 Zeichen, gespeichert nur als BCrypt-Hash |
| Kalender | Jugendliche verwalten eigene Termine direkt und schlagen Termine für andere vor; ein Administrator gibt Vorschläge frei oder lehnt sie ab. Kinder sehen nur |
| Sichtbarkeit | Kinder und Jugendliche sehen alles außer fremden privaten Terminen; Gäste nur Kategorien, die ein Administrator freigibt |
| Familiengröße | höchstens 2 Administratoren und 5 Kinder; Jugendliche zählen als Kinder, Gäste zählen nicht |
| Altersübergang | ein Kind mit Geburtsdatum wird ab 13 automatisch Jugendlicher (`familyhub.roles.teen-age`); je Person abschaltbar („Rolle nicht automatisch anpassen“) |

## Phasen

| Phase | Inhalt | Status |
|---|---|---|
| 1 | Konten: Benutzername, Passwort, Rolle, Geburtsdatum; Anmelden, Abmelden, Ersteinrichtung, eigenes Passwort ändern | erledigt |
| 2 | Rechteprüfung im Backend (Modul, Aktion, Geltungsbereich), Standardrechte je Rolle, Einzelrechte je Person | erledigt |
| 3 | Freigabe-Workflow für Termine, private Termine, Freigaben für Gäste | erledigt |
| 4 | Oberfläche: Anmeldeseite, Kalender je nach Rolle, Profilseite mit Mitglieder- und Rechteverwaltung | erledigt |
| 5 | Tests und Dokumentation | erledigt |

## Rechtemodell

Ein Recht besteht aus **Modul**, **Aktion** und **Geltungsbereich**, z. B.
`kalender / erstellen / eigen`.

- **Module:** familie, kalender, aufgaben, punkte, einkauf, essen, wetter, muell,
  fahrzeit, messenger, sprachassistent, system
- **Aktionen:** ansehen, erstellen, bearbeiten, loeschen, vorschlagen, freigeben, verwalten
- **Geltungsbereiche** von eng nach weit: `eigen`, `freigegeben`, `familie`. Ein
  weiterer Bereich schließt die engeren ein: Wer Familientermine bearbeiten darf,
  darf auch seine eigenen bearbeiten.

**Geltende Rechte** = Standardrechte der tatsächlichen Rolle, plus zusätzlich
vergebene, minus entzogene Einzelrechte. Administratoren haben immer alle Rechte,
Einzelrechte gelten für sie nicht. Die Standardrechte stehen in
`src/main/java/de/familyhub/permission/StandardRoles.java` und sind über
`GET /api/roles` abrufbar. Die Oberfläche bekommt die geltenden Rechte der
angemeldeten Person von `GET /api/auth/me`.

| Rolle | Kalender | Familie | Weitere Standardrechte (Auszug) |
|---|---|---|---|
| Administrator | alles | verwalten, Rollen und Rechte vergeben | alles |
| Jugendlicher | ansehen; eigene anlegen, ändern, löschen; für andere vorschlagen | ansehen | Aufgaben der Familie sehen, eigene verwalten; Einkauf bearbeiten; Essen mitplanen; Punkte der Familie sehen |
| Kind | ansehen | ansehen | eigene Aufgaben abhaken, eigene Punkte sehen, Einkaufs- und Essenswünsche |
| Gast | freigegebene Kategorien ansehen | ansehen | freigegebene Aufgaben, Wetter, Müllabfuhr |
| KI-Agent | ansehen, vorschlagen | ansehen | nur ansehen und vorschlagen, nie selbst anlegen oder freigeben |

**Schutz vor Rechteausweitung:** Das Einzelrecht „Familienmitglieder verwalten“
(`familie / verwalten`) erlaubt, Namen und Farben zu pflegen und
Nicht-Administratoren zu löschen. Für alles, was Rechte verschiebt, braucht es
zusätzlich „Rollen & Rechte verwalten“ (`system / verwalten`): neue Mitglieder,
Rollen und Einzelrechte, ein Geburtsdatum, das über den Altersübergang die Rolle
bestimmt, Benutzernamen und Passwörter anderer sowie Administratorkonten. Wer
„Rollen & Rechte verwalten“ hat, kann sich alle Rechte selbst geben und ist damit
praktisch Administrator.

## Kalender-Regeln

| | Administrator | Jugendlicher | Kind | Gast |
|---|---|---|---|---|
| Termine sehen | alle | alle außer fremden privaten | alle außer fremden privaten | nur freigegebene Kategorien, keine privaten |
| eigene Termine anlegen, ändern, löschen | ja | ja | nein | nein |
| Termine für andere anlegen | direkt | als Vorschlag | nein | nein |
| Vorschläge sehen | alle | eigene | nein | nein |
| Vorschläge freigeben oder ablehnen | ja | nein, nur eigene zurückziehen | nein | nein |

- **Eigene Termine** sind die, die der Person zugeordnet sind.
- **Vorschläge** (`status: proposed`) gelten erst nach der Freigabe (`approved`).
  Ablehnen löscht den Vorschlag. Das Dashboard zeigt nur freigegebene Termine.
- **Private Termine** (`private: true`) sehen nur die zugeordnete Person, wer den
  Termin angelegt hat, und Administratoren.
- **Freigaben für Gäste** stellt ein Administrator auf der Profilseite ein
  (`/api/settings`). Die Beispieldaten geben „Familie“ und „Schule“ frei, ohne
  Beispieldaten ist zunächst nichts freigegeben.
- Termine, die jemand nicht sehen darf, beantwortet das Backend mit **404**
  statt 403, damit nicht erkennbar ist, dass es sie gibt.

## REST-Schnittstelle

Ausprobieren: **http://localhost:8080/swagger-ui.html**. Dort zuerst unter
„Anmeldung“ `POST /api/auth/login` ausführen (z. B. `sarah` / `familyhub`),
danach funktionieren alle Aufrufe im selben Browser. Das CSRF-Token schickt die
Swagger UI selbst mit.

Neu mit dem Rollensystem:

| Methode und Pfad | Zweck | Wer |
|---|---|---|
| `GET /api/auth/status` | `setupRequired: true`, solange es noch kein Konto gibt | ohne Anmeldung |
| `POST /api/auth/setup` | ersten Administrator anlegen (`name`, `color`, `username`, `password`) | nur ohne vorhandene Mitglieder, sonst 409 |
| `POST /api/auth/login` | anmelden, **Formularfelder** `username` und `password`; Antwort wie `/me` | 401 bei falschen Daten |
| `POST /api/auth/logout` | abmelden | 204 |
| `GET /api/auth/me` | angemeldete Person mit Rolle, tatsächlicher Rolle und allen geltenden Rechten | angemeldet |
| `PUT /api/auth/password` | eigenes Passwort ändern (`currentPassword`, `newPassword`) | angemeldet; 400 bei falschem aktuellem Passwort |
| `GET /api/roles` | Rollen mit ihren Standardrechten | angemeldet |
| `GET /api/settings` | Einstellungen der Familie (`guestCategories`), dazu `teenAge`: ab welchem Alter ein Kind automatisch Jugendlicher wird (nur lesbar, aus `familyhub.roles.teen-age`) | angemeldet |
| `PUT /api/settings` | Freigaben für Gäste ändern | Administratoren |
| `POST /api/events/{id}/approve` | Vorschlag freigeben | Administratoren; 409, wenn es kein offener Vorschlag ist |
| `POST /api/events/{id}/reject` | Vorschlag ablehnen (löscht ihn) | Administratoren |

Änderungen an den bestehenden Endpunkten:

- **Alle** anderen Aufrufe brauchen eine Anmeldung, sonst **401**. Fehlen Rechte,
  antwortet das Backend mit **403**, Titel „Keine Berechtigung“ und einer
  Erklärung unter `detail`.
- **`/api/members`:** neue Felder `username`, `password` (nur beim Schreiben;
  beim Ändern bleibt ohne Passwort das bisherige), `role`, `birthDate`,
  `roleFixed`, `extraPermissions`, `revokedPermissions`; die Antwort enthält
  zusätzlich `effectiveRole`. Benutzername und Geburtsdatum sehen nur
  Administratoren und die Person selbst, Einzelrechte nur Administratoren.
  Verstöße gegen die Familiengröße meldet das Backend als Feldfehler `role`.
- **`/api/events`:** neue Felder `private`, `status` und `createdBy` (die letzten
  beiden setzt das Backend), Filter `?status=proposed`.

**CSRF:** Jede ändernde Anfrage (POST, PUT, DELETE) braucht den Header
`X-XSRF-TOKEN` mit dem Wert des Cookies `XSRF-TOKEN`, das das Backend bei jeder
Antwort setzt. Die Oberfläche macht das in `frontend/src/api/client.ts`
automatisch.

## Ausprobieren

1. MongoDB, Backend und Oberfläche starten (siehe [README](../../README.md)),
   **http://localhost:5173** öffnen. Alle Beispielkonten haben das Passwort `familyhub`.
2. Als `emma` anmelden (Jugendliche): Im Kalender einen Termin für Lily anlegen.
   Er wird zum Vorschlag und erscheint unter „Deine Vorschläge“.
3. Als `sarah` anmelden (Administratorin): Unter „Offene Vorschläge“ freigeben
   oder ablehnen. Auf der Profilseite Rechte von Lucas ändern, z. B. „Eigene
   Termine anlegen“, und die Freigaben für Gäste umstellen.
4. Als `lucas` (Kind) und `oma` (Gast) anmelden und vergleichen, was jeweils zu
   sehen ist. Der private „Book club“ ist nur für Sarah und Mike sichtbar.
5. Zurück zu den Beispieldaten: Datenbank löschen
   (`mongosh mongodb://127.0.0.1:27017/familyhub --eval "db.dropDatabase()"`) und
   das Backend neu starten.

## Tests

- Backend: Rechte je Rolle (`PermissionsTest`), Altersübergang (`RoleResolverTest`),
  Anmeldung und CSRF (`AuthControllerTest`, `CsrfCookieTest`), Familienverwaltung
  mit Limits und Schutz vor Rechteausweitung (`FamilyMemberControllerTest`),
  Kalender je Rolle mit Vorschlägen, privaten Terminen und Gast-Freigaben
  (`CalendarEventRoleTest`), Einstellungen und Rollenliste. Die Tests melden sich
  über `TestUsers.as(...)` an, ohne echtes Passwort.
- Oberfläche: mit allen Beispielkonten im Browser durchgespielt (Anmeldung,
  Sichtbarkeit je Rolle, Vorschlag und Freigabe, Einzelrechte, Gäste-Freigaben,
  Passwort ändern, abgelaufene Sitzung). Automatisierte Frontend-Tests gibt es
  noch nicht.

## Offen

- **KI-Agent:** Rolle und Rechte sind angelegt, ein Zugang (z. B. Dienstkonto mit
  Token statt Passwort) kommt mit dem KI-Modul.
- **Weitere Module:** Aufgaben, Einkauf, Essen und Punkte prüfen Rechte bisher
  nur in der Oberfläche; Vorschläge dort leben nur im Browser. Mit dem jeweiligen
  Backend wird die Prüfung wie beim Kalender serverseitig.
- **Eigene Rollen:** Laut Rollenkonzept können Administratoren Rollen anlegen.
  Umgesetzt sind die 5 Standardrollen plus Einzelrechte je Person.
- **Betrieb:** Für einen echten Betrieb fehlen HTTPS (Cookie mit `Secure`) und
  eine Begrenzung von Fehlversuchen beim Login. Die Beispieldaten mit dem
  Passwort `familyhub` dann abschalten (`familyhub.sample-data.enabled=false`).
- **Dokumente angleichen:** C4-Diagramm (Familienverwaltung API) und User Story
  A.2 kennen nur Eltern, Kind und Agent.
