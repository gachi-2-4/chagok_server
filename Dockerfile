# syntax=docker/dockerfile:1

# ---- 빌드 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 캐시를 살리기 위해 빌드 설정을 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null

COPY src src
# 테스트는 CI/로컬에서 따로 돌리므로 이미지 빌드에서는 건너뛴다.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /workspace/app.jar

# ---- 실행 ----
FROM eclipse-temurin:21-jre
# 서류 PDF를 이미지로 바꿀 때(PDFBox) 글꼴이 필요하다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core fonts-nanum \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 chagok

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER chagok

ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
