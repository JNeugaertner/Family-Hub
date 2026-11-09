# Installation der FamilyHub-Regressionstests

Diese Anleitung beschreibt das Podman-Testlabor mit Cucumber, Playwright,
axe und nativen JUnit-Backendtests. Der sichtbare Testbrowser läuft auf einem
Linux-Desktop, der über den Webbrowser erreichbar ist.

## 1. Voraussetzungen

Auf dem Windows-Rechner werden benötigt:

- Git
- Podman bzw. Podman Desktop mit einer Linux-Maschine
- PowerShell
- Internetzugang beim ersten Build
- Freie Ports **6080** und **18080**

Für Images und Build-Caches mehrere GB freien Speicher einplanen. Für die
Podman-Maschine sind etwa 6 GB RAM empfehlenswert. Java, Maven, Node.js,
Python und Playwright müssen auf dem Windows-Host nicht installiert sein;
die benötigten Laufzeiten befinden sich in den Containern.

Installation prüfen:

```powershell
git --version
podman --version
podman machine list
```

Falls noch keine Podman-Maschine existiert:

```powershell
podman machine init
```

Eine vorhandene, gestoppte Maschine starten:

```powershell
podman machine start
podman info
```

Eine bereits laufende Maschine muss nicht erneut gestartet werden.

## 2. Dateien ablegen

Das Testprojekt kann als Ordner `regression` parallel zu `src` abgelegt werden:

```text
Family-Hub/
├── pom.xml
├── src/
│   └── test/java/de/familyhub/
└── regression/
    ├── app/
    ├── backend-tests/
    ├── data/
    ├── desktop/
    ├── features/
    ├── scripts/
    ├── steps/
    ├── support/
    ├── Containerfile
    ├── package.json
    ├── package-lock.json
    ├── start-lab.ps1
    └── run-regression.ps1
```

Beim Entpacken des Testpakets dessen **Inhalt** nach `regression` kopieren.
`package.json` und `start-lab.ps1` müssen direkt darin liegen. Das Paket
kann auch eigenständig außerhalb des Anwendungsrepositories verwendet werden.

PowerShell im Ordner `regression` öffnen. Aus dem Repository-Hauptordner:

```powershell
cd regression
```

Die vorhandenen Skripte erzeugen einen eigenen Checkout in `regression/source`
und verwenden Commit `fb02978db0e9e1d202ba32e92241f32cc34f8272`.
**Sie testen damit diesen festgelegten Quellstand, nicht automatisch den
aktuellen Branch des übergeordneten Repositorys.** Im Ordner `source` nicht
manuell entwickeln. Der Anwendungscode im übergeordneten Repository wird
vom normalen Startskript nicht geändert.

## 3. Testlabor installieren und starten

Im Ordner `regression` ausführen:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 start
```

Das Skript lädt den Quellstand, baut die Images und startet den Pod
`familyhub-e2e` mit folgenden Containern:

| Container | Aufgabe |
|---|---|
| `familyhub-e2e-db` | Separate MongoDB für Testdaten |
| `familyhub-e2e-backend` | Java-/Spring-Backend |
| `familyhub-e2e-frontend` | Gebautes Frontend mit Nginx |
| `familyhub-e2e-desktop` | Grafischer Desktop, Chromium und Testwerkzeuge |

Der erste Start kann wegen der Downloads und Builds einige Minuten dauern.
Ein vorhandener Pod wird beim erneuten Start wieder gestartet; die Images
werden dann nicht neu gebaut.

Anschließend öffnen:

- **Testlabor und Desktop:** <http://localhost:6080/>
- **Separate Testanwendung:** <http://localhost:18080/>
- **Backend-Healthcheck:** <http://localhost:18080/api/health>

Eine bestehende FamilyHub-Installation auf Port 8080 wird nicht verwendet.
Die Testumgebung ist ausschließlich an die lokale Rechneradresse gebunden.
Der passwortlose noVNC-Zugang ist für diese lokale Testumgebung vorgesehen.

## 4. Tests im grafischen Desktop starten

Auf der Startseite „Test-Desktop öffnen“ wählen. Im sichtbaren Terminal
einen Befehl eingeben und mit Enter bestätigen:

| Eingabe | Testumfang |
|---|---|
| Nur Enter | Alle 207 Cucumber-/Playwright-/axe-Szenarien |
| `smoke` | Kurzer Funktionstest |
| `backend` | Unicode-, Sonderzeichen- und Leerraum-API-Tests |
| `security` | Mit `@security` markierte Sicherheitsfälle |
| `audit` | Zusätzliche Injections und Barrierefreiheit |
| `a11y` | Barrierefreiheit mit axe und Tastatur-Anmeldung |
| `extended` | Mit `@extended` markierte Erweiterungstests |

Chromium führt die Oberflächentests sichtbar aus. API-Prüfungen rufen das
Backend direkt auf; dabei sind nicht für jeden Schritt Browseraktionen zu
sehen. Testergebnisse erscheinen im Terminal und im Cucumber-Bericht.

Beim ersten Testlauf richtet die Suite über die Oberfläche einen
Testadministrator ein:

```text
Benutzername: regressionadmin
Passwort:    FamilyHub-E2E-2026!
```

Diese bekannten Zugangsdaten gehören ausschließlich zur isolierten
Testumgebung. Für reproduzierbare Familienlimit-Prüfungen keine dauerhaften
zusätzlichen Administratoren oder Kinder in dieser Testdatenbank anlegen.
Es darf nur ein Testlauf gleichzeitig aktiv sein.

## 5. Vollständigen Gesamtlauf starten

Für alle **207 Cucumber-Szenarien plus 90 native JUnit-Backendtests**:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-regression.ps1
```

