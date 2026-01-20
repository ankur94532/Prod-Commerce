# Use a small JRE image (Java 21, matches Spring Boot 3.x nicely)
FROM eclipse-temurin:21-jre-jammy

# Set working directory
WORKDIR /app

# Copy the built JAR from Maven target folder into the image
COPY target/catalog-service-0.0.1-SNAPSHOT.jar app.jar

# Cart service listens on 8083 (as in application.yml)
EXPOSE 8082

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
