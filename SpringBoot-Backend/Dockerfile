# ─────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Cache dependencies layer separately
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build application
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────
# Stage 2: Runtime (minimal JRE image)
# ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking

# Copy built JAR
COPY --from=builder /build/target/banking-backend-*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R banking:banking /app

USER banking

# JVM tuning for containers
ENV JAVA_OPTS="-Xms256m -Xmx512m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
