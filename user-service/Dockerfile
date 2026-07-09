# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制所有 pom.xml（利用 Docker layer 缓存，依赖不变时不重新下载）
COPY pom.xml .
COPY user-service/pom.xml user-service/
COPY gateway-service/pom.xml gateway-service/
COPY task-service/pom.xml task-service/
COPY tool-agent/pom.xml tool-agent/
COPY rag-agent/pom.xml rag-agent/

# 安装父 pom（不构建子模块）
RUN mvn install -N -q

# 复制源码并构建
COPY user-service/src user-service/src
RUN mvn package -pl user-service -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/user-service/target/*.jar app.jar
ENV JAVA_OPTS="-Xmx256m -Xms128m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
