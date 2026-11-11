FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Resolve dependencies first so source changes do not re-download them
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN ./gradlew --no-daemon --console=plain dependencies > /dev/null
COPY src/main src/main
RUN ./gradlew --no-daemon --console=plain bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/backend-api-1.0.0.jar app.jar
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
