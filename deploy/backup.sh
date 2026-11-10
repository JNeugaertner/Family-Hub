#!/usr/bin/env bash
# Sichert die FamilyHub-Datenbank nach deploy/backups/ (eine Datei je Lauf) und löscht Sicherungen, die älter als
# BACKUP_KEEP_DAYS Tage sind (Standard 14). Läuft, während FamilyHub läuft. Täglich per cron, z. B.:
#   30 3 * * * /pfad/zu/deploy/backup.sh >> /pfad/zu/deploy/backups/backup.log 2>&1
# Zurückspielen: siehe README, „Sichern und Wiederherstellen“. Eine Sicherung auf demselben Rechner schützt nicht
# vor einem Plattenschaden: die Dateien regelmäßig woandershin kopieren.
set -euo pipefail
cd "$(dirname "$0")"

password="$(grep -E '^MONGO_PASSWORD=' .env | cut -d= -f2- || true)"
if [[ -z "$password" ]]; then
  echo "In .env fehlt MONGO_PASSWORD." >&2
  exit 1
fi

mkdir -p backups
file="backups/familyhub-$(date +%Y-%m-%d_%H%M%S).archive.gz"
docker compose exec -T mongodb mongodump --quiet --archive --gzip --db familyhub \
  --username familyhub --password "$password" --authenticationDatabase admin > "$file.part"
mv "$file.part" "$file"
find backups -name 'familyhub-*.archive.gz' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete
echo "Gesichert: $file ($(du -h "$file" | cut -f1))"
