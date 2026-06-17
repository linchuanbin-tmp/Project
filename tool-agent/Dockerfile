# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY user-service/pom.xml user-service/
COPY gateway-service/pom.xml gateway-service/
COPY task-service/pom.xml task-service/
COPY tool-agent/pom.xml tool-agent/

RUN mvn install -N -q

COPY tool-agent/src tool-agent/src
RUN mvn package -pl tool-agent -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/tool-agent/target/*.jar app.jar
ENV JAVA_OPTS="-Xmx256m -Xms128m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
