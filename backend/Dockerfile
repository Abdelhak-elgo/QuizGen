# ============================================================
# QuizGen — Spring Boot API Dockerfile
# Multi-stage build pour image légère
# ============================================================

# Stage 1 : Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Stage 2 : Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S quizgen && adduser -S quizgen -G quizgen
USER quizgen

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
