FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2"

COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY api-gateway/pom.xml api-gateway/
COPY wallet-service/pom.xml wallet-service/

RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -pl wallet-service -am -B

COPY common/src common/src
COPY wallet-service/src wallet-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl wallet-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /app/wallet-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
