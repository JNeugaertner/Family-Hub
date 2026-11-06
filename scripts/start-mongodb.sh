#!/usr/bin/env bash
# Startet eine lokale MongoDB fuer die Entwicklung unter macOS und Linux (nur 127.0.0.1, Port 27017).
# Gegenstueck zu start-mongodb.ps1 (Windows). Beenden mit Strg+C. Die Daten bleiben erhalten in
#   macOS: ~/Library/Application Support/FamilyHub/mongodb
#   Linux: ${XDG_DATA_HOME:-~/.local/share}/FamilyHub/mongodb
# mongod installieren, z. B. unter macOS: brew tap mongodb/brew && brew install mongodb-community@8.0
set -euo pipefail

if [[ "$(uname -s)" == "Darwin" ]]; then
  base="$HOME/Library/Application Support/FamilyHub/mongodb"
else
  base="${XDG_DATA_HOME:-$HOME/.local/share}/FamilyHub/mongodb"
fi
data_dir="$base/data"
mkdir -p "$data_dir"

mongod_bin="$(command -v mongod || true)"
if [[ -z "$mongod_bin" ]] && command -v brew >/dev/null 2>&1; then
  # Homebrew verlinkt versionierte Formeln nicht immer in den PATH
  for formula in mongodb-community@8.0 mongodb-community; do
    candidate="$(brew --prefix)/opt/$formula/bin/mongod"
    if [[ -x "$candidate" ]]; then
      mongod_bin="$candidate"
      break
    fi
  done
fi
if [[ -z "$mongod_bin" ]]; then
  echo "mongod nicht gefunden. Unter macOS: brew tap mongodb/brew && brew install mongodb-community@8.0" >&2
  echo "oder MongoDB mit Docker starten: docker compose up -d (siehe README)." >&2
  exit 1
fi

echo "MongoDB startet: mongodb://127.0.0.1:27017  (Daten: $data_dir)"
exec "$mongod_bin" --dbpath "$data_dir" --bind_ip 127.0.0.1 --port 27017
