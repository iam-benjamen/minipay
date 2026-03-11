FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/

RUN mvn dependency:go-offline -pl api-gateway -am -B

COPY common/src common/src
COPY api-gateway/src api-gateway/src

RUN mvn clean package -pl api-gateway -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
