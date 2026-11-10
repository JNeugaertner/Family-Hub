#!/usr/bin/env bash
# macOS: Doppelklick beendet FamilyHub (siehe familyhub.sh). Die Daten bleiben erhalten.
cd "$(dirname "$0")"
./familyhub.sh stop
status=$?
echo
read -n 1 -s -r -p "Zum Schließen eine Taste drücken …"
exit $status
