FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /app

COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/

RUN mvn dependency:go-offline -pl auth-service -am -B

COPY common/src common/src
COPY auth-service/src auth-service/src

RUN mvn clean package -pl auth-service -am -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY --from=builder /app/auth-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
