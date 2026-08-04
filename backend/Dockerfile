FROM maven:3-eclipse-temurin-24 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN groupadd --system jobsrch \
    && useradd --system --gid jobsrch --home-dir /app jobsrch \
    && mkdir -p /app/data/resumes \
    && chown -R jobsrch:jobsrch /app
COPY --chown=jobsrch:jobsrch --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
USER jobsrch
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
