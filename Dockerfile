# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom first and download dependencies separately.
# This layer is cached — rebuilds after code changes skip re-downloading everything.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---------- Stage 2: slim runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# attempt-service: 8083
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]