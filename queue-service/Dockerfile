FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

COPY common/build.gradle common/
COPY auth-service/build.gradle auth-service/
COPY user-service/build.gradle user-service/
COPY ticket-service/build.gradle ticket-service/
COPY queue-service/build.gradle queue-service/

RUN mkdir -p common/src/main/java \
             auth-service/src/main/java \
             user-service/src/main/java \
             ticket-service/src/main/java \
             queue-service/src/main/java

RUN chmod +x ./gradlew
RUN ./gradlew :queue-service:dependencies --no-daemon

COPY common common
COPY queue-service queue-service

RUN ./gradlew :queue-service:clean :queue-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/queue-service/build/libs/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
