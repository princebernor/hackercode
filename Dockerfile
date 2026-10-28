# Multi-stage build: React client + Spring Boot jar, then a small JRE runtime image.
FROM node:22-alpine AS client
WORKDIR /build/client
COPY client/package.json client/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY client/ ./
RUN mkdir -p ../target/classes/static && npm run build

FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q -Dskip.frontend=true dependency:go-offline
COPY src/ src/
COPY --from=client /build/target/classes/static target/classes/static
RUN mvn -B -q -Dskip.frontend=true -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 hackercode
WORKDIR /app
COPY --from=server /build/target/hackercode-1.0.0.jar app.jar
USER hackercode
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
