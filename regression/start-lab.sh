#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
case "${1:-start}" in
  stop) exec podman pod stop familyhub-e2e ;;
  status) exec podman ps -a --filter pod=familyhub-e2e ;;
  logs) exec podman logs --tail 100 familyhub-e2e-backend ;;
  test) exec podman exec familyhub-e2e-desktop npm test ;;
  smoke) exec podman exec familyhub-e2e-desktop npm run test:smoke ;;
  extended) exec podman exec familyhub-e2e-desktop npm run test:extended ;;
  security) exec podman exec familyhub-e2e-desktop npm run test:security ;;
  backend) exec podman exec familyhub-e2e-desktop npm run test:backend ;;
  audit) exec podman exec familyhub-e2e-desktop npm run test:audit ;;
  a11y) exec podman exec familyhub-e2e-desktop npm run test:a11y ;;
  remove) exec podman pod rm -f familyhub-e2e ;;
  start) ;;
  *) echo 'Aufruf: bash start-lab.sh start|stop|status|logs|test|smoke|extended|security|backend|audit|a11y|remove'; exit 2 ;;
esac
if podman pod exists familyhub-e2e; then
  podman pod start familyhub-e2e
  echo 'Testlabor: http://localhost:6080'; exit 0
fi
revision=fb02978db0e9e1d202ba32e92241f32cc34f8272
if [ ! -d source ]; then git clone https://github.com/JNeugaertner/Family-Hub.git source; fi
git -C source fetch origin "$revision"
git -C source checkout --detach "$revision"
cp app/nginx.conf source/podman-nginx.conf
printf '.git\nlocal.properties\n**/node_modules\ntarget\nfrontend/dist\n' > source/.containerignore
podman build --target backend -f app/Containerfile -t localhost/familyhub-e2e-backend:local source
podman build --target frontend -f app/Containerfile -t localhost/familyhub-e2e-frontend:local source
podman build -t localhost/familyhub-e2e-desktop:local .
podman volume create familyhub-e2e-data
podman volume create familyhub-e2e-reports
podman pod create --name familyhub-e2e -p 127.0.0.1:6080:6080 -p 127.0.0.1:18080:8081
podman run -d --pod familyhub-e2e --name familyhub-e2e-db -v familyhub-e2e-data:/data/db docker.io/library/mongo:8.0 --bind_ip 127.0.0.1
ready=false
for i in {1..90}; do
  if podman exec familyhub-e2e-db mongosh --quiet --eval 'quit(db.adminCommand({ping:1}).ok === 1 ? 0 : 1)' >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
$ready || { echo 'MongoDB nicht bereit'; exit 1; }
podman run -d --pod familyhub-e2e --name familyhub-e2e-backend -e SPRING_MONGODB_URI=mongodb://127.0.0.1:27017/familyhub_e2e -e FAMILYHUB_SAMPLEDATA_ENABLED=false -e JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Berlin localhost/familyhub-e2e-backend:local
podman run -d --pod familyhub-e2e --name familyhub-e2e-frontend localhost/familyhub-e2e-frontend:local
ready=false
for i in {1..90}; do
  if podman exec familyhub-e2e-frontend wget -qO- http://127.0.0.1:8081/api/health >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
$ready || { echo 'Backend nicht bereit'; exit 1; }
podman run -d --pod familyhub-e2e --name familyhub-e2e-desktop --shm-size 1g -v familyhub-e2e-reports:/tests/reports localhost/familyhub-e2e-desktop:local
echo 'Testlabor: http://localhost:6080 | Testanwendung: http://localhost:18080'
