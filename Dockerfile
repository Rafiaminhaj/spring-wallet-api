# Use a lightweight and secure Eclipse Temurin JRE 21 runtime
FROM eclipse-temurin:21-jre-jammy

# Set working directory inside the container
WORKDIR /app

# Copy the pre-compiled JAR file from the local target/ directory
COPY target/api-1.0.0.jar app.jar

# Expose port 8080 for HTTP API access
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
