# FIRSTORY 백엔드 이미지. 개발 서버(Railway)용이며 SPRING_PROFILES_ACTIVE로 프로필을 고른다.
# 환경 변수 전체는 docs/deploy/railway.md
#
#   docker build -t firstory-backend .
#   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev -e DB_URL=... firstory-backend

# 1단계: Gradle로 실행 jar(bootJar)를 만든다. 테스트는 Testcontainers(Docker)가 필요해 여기서 돌리지 않는다
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 층을 먼저 만들어 소스만 바뀌면 다시 받지 않게 한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: JRE만 있는 이미지에서 실행한다
FROM eclipse-temurin:21-jre
WORKDIR /app

# root가 아닌 사용자로 돈다. 그림 파일은 /app/data/storage (STORAGE_LOCAL_ROOT 기본값 ./data/storage)
RUN groupadd --system firstory && useradd --system --gid firstory firstory \
    && mkdir -p /app/data/storage && chown -R firstory:firstory /app
COPY --from=build --chown=firstory:firstory /workspace/build/libs/*.jar /app/app.jar
USER firstory

# 컨테이너 메모리 한도에 맞춰 힙을 정한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# 서버 포트는 SERVER_PORT (없으면 PORT, 둘 다 없으면 8080). application.yml의 server.port
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
