# Regressionstests auf Knopfdruck (Windows; macOS und Linux: regression.sh). Baut FamilyHub aus dem aktuellen
# Checkout, startet es mit einer leeren Wegwerf-Datenbank auf Port 8081, führt die Tests aus und räumt danach auf.
#
#   .\regression.ps1                      alle Tests (Browser sichtbar)
#   .\regression.ps1 --tags '@smoke'      nur bestimmte Tests (alle Optionen von cucumber-js gehen)
#   .\regression.ps1 --headless ...       ohne sichtbaren Browser
#   .\regression.ps1 --container ...      Tests im Playwright-Container (ohne Node.js auf dem Rechner, ohne Fenster)
#   .\regression.ps1 --keep ...           Testumgebung danach weiterlaufen lassen (http://127.0.0.1:8081)
#   .\regression.ps1 down                 eine weiterlaufende Testumgebung beenden
#
# Bericht: regression\reports\cucumber.html
# Falls Windows das Skript blockiert: powershell -ExecutionPolicy Bypass -File .\regression.ps1
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

function Fail([string]$message) { Write-Host "`nFehler: $message" -ForegroundColor Red; exit 1 }

$engine = $null
if (Get-Command docker -ErrorAction SilentlyContinue) { $engine = 'docker' }
elseif (Get-Command podman -ErrorAction SilentlyContinue) { $engine = 'podman' }
else { Fail 'Weder Docker noch Podman gefunden.' }
& $engine info *> $null
if ($LASTEXITCODE -ne 0) { Fail "$engine läuft nicht. Bitte Docker Desktop starten und erneut versuchen." }

$port = if ($env:REGRESSION_PORT) { $env:REGRESSION_PORT } else { '8081' }
$env:REGRESSION_PORT = $port

if ($args.Count -gt 0 -and $args[0] -eq 'down') { & $engine compose down -v --remove-orphans; exit $LASTEXITCODE }

$container = $false; $keep = $false
$testArgs = @()
foreach ($arg in $args) {
  switch ($arg) {
    '--container' { $container = $true }
    '--keep' { $keep = $true }
    '--headless' { $env:HEADLESS = 'true' }
    default { $testArgs += $arg }
  }
}

if (-not $container -and -not (Get-Command node -ErrorAction SilentlyContinue)) {
  Write-Host 'Node.js nicht gefunden: Die Tests laufen im Container (ohne sichtbaren Browser).'
  $container = $true
}

$running = & $engine compose ps -q --status running familyhub 2>$null
if (-not $running -and (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) {
  Fail "Port $port ist belegt. Anderen Port wählen: `$env:REGRESSION_PORT='8091'; .\regression.ps1"
}

$status = 1
try {
  Write-Host 'FamilyHub wird aus dem aktuellen Stand gebaut und gestartet …'
  & $engine compose up -d --build familyhub
  if ($LASTEXITCODE -ne 0) { Fail 'FamilyHub ließ sich nicht bauen oder starten (Ausgabe oben).' }

  Write-Host 'Warten, bis FamilyHub bereit ist …'
  $ready = $false
  for ($i = 0; $i -lt 90 -and -not $ready; $i++) {
    try {
      $health = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:$port/api/health"
      $ready = $health.Content -match '"UP"'
    } catch { }
    if (-not $ready) { Start-Sleep -Seconds 2 }
  }
  if (-not $ready) {
    & $engine compose logs --tail 40 familyhub
    Fail 'FamilyHub ist nach drei Minuten nicht bereit (Protokoll oben).'
  }

  if ($container) {
    & $engine compose --profile container run --rm tests @testArgs
  } else {
    if (-not (Test-Path 'node_modules\@cucumber')) { & npm ci --no-audit --no-fund --loglevel=error }
    # Ohne eigenen Playwright-Browser den installierten Chrome verwenden, sonst Edge (gibt es unter Windows immer)
    if (-not $env:BROWSER_CHANNEL -and -not (Test-Path "$env:LOCALAPPDATA\ms-playwright\chromium-*")) {
      $chrome = @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe") | Where-Object { Test-Path $_ }
      $env:BROWSER_CHANNEL = if ($chrome) { 'chrome' } else { 'msedge' }
    }
    $env:BASE_URL = "http://127.0.0.1:$port"
    & node scripts/run.mjs @testArgs
  }
  $status = $LASTEXITCODE

  Write-Host ''
  if ($status -eq 0) { Write-Host 'Alle Tests bestanden.' -ForegroundColor Green }
  else { Write-Host "Es sind Tests fehlgeschlagen (Status $status)." -ForegroundColor Yellow }
  Write-Host "Bericht: $PSScriptRoot\reports\cucumber.html"
} finally {
  if ($keep) {
    Write-Host "Testumgebung läuft weiter: http://127.0.0.1:$port (beenden mit .\regression.ps1 down)"
  } else {
    Write-Host 'Testumgebung wird entfernt …'
    & $engine compose --profile container down -v --remove-orphans *> $null
  }
}
exit $status
