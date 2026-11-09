# Build the executable Spring Boot JAR.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .

RUN chmod +x ./mvnw \
    && ./mvnw -B clean package -DskipTests

# Run the app in a smaller, non-root Java 21 image.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
