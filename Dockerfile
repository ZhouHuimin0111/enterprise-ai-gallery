FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --uid 10001 --create-home gallery && mkdir -p /data/objects && chown -R gallery:gallery /app /data
COPY --from=build /workspace/target/enterprise-ai-gallery-1.0.0.jar /app/app.jar
USER gallery
EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Djava.awt.headless=true","-jar","/app/app.jar"]
