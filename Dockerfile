# --- Build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Cache dependencies separately from source so a source-only change doesn't
# force Maven to re-download the world.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Run as a non-root user rather than the container default root.
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 5458
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
