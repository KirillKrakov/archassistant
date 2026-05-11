FROM eclipse-temurin:17-jdk-alpine

# ← ИСПРАВЛЕНО: устанавливаем curl для healthcheck
RUN apk add --no-cache curl

WORKDIR /app

# Копируем JAR (предполагается, что он уже собран локально)
COPY build/libs/*.jar app.jar

EXPOSE 8080

# Healthcheck для Docker
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]