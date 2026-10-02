FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -ntp -f backend/pom.xml dependency:resolve
COPY backend/src backend/src
RUN mvn -B -ntp -f backend/pom.xml package -DskipTests
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/target/backend-1.0.0.jar /app/backend.jar
ENV MEDIA_DIR=/data/media
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:ActiveProcessorCount=1"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/backend.jar"]
