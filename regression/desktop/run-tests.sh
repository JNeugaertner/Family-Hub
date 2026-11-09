#!/bin/sh
cd /tests
printf '\nFamilyHub Regressionstests\n\n'
printf 'Anwendung: http://127.0.0.1:8081\nErgebnisse: http://localhost:6080/reports/cucumber.html\n\n'
while true; do
  printf 'Enter = alle Tests | audit = Injections + Barrierefreiheit | a11y = Barrierefreiheit | smoke | backend | extended | security | shell\n> '
  read -r action || exit 0
  case "$action" in
    smoke) npm run test:smoke ;;
    extended) npm run test:extended ;;
    security) npm run test:security ;;
    backend) npm run test:backend ;;
    audit) npm run test:audit ;;
    a11y) npm run test:a11y ;;
    shell) /bin/bash ;;
    *) npm test ;;
  esac
done
