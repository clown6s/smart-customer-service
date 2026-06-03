# ============================================================
# 多阶段构建 — smart-customer-service
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build

# 先只复制 pom.xml，利用 Docker 层缓存，依赖不变时跳过下载
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -q

# 再复制源码并打包
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml clean package -DskipTests -q

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# 非 root 用户运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 从 builder 阶段复制 jar
COPY --from=builder /build/target/smart-customer-service-*.jar app.jar

# 日志目录
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

# JVM 容器感知参数
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+OptimizeStringConcat \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
