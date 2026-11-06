FROM gradle:8.11.1-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