Alternativ:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 all
```

Der Pod muss zuvor gestartet sein. Die nativen Tests laufen in einem
zusätzlichen temporären Container im selben Pod; dieser wird danach
entfernt. Das Gesamtskript führt auch nach fachlich fehlgeschlagenen Tests
die verbleibenden Tests aus und erzeugt anschließend den Gesamtbericht.
Bei fehlgeschlagenen Tests endet es mit **Exitcode 1**.

Der neue Gesamtbericht ist anschließend hier erreichbar:

**<http://localhost:6080/reports/index.html>**

Einzelne Cucumber-Läufe aktualisieren den zusammengeführten Gesamtbericht
nicht automatisch. Dafür den Gesamtlauf erneut starten.

## 6. Berichte öffnen und sichern

| Bericht | Adresse |
|---|---|
| Alle 297 Testfälle mit Suche und Ergebnisfilter | <http://localhost:6080/reports/index.html> |
| Alle Cucumber-Fälle mit Screenshots | <http://localhost:6080/reports/cucumber.html> |
| Injection- und Barrierefreiheitsdetails | <http://localhost:6080/reports/audit/index.html> |
| Gesamter JUnit-Export für CI | <http://localhost:6080/reports/all-junit.xml> |
| Alle Ergebnisse als JSON | <http://localhost:6080/reports/all-results.json> |

Berichte entstehen erst nach dem entsprechenden Testlauf. Nach einem neuen
Lauf die Berichtsseite im Browser neu laden. Der Cucumber-Bericht zeigt den
jeweils letzten Cucumber-Lauf, auch wenn nur ein Teilbereich gestartet wurde.

Alle aktuellen Berichte auf den Host kopieren:

```powershell
podman cp familyhub-e2e-desktop:/tests/reports ./testberichte
```

Berichte, Screenshots und Traces können Test- und Sitzungsdaten enthalten.
Sie gehören als Laufartefakte in die CI bzw. in die lokale Ablage und nicht
in Git. Beim Einchecken des Testcodes insbesondere `reports/`,
`testberichte/`, `source/`, `node_modules/` und lokale Zugangsdaten ausschließen.

## 7. Stoppen und erneut starten

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 stop
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 start
```

Status und Backend-Ausgabe:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 status
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 logs
podman logs familyhub-e2e-desktop
```

Die Volumes `familyhub-e2e-data` und `familyhub-e2e-reports` erhalten
Testdaten und Berichte über einen Container-Neustart hinweg. Ein automatischer
Start des Testlabors beim Windows-Start ist nicht eingerichtet.

## 8. Nach Änderungen neu bauen

Geänderte Testdateien werden erst durch einen neuen Image-Build in den
Container übernommen. Zunächst gegebenenfalls Berichte sichern, dann:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 remove
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 start
```

Die benannten Volumes bleiben dabei erhalten. Änderungen am geprüften
Anwendungsstand erfordern außerdem eine bewusste Anpassung des festgelegten
Commits in den Startskripten.

## 9. Testdaten vollständig zurücksetzen

Die folgenden Befehle löschen ausschließlich die separate Testdatenbank.
Vorher benötigte Testdaten sichern:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 remove
podman volume rm familyhub-e2e-data
powershell -ExecutionPolicy Bypass -File .\start-lab.ps1 start
```

Der nächste Testlauf legt den Testadministrator erneut an. Das separate
Berichtsvolume bleibt erhalten.

## 10. Fehlerbehebung

### Podman ist nicht erreichbar

```powershell
podman machine list
podman machine start
podman info
```

### Port 6080 oder 18080 ist belegt

Den bereits laufenden Dienst identifizieren und den Portkonflikt beheben.
Bei einer bereits vorhandenen Testinstallation zuerst deren Status prüfen:

```powershell
podman pod ps
podman ps -a --filter pod=familyhub-e2e
```

### Start oder Build wurde unterbrochen

Logs prüfen. Falls ein unvollständiger Pod zurückgeblieben ist, mit
`start-lab.ps1 remove` entfernen und mit `start-lab.ps1 start` neu erstellen.
Die Datenvolumes werden dabei nicht gelöscht.

### Ein Testlauf gilt nach einem Abbruch noch als aktiv

Zuerst sicherstellen, dass tatsächlich kein Testprozess mehr läuft:

```powershell
podman top familyhub-e2e-desktop
```

Erst dann den verbliebenen Lauf-Lock entfernen:

```powershell
podman exec familyhub-e2e-desktop rm -f /tests/reports/.running
```

### Tests sind rot, obwohl die Installation funktioniert

Im zuletzt vollständig geprüften Lauf bestanden **280 von 297 Fällen**.
Offen waren sieben Passwortfälle mit HTTP 500 sowie zehn axe-Prüfungen mit
Kontrastproblemen und fehlenden Kalender-Button-Namen. Diese Fehler werden
nicht übersprungen und bedeuten keinen Installationsfehler.

Details: [GESAMTLAUF.md](GESAMTLAUF.md), [BEFUNDE.md](BEFUNDE.md),
[AUDIT-BEFUNDE.md](AUDIT-BEFUNDE.md) und
[EINGABE-ANALYSE.md](EINGABE-ANALYSE.md).

## Linux

Das Testlabor kann unter Linux aus demselben Ordner gestartet werden:

```bash
bash start-lab.sh start
bash start-lab.sh test
bash start-lab.sh audit
bash start-lab.sh stop
```

Die enthaltene Orchestrierung des kombinierten 297er-Laufs ist ein
PowerShell-Skript für Windows. Das Bash-Startskript bietet die
Cucumber-Teilläufe; es startet die nativen JUnit-Tests nicht automatisch.
