# FamilyHub mit Docker oder Podman starten und beenden (Windows). Normalerweise per Doppelklick auf
# „FamilyHub starten.bat“ bzw. „FamilyHub beenden.bat“; Gegenstück zu familyhub.sh (macOS, Linux).
#
#   familyhub.ps1 start    starten und den Browser öffnen (beim ersten Mal: .env anlegen, Image bauen oder laden)
#   familyhub.ps1 stop     beenden; die Daten bleiben erhalten
#   familyhub.ps1 update   neues Image bauen (im Projekt) bzw. laden (nur deploy\) und neu starten
#   familyhub.ps1 logs     Protokoll von FamilyHub ansehen (beenden mit Strg+C)
#
# Gespeichert als UTF-8 mit BOM, sonst zeigt Windows PowerShell 5.1 die Umlaute falsch an.
param([ValidateSet('start', 'stop', 'update', 'logs')][string]$Action = 'start')

# Nicht 'Stop': PowerShell 5.1 behandelte sonst jede Ausgabe von docker auf stderr als Fehler
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
$envFile = Join-Path $PSScriptRoot '.env'

function Fail([string]$message) {
    Write-Host ''
    Write-Host "Fehler: $message" -ForegroundColor Red
    exit 1
}

# --- Docker oder Podman finden ---------------------------------------------------------------------------------

$engine = $null
foreach ($candidate in 'docker', 'podman') {
    if (Get-Command $candidate -ErrorAction SilentlyContinue) { $engine = $candidate; break }
}
if (-not $engine) {
    $dockerExe = Join-Path $env:ProgramFiles 'Docker\Docker\resources\bin\docker.exe'
    if (Test-Path $dockerExe) { $engine = $dockerExe }
}
if (-not $engine) {
    Fail 'Weder Docker noch Podman gefunden. Bitte Docker Desktop installieren: https://www.docker.com/products/docker-desktop/'
}
$isPodman = $engine -like '*podman*'

function Test-Engine {
    & $engine info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Initialize-Engine {
    if (Test-Engine) { return }
    if ($isPodman) {
        Write-Host 'Podman wird gestartet …'
        & $engine machine start *> $null
    } else {
        $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        if (-not (Test-Path $desktop)) { Fail 'Docker läuft nicht. Bitte Docker Desktop starten.' }
        Write-Host 'Docker Desktop wird gestartet …'
        Start-Process $desktop
    }
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-Engine) { return }
        Start-Sleep -Seconds 2
    }
    Fail "$engine antwortet nicht. Bitte prüfen, ob es läuft (Wal-Symbol in der Taskleiste), und erneut versuchen."
}

function Invoke-Compose { & $engine compose @args }

# --- Einstellungen (.env) --------------------------------------------------------------------------------------

function Get-EnvValue([string]$key) {
    if (-not (Test-Path $envFile)) { return '' }
    $line = Get-Content $envFile | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
    if ($line) { return ($line -replace "^$key=", '').Trim() }
    return ''
}

# Mit LF und ohne BOM schreiben: docker compose läse sonst ein unsichtbares Zeichen am Ende jedes Werts mit
function Save-Lines([string[]]$lines) {
    [IO.File]::WriteAllText($envFile, (($lines -join "`n") + "`n"), (New-Object Text.UTF8Encoding $false))
}

function Set-EnvValue([string]$key, [string]$value) {
    $lines = @(Get-Content $envFile)
    $done = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if (-not $done -and $lines[$i] -match "^#? ?$key=") { $lines[$i] = "$key=$value"; $done = $true }
    }
    if (-not $done) { $lines += "$key=$value" }
    Save-Lines $lines
}

# Im Projekt (Dockerfile eine Ebene höher) baut FamilyHub sein Image selbst; nur mit dem Ordner deploy\ lädt es
# das veröffentlichte Image.
function Test-InProject { return (Test-Path (Join-Path $PSScriptRoot '..\Dockerfile')) }
function Get-LocalImage { if ($isPodman) { return 'localhost/familyhub:lokal' } return 'familyhub:lokal' }
function Test-Image([string]$image) {
    & $engine image inspect $image *> $null
    return ($LASTEXITCODE -eq 0)
}

# Das Volume mit den Daten; der Projektname kommt aus compose.yaml ("familyhub"), außer COMPOSE_PROJECT_NAME ist gesetzt
function Get-DataVolume {
    $project = if ($env:COMPOSE_PROJECT_NAME) { $env:COMPOSE_PROJECT_NAME } else { 'familyhub' }
    return "${project}_familyhub-data"
}

