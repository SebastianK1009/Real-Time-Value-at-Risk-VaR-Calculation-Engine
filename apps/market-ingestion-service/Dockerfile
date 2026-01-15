# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the shaded jar
COPY --from=builder /app/target/market-ingestion-service-1.0.0.jar app.jar

# Environment defaults
ENV SIMULATOR_HOST=market-data-simulator
ENV SIMULATOR_PORT=9999
ENV KAFKA_BOOTSTRAP_SERVERS=kafka:9092
ENV KAFKA_TOPIC=market_data
ENV RETRY_INTERVAL=5

# Run
CMD ["java", "-jar", "app.jar"]
