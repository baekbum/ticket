# 1단계: 빌드 스테이지
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 1. 루트 프로젝트 설정 파일 복사 (gradlew 및 설정파일)
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

# 2. 모든 모듈의 build.gradle 복사 (멀티 모듈 구조 유지를 위해)
COPY common/build.gradle common/
COPY auth-service/build.gradle auth-service/
COPY user-service/build.gradle user-service/

# 3. Gradle 구조 인식을 위한 최소한의 빈 폴더 생성
RUN mkdir -p common/src/main/java \
             auth-service/src/main/java \
             user-service/src/main/java

# 4. 의존성 미리 다운로드 (캐싱을 통한 속도 향상)
RUN chmod +x ./gradlew
RUN ./gradlew :user-service:dependencies --no-daemon

# 5. 실제 빌드에 필요한 모듈 소스만 복사 (common은 의존성이므로 포함)
COPY common common
COPY user-service user-service

# 6. user-service 빌드 실행
# QueryDSL QClass 생성도 이 과정(compileJava)에서 자동으로 포함됩니다.
RUN ./gradlew :user-service:clean :user-service:bootJar -x test --no-daemon

# 2단계: 실행 스테이지 (JRE만 포함하여 용량 최적화)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 user-service의 jar만 복사
COPY --from=build /app/user-service/build/libs/*.jar app.jar

# JVM 및 인코딩 설정 (한국어 처리 등을 위해 UTF-8 권장)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]