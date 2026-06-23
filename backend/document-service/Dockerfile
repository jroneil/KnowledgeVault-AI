# Multi-stage build for Spring Boot application

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/document-service-*.jar app.jar

# Add a user to run the application (not root)
RUN addgroup -S spring && adduser -S spring -G spring

# Create storage directory with proper permissions
RUN mkdir -p /app/storage && chown -R spring:spring /app/storage

# Switch to non-root user
USER spring:spring

# Expose the port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]