FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /src/build/libs/*.jar /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
