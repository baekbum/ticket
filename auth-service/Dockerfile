# 1단계: 빌드 스테이지 (JDK 21 사용)
# alpine 버전은 용량이 작아 빌드 속도와 이미지 크기 최적화에 유리합니다.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 빌드 캐시 효율을 위해 래퍼와 설정 파일 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .

# 라이브러리 미리 다운로드 (소스 코드 변경 시에도 라이브러리 층은 캐싱됨)
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 실제 소스 코드 복사 및 빌드
COPY src src
RUN ./gradlew clean bootJar -x test --no-daemon

# 2단계: 실행 스테이지 (JRE 21 사용)
# 실행 시에는 JDK가 아닌 가벼운 JRE만 포함된 이미지를 사용합니다.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 최종 결과물(jar)만 추출
# --from=build가 말씀하신 "임시 컨테이너 결과물 받기" 핵심입니다.
COPY --from=build /app/build/libs/*.jar app.jar

# JVM 최적화 옵션 (Java 21의 가상 스레드 등을 고려한 기본 설정)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]