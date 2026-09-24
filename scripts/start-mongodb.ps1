# Startet eine lokale MongoDB fuer die Entwicklung (nur 127.0.0.1, Port 27017).
# Beenden mit Strg+C. Daten bleiben in %LOCALAPPDATA%\FamilyHub\mongodb erhalten.
$ErrorActionPreference = 'Stop'

$base = Join-Path $env:LOCALAPPDATA 'FamilyHub\mongodb'
$dataDir = Join-Path $base 'data'
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

$mongod = Get-Command mongod -ErrorAction SilentlyContinue
if (-not $mongod) {
    $fallback = Join-Path $env:LOCALAPPDATA 'Programs\mongodb\bin\mongod.exe'
    if (-not (Test-Path $fallback)) {
        throw "mongod nicht gefunden. MongoDB-ZIP nach $env:LOCALAPPDATA\Programs\mongodb entpacken (siehe README)."
    }
    $mongod = $fallback
} else {
    $mongod = $mongod.Source
}

Write-Host "MongoDB startet: mongodb://127.0.0.1:27017  (Daten: $dataDir)"
& $mongod --dbpath $dataDir --bind_ip 127.0.0.1 --port 27017
