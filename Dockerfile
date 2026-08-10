FROM maven:3.9.16-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src

RUN mvn clean package

FROM quay.io/keycloak/keycloak:26.7.1

COPY --from=build /build/target/de.smf-SmsAuthenticator.jar /opt/keycloak/providers/
