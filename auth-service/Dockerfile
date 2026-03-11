FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Force TLS 1.2 — newer JDKs default to TLS 1.3 which some network middleboxes
# (proxies, firewalls) mishandle, causing "bad_record_mac" download failures.
ENV MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2"

COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY api-gateway/pom.xml api-gateway/
COPY wallet-service/pom.xml wallet-service/

# --mount=type=cache keeps the Maven local repo across builds so dependencies
# are only downloaded once, not on every docker build.
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -pl auth-service -am -B

COPY common/src common/src
COPY auth-service/src auth-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn package -pl auth-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY --from=builder /app/auth-service/target/*.jar app.jar
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