function Initialize-Env {
    if (-not (Test-Path (Join-Path $PSScriptRoot '.env.example'))) {
        Fail 'deploy\.env.example fehlt. Bitte die Dateien aus dem Ordner deploy vollständig kopieren.'
    }
    if (-not (Test-Path $envFile)) {
        # Ein neues Passwort sperrte FamilyHub aus den vorhandenen Daten aus: MongoDB übernimmt es nur beim ersten Start
        & $engine volume inspect (Get-DataVolume) *> $null
        if ($LASTEXITCODE -eq 0) {
            Fail "deploy\.env fehlt, es gibt aber schon Daten (Volume $(Get-DataVolume)). Bitte die alte .env zurückholen, z. B. aus der Sicherung. Nur wer die Daten wirklich nicht mehr braucht: $engine compose down -v, dann neu starten."
        }
        Write-Host 'Erster Start: Einstellungen werden angelegt (deploy\.env) …'
        Save-Lines @(Get-Content (Join-Path $PSScriptRoot '.env.example'))
        Set-EnvValue 'MONGO_PASSWORD' ([guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N'))
        if (Test-InProject) { Set-EnvValue 'FAMILYHUB_IMAGE' (Get-LocalImage) }
    }
    if (-not (Get-EnvValue 'MONGO_PASSWORD')) {
        Fail 'In deploy\.env fehlt MONGO_PASSWORD (Buchstaben und Ziffern, mindestens 24 Zeichen).'
    }
}

function Build-Image {
    Write-Host 'FamilyHub wird gebaut. Beim ersten Mal dauert das einige Minuten …'
    & $engine build -t familyhub:lokal ..
    if ($LASTEXITCODE -ne 0) { Fail 'Das Image ließ sich nicht bauen (Ausgabe oben).' }
}

function Initialize-Image {
    $image = Get-EnvValue 'FAMILYHUB_IMAGE'
    if ($image -like '*familyhub:lokal') {
        if (Test-Image $image) { return }
        if (-not (Test-InProject)) {
            Fail "Das Image $image gibt es nicht. In deploy\.env die Zeile FAMILYHUB_IMAGE entfernen, dann wird das veröffentlichte Image geladen."
        }
        Build-Image
    }
}

# --- Befehle ---------------------------------------------------------------------------------------------------

function Get-Port { $p = Get-EnvValue 'FAMILYHUB_PORT'; if ($p) { return $p } return '8080' }
function Get-Url { $u = Get-EnvValue 'FAMILYHUB_URL'; if ($u) { return $u } return "http://localhost:$(Get-Port)" }

function Test-Running {
    $id = Invoke-Compose ps -q --status running familyhub 2> $null
    return [bool]$id
}

function Test-WrongPassword {
    if ((Invoke-Compose logs familyhub 2>&1 | Out-String) -match 'AuthenticationFailed') {
        Fail 'FamilyHub kommt nicht an die Datenbank: Das Passwort in deploy\.env passt nicht zu den vorhandenen Daten. MONGO_PASSWORD gilt nur beim allerersten Start; bitte den alten Wert wieder eintragen.'
    }
}

function Wait-Ready {
    Write-Host 'Warten, bis FamilyHub bereit ist …'
    for ($i = 1; $i -le 90; $i++) {
        try {
            $health = Invoke-RestMethod "http://localhost:$(Get-Port)/api/health" -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch { }
        # Ein falsches Passwort wird nicht besser: alle zehn Sekunden nachsehen und dann gleich abbrechen
        if ($i % 5 -eq 0) { Test-WrongPassword }
        Start-Sleep -Seconds 2
    }
    Test-WrongPassword
    Invoke-Compose logs --tail 30 familyhub
    Fail 'FamilyHub ist nach drei Minuten nicht bereit (Protokoll oben).'
}

function Start-FamilyHub {
    Initialize-Engine
    Initialize-Env
    Initialize-Image
    if (Test-Running) {
        Write-Host 'FamilyHub läuft schon.'
    } else {
        if (Get-NetTCPConnection -LocalPort ([int](Get-Port)) -State Listen -ErrorAction SilentlyContinue) {
            Fail "Port $(Get-Port) ist belegt, z. B. durch ein laufendes Entwicklungs-Backend. Dieses beenden oder in deploy\.env FAMILYHUB_PORT und FAMILYHUB_URL ändern."
        }
        Write-Host 'FamilyHub wird gestartet …'
        Invoke-Compose up -d
        if ($LASTEXITCODE -ne 0) { Fail 'Der Start ist fehlgeschlagen (Ausgabe oben).' }
        Wait-Ready
    }
    Write-Host ''
    Write-Host "FamilyHub läuft: $(Get-Url)" -ForegroundColor Green
    Write-Host 'Beim ersten Start dort unter „Familie einrichten“ den ersten Administrator anlegen.'
    Start-Process (Get-Url)
}

function Stop-FamilyHub {
    Initialize-Engine
    Write-Host 'FamilyHub wird beendet …'
    Invoke-Compose down
    Write-Host 'FamilyHub ist beendet. Die Daten bleiben erhalten.' -ForegroundColor Green
}

function Update-FamilyHub {
    Initialize-Engine
    Initialize-Env
    $image = Get-EnvValue 'FAMILYHUB_IMAGE'
    if ($image -like '*familyhub:lokal') {
        if (-not (Test-InProject)) { Fail 'Ein selbst gebautes Image lässt sich nur im Projektordner neu bauen.' }
        Build-Image
    } else {
        Write-Host 'Neueste Version wird geladen …'
        Invoke-Compose pull familyhub
    }
    Invoke-Compose up -d
    Wait-Ready
    Write-Host "FamilyHub ist aktualisiert: $(Get-Url)" -ForegroundColor Green
}

switch ($Action) {
    'start' { Start-FamilyHub }
    'stop' { Stop-FamilyHub }
    'update' { Update-FamilyHub }
    'logs' { Initialize-Engine; Invoke-Compose logs -f familyhub }
}
