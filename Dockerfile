# syntax=docker/dockerfile:1
# One image, one process: Spring Boot serves the API and the built React app from the same origin.
# Build:  docker build -t yuroom .
# Run:    see docker-compose.yml (profile "full") or docs/deploy.md

# ---- 1. Frontend: type-check and build static files ----
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. Backend: build the jar with the frontend inside it ----
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /src/backend
# Dependencies first, so this layer is cached until build.gradle changes.
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null
COPY backend/src src
COPY --from=frontend /src/frontend/dist src/main/resources/static
# Tests already ran in CI against a real Postgres; the image build only packages.
RUN ./gradlew --no-daemon -q bootJar \
 && java -Djarmode=tools -jar build/libs/yuroom-*.jar extract --layers --launcher --destination build/extracted

# ---- 3. Runtime: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S yuroom && adduser -S yuroom -G yuroom
WORKDIR /app
# Layered: dependencies change rarely, our code often, so redeploys only push the small top layer.
COPY --from=backend /src/backend/build/extracted/dependencies/ ./
COPY --from=backend /src/backend/build/extracted/spring-boot-loader/ ./
COPY --from=backend /src/backend/build/extracted/snapshot-dependencies/ ./
COPY --from=backend /src/backend/build/extracted/application/ ./
# Synthetic demo timetable (scripts/GenerateDemoTimetable.java). Real York data is never baked into an image (no redistribution);
# with permission, point INGESTION_FILE_PATH at a mounted file and set DEMO_DATA=false.
COPY data/sample/timetable-demo.csv /app/data/timetable.csv
USER yuroom

ENV SPRING_PROFILES_ACTIVE=prod \
    DEMO_DATA=true \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
