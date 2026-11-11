#!/usr/bin/env bash
# macOS: Doppelklick startet FamilyHub (siehe familyhub.sh). Das Fenster bleibt offen, bis man eine Taste drückt.
cd "$(dirname "$0")"
./familyhub.sh start
status=$?
echo
read -n 1 -s -r -p "Zum Schließen eine Taste drücken …"
exit $status
