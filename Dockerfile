FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/similar-products-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
