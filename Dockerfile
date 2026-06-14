# Stage 1: Build the application using Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Limit Maven's RAM usage to prevent Render from killing the build
ENV MAVEN_OPTS="-Xmx256m"
RUN mvn clean package -DskipTests

# Stage 2: Run the application using Java 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Copy the built jar from the previous stage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
