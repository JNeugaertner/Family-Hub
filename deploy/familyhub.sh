#!/usr/bin/env bash
# FamilyHub mit Docker oder Podman starten und beenden (macOS und Linux). Unter macOS geht das auch per
# Doppelklick auf „FamilyHub starten.command“ bzw. „FamilyHub beenden.command“, unter Windows mit den .bat-Dateien.
#
#   ./familyhub.sh start    starten und den Browser öffnen (beim ersten Mal: .env anlegen, Image bauen oder laden)
#   ./familyhub.sh stop     beenden; die Daten bleiben erhalten
#   ./familyhub.sh update   neues Image bauen (im Projekt) bzw. laden (nur deploy/) und neu starten
#   ./familyhub.sh logs     Protokoll von FamilyHub ansehen (beenden mit Strg+C)
set -euo pipefail
cd "$(dirname "$0")"

say() { printf '%s\n' "$*"; }
fail() { printf '\nFehler: %s\n' "$*" >&2; exit 1; }

# --- Docker oder Podman finden ---------------------------------------------------------------------------------

# Docker Desktop unter macOS legt den Befehl nach ~/.docker/bin, das oft nicht im Suchpfad steht
PATH="$PATH:$HOME/.docker/bin:/usr/local/bin:/opt/homebrew/bin"

engine=""
if command -v docker >/dev/null 2>&1; then
  engine=docker
elif command -v podman >/dev/null 2>&1; then
  engine=podman
else
  fail "Weder Docker noch Podman gefunden. Bitte Docker Desktop installieren: https://www.docker.com/products/docker-desktop/"
fi

engine_running() { "$engine" info >/dev/null 2>&1; }

ensure_engine() {
  engine_running && return
  if [[ "$engine" == docker && "$(uname -s)" == Darwin ]]; then
    say "Docker Desktop wird gestartet …"
    open -a Docker || fail "Docker Desktop ließ sich nicht starten. Bitte von Hand öffnen."
  elif [[ "$engine" == podman ]]; then
    say "Podman wird gestartet …"
    podman machine start >/dev/null 2>&1 || true
  else
    fail "Docker läuft nicht. Bitte Docker starten (z. B. sudo systemctl start docker)."
  fi
  for _ in $(seq 1 60); do
    engine_running && return
    sleep 2
  done
  fail "$engine antwortet nicht. Bitte prüfen, ob es läuft (Wal-Symbol in der Menüleiste), und erneut versuchen."
}

compose() { "$engine" compose "$@"; }

# --- Einstellungen (.env) --------------------------------------------------------------------------------------

env_value() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- | tr -d '\r'; }

set_env() {
  # Zeile KEY=… ersetzen, eine auskommentierte "# KEY=…" einschalten oder ans Ende anhängen
  local key=$1 value=$2 tmp
  tmp="$(mktemp)"
  if grep -qE "^#? ?$key=" .env; then
    awk -v k="$key" -v v="$value" 'done == 0 && $0 ~ "^#? ?" k "=" { print k "=" v; done = 1; next } { print }' \
      .env > "$tmp"
  else
    cat .env > "$tmp"
    printf '%s=%s\n' "$key" "$value" >> "$tmp"
  fi
  cat "$tmp" > .env
  rm -f "$tmp"
}

random_password() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
  else
    od -An -tx1 -N24 /dev/urandom | tr -d ' \n'
  fi
}

# Im Projekt (Dockerfile eine Ebene höher) baut FamilyHub sein Image selbst; nur mit dem Ordner deploy/ lädt es das
# veröffentlichte Image.
in_project() { [[ -f ../Dockerfile ]]; }
local_image() { [[ "$engine" == podman ]] && echo localhost/familyhub:lokal || echo familyhub:lokal; }
image_exists() { "$engine" image inspect "$1" >/dev/null 2>&1; }

# Das Volume mit den Daten; der Projektname kommt aus compose.yaml ("familyhub"), außer COMPOSE_PROJECT_NAME ist gesetzt
data_volume() { echo "${COMPOSE_PROJECT_NAME:-familyhub}_familyhub-data"; }

ensure_env() {
  [[ -f .env.example ]] || fail "deploy/.env.example fehlt. Bitte die Dateien aus dem Ordner deploy vollständig kopieren."
  if [[ ! -f .env ]] && "$engine" volume inspect "$(data_volume)" >/dev/null 2>&1; then
    # Ein neues Passwort sperrte FamilyHub aus den vorhandenen Daten aus: MongoDB übernimmt es nur beim ersten Start
    fail "deploy/.env fehlt, es gibt aber schon Daten (Volume $(data_volume)). Bitte die alte .env zurückholen, z. B. aus der Sicherung. Nur wer die Daten wirklich nicht mehr braucht: $engine compose down -v, dann neu starten."
  fi
  if [[ ! -f .env ]]; then
    say "Erster Start: Einstellungen werden angelegt (deploy/.env) …"
    tr -d '\r' < .env.example > .env
    chmod 600 .env
    set_env MONGO_PASSWORD "$(random_password)"
    in_project && set_env FAMILYHUB_IMAGE "$(local_image)"
  fi
  [[ -n "$(env_value MONGO_PASSWORD)" ]] || fail "In deploy/.env fehlt MONGO_PASSWORD (Buchstaben und Ziffern, mindestens 24 Zeichen)."
}

