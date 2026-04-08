# 1단계: 빌드 스테이지
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 1. 루트(ticket) 프로젝트 설정 파일 복사
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

# 2. 각 모듈의 설정 파일(build.gradle) 복사
# user-service 소스는 필요 없어도, 명부에 적힌 모듈이므로 설정 파일은 필요합니다.
COPY common/build.gradle common/
COPY auth-service/build.gradle auth-service/
COPY user-service/build.gradle user-service/

# 3. [핵심] Gradle의 '출석 체크' 통과를 위해 빈 폴더 생성
# 소스 코드를 복사하는 대신, 폴더만 만들어 Gradle을 안심시킵니다.
RUN mkdir -p common/src/main/java \
             auth-service/src/main/java \
             user-service/src/main/java

# 4. 의존성 미리 다운로드 (캐싱)
RUN chmod +x ./gradlew
RUN ./gradlew :auth-service:dependencies --no-daemon

# 5. 실제 빌드에 필요한 모듈 소스만 복사
# user-service는 빌드 대상이 아니므로 복사하지 않습니다.
COPY common common
COPY auth-service auth-service

# 6. auth-service 전용 빌드 실행
RUN ./gradlew :auth-service:clean :auth-service:bootJar -x test --no-daemon

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 auth-service의 jar만 추출
COPY --from=build /app/auth-service/build/libs/*.jar app.jar

# JVM 최적화 옵션
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]