# ==============================================================================
# Stage 1: Build stage
# ==============================================================================
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper and POM configuration first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings in case repository was checked out on Windows with CRLF
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Pre-fetch project dependencies for offline caching
RUN ./mvnw dependency:go-offline -B || true

# Copy application source code
COPY src/ ./src/

# Fallback: if application.properties is missing, use application.properties.example
RUN if [ ! -f src/main/resources/application.properties ]; then \
      cp src/main/resources/application.properties.example src/main/resources/application.properties; \
    fi

# Build executable Spring Boot fat JAR, skipping tests for fast and reproducible builds
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Production Runtime stage
# ==============================================================================
FROM eclipse-temurin:17-jre-jammy AS runner

# Install curl for container healthcheck support
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Run as a dedicated non-root user for container security
RUN groupadd -r -g 1001 spring && \
    useradd -r -u 1001 -g spring -m -s /bin/sh spring

WORKDIR /app

# Copy entrypoint script to convert cloud DATABASE_URL to JDBC on container boot
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh && \
    chmod +x /usr/local/bin/docker-entrypoint.sh

# Prepare directories for uploads and set permissions for non-root user
RUN mkdir -p /app/uploads && \
    chown -R spring:spring /app

# Copy the generated executable Spring Boot JAR from builder stage
COPY --from=builder --chown=spring:spring /build/target/backend-*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose default port (Render will override via dynamic $PORT)
EXPOSE 8080

# Configure JVM flags optimized for container environments (cgroups memory limits)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Container healthcheck using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Launch using entrypoint wrapper for automatic cloud database URL conversion
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
