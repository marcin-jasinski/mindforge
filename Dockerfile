# syntax=docker/dockerfile:1

# 1 — the Angular SPA
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2 — the JAR, embedding the SPA under classpath:/static/
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=frontend /build/frontend/dist/frontend/browser ./frontend/dist/frontend/browser
RUN mvn -q -B package -DskipTests -Djacoco.skip=true

# 3 — the runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S mindforge && adduser -S mindforge -G mindforge
COPY --from=backend /build/target/mindforge-*.jar app.jar
USER mindforge
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
