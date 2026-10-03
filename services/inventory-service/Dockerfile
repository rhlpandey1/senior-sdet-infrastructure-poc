FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/inventory-service-1.0.0.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