build_image() {
  say "FamilyHub wird gebaut. Beim ersten Mal dauert das einige Minuten …"
  "$engine" build -t familyhub:lokal .. || fail "Das Image ließ sich nicht bauen (Ausgabe oben)."
}

ensure_image() {
  local image
  image="$(env_value FAMILYHUB_IMAGE)"
  if [[ -n "$image" && "$image" == *familyhub:lokal ]]; then
    image_exists "$image" && return
    in_project || fail "Das Image $image gibt es nicht. In deploy/.env die Zeile FAMILYHUB_IMAGE entfernen, dann wird das veröffentlichte Image geladen."
    build_image
  fi
}

# --- Befehle ---------------------------------------------------------------------------------------------------

port() { local p; p="$(env_value FAMILYHUB_PORT)"; echo "${p:-8080}"; }
url() { local u; u="$(env_value FAMILYHUB_URL)"; echo "${u:-http://localhost:$(port)}"; }

running() { [[ -n "$(compose ps -q --status running familyhub 2>/dev/null)" ]]; }

open_browser() {
  if command -v open >/dev/null 2>&1; then open "$1"
  elif command -v xdg-open >/dev/null 2>&1; then xdg-open "$1" >/dev/null 2>&1 &
  else say "Bitte im Browser öffnen: $1"
  fi
}

wrong_password() {
  # Erst ganz lesen, dann suchen: "logs | grep -q" bräche mit pipefail ab, sobald grep fündig wird
  local logs
  logs="$(compose logs familyhub 2>&1 || true)"
  if [[ "$logs" == *AuthenticationFailed* ]]; then
    fail "FamilyHub kommt nicht an die Datenbank: Das Passwort in deploy/.env passt nicht zu den vorhandenen Daten. MONGO_PASSWORD gilt nur beim allerersten Start; bitte den alten Wert wieder eintragen."
  fi
}

wait_until_ready() {
  say "Warten, bis FamilyHub bereit ist …"
  for i in $(seq 1 90); do
    if [[ "$(curl -fsS "http://localhost:$(port)/api/health" 2>/dev/null || true)" == *'"UP"'* ]]; then
      return
    fi
    # Ein falsches Passwort wird nicht besser: alle zehn Sekunden nachsehen und dann gleich abbrechen
    (( i % 5 == 0 )) && wrong_password
    sleep 2
  done
  wrong_password
  compose logs --tail 30 familyhub || true
  fail "FamilyHub ist nach drei Minuten nicht bereit (Protokoll oben)."
}

start() {
  ensure_engine
  ensure_env
  ensure_image
  if running; then
    say "FamilyHub läuft schon."
  else
    if lsof -nP -iTCP:"$(port)" -sTCP:LISTEN >/dev/null 2>&1; then
      fail "Port $(port) ist belegt, z. B. durch ein laufendes Entwicklungs-Backend. Dieses beenden oder in deploy/.env FAMILYHUB_PORT und FAMILYHUB_URL ändern."
    fi
    say "FamilyHub wird gestartet …"
    compose up -d || fail "Der Start ist fehlgeschlagen (Ausgabe oben)."
    wait_until_ready
  fi
  say ""
  say "FamilyHub läuft: $(url)"
  say "Beim ersten Start dort unter „Familie einrichten“ den ersten Administrator anlegen."
  open_browser "$(url)"
}

stop() {
  ensure_engine
  say "FamilyHub wird beendet …"
  compose down
  say "FamilyHub ist beendet. Die Daten bleiben erhalten."
}

update() {
  ensure_engine
  ensure_env
  local image
  image="$(env_value FAMILYHUB_IMAGE)"
  if [[ -n "$image" && "$image" == *familyhub:lokal ]]; then
    in_project || fail "Ein selbst gebautes Image lässt sich nur im Projektordner neu bauen."
    build_image
  else
    say "Neueste Version wird geladen …"
    compose pull familyhub
  fi
  compose up -d
  wait_until_ready
  say "FamilyHub ist aktualisiert: $(url)"
}

case "${1:-start}" in
  start) start ;;
  stop) stop ;;
  update) update ;;
  logs) ensure_engine; compose logs -f familyhub ;;
  *) say "Aufruf: ./familyhub.sh [start|stop|update|logs]"; exit 2 ;;
esac
