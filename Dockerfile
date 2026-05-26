FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -XX:+UseContainerSupport -XX:MaxRAMPercentage=${JAVA_MAX_RAM_PERCENTAGE:-75} -jar /app/app.jar"]
