#!/usr/bin/env bash
# Regressionstests auf Knopfdruck (macOS und Linux; Windows: regression.ps1). Baut FamilyHub aus dem aktuellen
# Checkout, startet es mit einer leeren Wegwerf-Datenbank auf Port 8081, führt die Tests aus und räumt danach auf.
#
#   ./regression.sh                      alle Tests (Browser sichtbar)
#   ./regression.sh --tags @smoke        nur bestimmte Tests (alle Optionen von cucumber-js gehen)
#   ./regression.sh --headless ...       ohne sichtbaren Browser
#   ./regression.sh --container ...      Tests im Playwright-Container (ohne Node.js auf dem Rechner, ohne Fenster)
#   ./regression.sh --keep ...           Testumgebung danach weiterlaufen lassen (http://127.0.0.1:8081)
#   ./regression.sh down                 eine weiterlaufende Testumgebung beenden
#
# Bericht: regression/reports/cucumber.html
set -euo pipefail
cd "$(dirname "$0")"
PATH="$PATH:$HOME/.docker/bin:/usr/local/bin:/opt/homebrew/bin:$HOME/.local/opt/node/bin"

fail() { printf '\nFehler: %s\n' "$*" >&2; exit 1; }

engine=""
if command -v docker >/dev/null 2>&1; then engine=docker
elif command -v podman >/dev/null 2>&1; then engine=podman
else fail "Weder Docker noch Podman gefunden."
fi
"$engine" info >/dev/null 2>&1 || fail "$engine läuft nicht. Bitte Docker Desktop starten und erneut versuchen."
compose() { "$engine" compose "$@"; }

port="${REGRESSION_PORT:-8081}"
export REGRESSION_PORT="$port"

if [[ "${1:-}" == down ]]; then compose down -v --remove-orphans; exit 0; fi

container=false keep=false
args=()
for arg in "$@"; do
  case "$arg" in
    --container) container=true ;;
    --keep) keep=true ;;
    --headless) export HEADLESS=true ;;
    *) args+=("$arg") ;;
  esac
done

if ! $container && ! command -v node >/dev/null 2>&1; then
  echo "Node.js nicht gefunden: Die Tests laufen im Container (ohne sichtbaren Browser)."
  container=true
fi

if ! compose ps -q --status running familyhub 2>/dev/null | grep -q .; then
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    fail "Port $port ist belegt. Anderen Port wählen: REGRESSION_PORT=8091 ./regression.sh"
  fi
fi

cleanup() {
  if $keep; then
    echo "Testumgebung läuft weiter: http://127.0.0.1:$port (beenden mit ./regression.sh down)"
  else
    echo "Testumgebung wird entfernt …"
    compose --profile container down -v --remove-orphans >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "FamilyHub wird aus dem aktuellen Stand gebaut und gestartet …"
compose up -d --build familyhub

echo "Warten, bis FamilyHub bereit ist …"
ready=false
for _ in $(seq 1 90); do
  if [[ "$(curl -fsS "http://127.0.0.1:$port/api/health" 2>/dev/null || true)" == *'"UP"'* ]]; then ready=true; break; fi
  sleep 2
done
if ! $ready; then
  compose logs --tail 40 familyhub || true
  fail "FamilyHub ist nach drei Minuten nicht bereit (Protokoll oben)."
fi

status=0
if $container; then
  compose --profile container run --rm tests ${args[@]+"${args[@]}"} || status=$?
else
  if [[ ! -d node_modules/@cucumber ]]; then npm ci --no-audit --no-fund --loglevel=error; fi
  # Ohne eigenen Playwright-Browser den installierten Google Chrome verwenden
  if [[ -z "${BROWSER_CHANNEL:-}" ]] && ! ls "${PLAYWRIGHT_BROWSERS_PATH:-$HOME/Library/Caches/ms-playwright}"/chromium-* \
       "$HOME/.cache/ms-playwright"/chromium-* >/dev/null 2>&1; then
    export BROWSER_CHANNEL=chrome
  fi
  BASE_URL="http://127.0.0.1:$port" node scripts/run.mjs ${args[@]+"${args[@]}"} || status=$?
fi

echo
if (( status == 0 )); then echo "Alle Tests bestanden."; else echo "Es sind Tests fehlgeschlagen (Status $status)."; fi
echo "Bericht: $(pwd)/reports/cucumber.html"
exit "$status"
