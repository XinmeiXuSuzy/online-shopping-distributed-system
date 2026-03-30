# ============================================================
# Multi-stage layered JAR Dockerfile
# Stage 1: Extract Spring Boot layers for optimal cache reuse
# Stage 2: Minimal JRE runtime image
# ============================================================

# ---- Stage 1: Layer extraction ----
FROM eclipse-temurin:17-jre-alpine AS builder
WORKDIR /app

COPY shopping-api/target/*.jar app.jar

# Spring Boot layered JAR: dependencies change less often than application code
# so Docker layer caching keeps the rebuild of the application layer fast.
RUN java -Djarmode=layertools -jar app.jar extract

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy layers in order of expected change frequency (least → most)
COPY --from=builder /app/dependencies/          ./
COPY --from=builder /app/spring-boot-loader/    ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/           ./

EXPOSE 8080

# Health check for ECS
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
