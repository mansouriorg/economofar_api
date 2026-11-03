FROM maven:3-eclipse-temurin-25-noble AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:25-jre-noble
RUN groupadd --system economofar && useradd --system --gid economofar --home-dir /app --shell /usr/sbin/nologin economofar && mkdir -p /app/uploads && chown -R economofar:economofar /app
WORKDIR /app
COPY --from=build --chown=economofar:economofar /build/app.jar /app/app.jar
USER economofar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
