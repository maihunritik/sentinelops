# Multi-stage build shared by both services: docker build --build-arg SERVICE=ingest-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /src
COPY pom.xml .
COPY common/pom.xml common/
COPY ingest-service/pom.xml ingest-service/
COPY analyzer-service/pom.xml analyzer-service/
RUN mvn -B -q dependency:go-offline -pl ${SERVICE} -am || true
COPY . .
RUN mvn -B -q package -pl ${SERVICE} -am -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*.jar /app.jar \
 && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
