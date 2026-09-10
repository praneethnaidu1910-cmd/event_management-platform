# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy wrapper and POM first so dependency resolution is cached
# separately from source changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline -B

COPY src src
RUN ./mvnw -q package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /app/target/event-management-*.jar app.jar
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
