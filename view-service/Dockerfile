# view-service/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# [중요] 내 폴더(view-service) 안에 있는 파일들만 복사
COPY . .

RUN chmod +x ./gradlew
# 모듈 명칭(:view-service:) 없이 그냥 빌드 (독립 프로젝트니까요)
RUN ./gradlew clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# 이제 경로가 단순해집니다.
COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]