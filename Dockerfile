# Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl is used by the docker-compose healthcheck against /api/health.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/webhook-service.jar ./app.jar
EXPOSE 8080

# Container-aware JVM tuning (Demo 6 second-wave optimization).
# MaxRAMPercentage lets the JVM size the heap from the container cgroup limit
# instead of host RAM; G1GC keeps p99 pause times low under the k6 hot-path load;
# ExitOnOutOfMemoryError makes the orchestrator restart a wedged instance instead
# of letting it limp along with a poisoned heap.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
