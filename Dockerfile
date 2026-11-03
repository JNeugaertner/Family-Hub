# FamilyHub als ein Docker-Image: Das Backend liefert die gebaute Oberfläche unter / mit aus.
# Bauen: docker build -t familyhub:lokal .
# Starten zusammen mit MongoDB: siehe deploy/compose.yaml und README („Mit Docker betreiben“).

# 1. Oberfläche bauen. Das Ergebnis ist für jeden Prozessor gleich, daher läuft dieser Schritt immer auf dem
#    Prozessor des bauenden Rechners (auch wenn das Image für einen Raspberry Pi gebaut wird).
FROM --platform=$BUILDPLATFORM docker.io/library/node:24-slim AS frontend
RUN npm install -g pnpm@10.34.3
WORKDIR /src/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

# 2. Backend bauen; die Oberfläche kommt nach static/ und landet so im JAR. Die Tests laufen hier nicht
#    (sie brauchen eine eingebettete MongoDB), sondern vorher mit ./mvnw test.
FROM --platform=$BUILDPLATFORM docker.io/library/eclipse-temurin:25-jdk AS backend
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Abhängigkeiten in einer eigenen Schicht laden: Sie bleiben im Cache, solange pom.xml gleich bleibt
RUN sh mvnw -B -q dependency:go-offline
COPY src/main src/main
COPY --from=frontend /src/frontend/dist src/main/resources/static
RUN sh mvnw -B -q -DskipTests package

# 3. Laufzeit: nur die Java-Laufzeit und das JAR, ausgeführt ohne Root-Rechte
FROM docker.io/library/eclipse-temurin:25-jre
# Uhrzeiten wie die Müll-Aufgaben um 07:50 Uhr, die Erinnerungen und „heute“ gelten in dieser Zeitzone.
# MongoDB speichert Termine umgerechnet nach UTC: Die Zeitzone später nicht mehr ändern.
ENV TZ=Europe/Berlin
RUN useradd --system --uid 10001 --home-dir /app familyhub
WORKDIR /app
COPY --from=backend /src/target/familyhub-backend-*.jar familyhub.jar
USER familyhub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/familyhub.jar"]
