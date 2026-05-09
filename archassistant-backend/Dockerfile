FROM eclipse-temurin:17-jdk-alpine

# ← ИСПРАВЛЕНО: устанавливаем curl для healthcheck
RUN apk add --no-cache curl

WORKDIR /app

# Копируем JAR (предполагается, что он уже собран локально)
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]