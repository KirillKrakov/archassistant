FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl ca-certificates

WORKDIR /app

COPY build/libs/*.jar app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]