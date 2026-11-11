#!/usr/bin/env bash
# Baut deploy/FamilyHub.app aus familyhub-fenster.js (nur unter macOS). Nach Änderungen am Fenster neu ausführen
# und die App mit einchecken. osacompile, sips, iconutil und codesign sind in macOS enthalten.
set -euo pipefail
cd "$(dirname "$0")"
app=../FamilyHub.app
rm -rf "$app"
# -s: bleibt offen (Fenster mit Ereignisschleife), statt nach dem Skript sofort zu beenden
osacompile -l JavaScript -s -o "$app" familyhub-fenster.js

# Symbol: Haus mit Herz in allen Größen, die macOS erwartet
iconset="$(mktemp -d)/FamilyHub.iconset"
mkdir -p "$iconset"
for size in 16 32 64 128 256 512; do
  sips -z $size $size familyhub-512.png --out "$iconset/icon_${size}x${size}.png" >/dev/null
  double=$((size * 2))
  [[ $double -le 512 ]] && sips -z $double $double familyhub-512.png --out "$iconset/icon_${size}x${size}@2x.png" >/dev/null
done
iconutil -c icns "$iconset" -o "$app/Contents/Resources/applet.icns"
# Neuere macOS-Versionen nähmen sonst das Skript-Symbol aus Assets.car (CFBundleIconName) statt applet.icns
rm -f "$app/Contents/Resources/Assets.car"
rm -rf "$(dirname "$iconset")"

plist="$app/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Set :CFBundleName FamilyHub" "$plist"
/usr/libexec/PlistBuddy -c "Delete :CFBundleIconName" "$plist" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :CFBundleDisplayName string FamilyHub" "$plist" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :CFBundleIdentifier string de.familyhub.fenster" "$plist" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Set :CFBundleIdentifier de.familyhub.fenster" "$plist"
/usr/libexec/PlistBuddy -c "Add :NSHighResolutionCapable bool true" "$plist" 2>/dev/null || true

# Nach den Änderungen neu (ad hoc) signieren, sonst meldet macOS die App als beschädigt
codesign --force --deep --sign - "$app"
touch "$app"  # Finder liest das Symbol neu
echo "Gebaut: $(cd "$(dirname "$app")" && pwd)/FamilyHub.app"
