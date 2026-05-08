FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache curl unzip bash

ENV KOTLIN_VERSION=1.9.20
ENV KOTLIN_HOME=/opt/kotlin
ENV KOTLIN_COMPILER_HOME=/opt/kotlin
ENV PATH=/opt/kotlin/bin:/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

RUN set -eux; \
    mkdir -p /opt/kotlin /tmp/kotlin-dist; \
    curl -fLso /tmp/kotlin-compiler.zip \
      "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"; \
    unzip -q /tmp/kotlin-compiler.zip -d /tmp/kotlin-dist; \
    KOTLINC_SRC_DIR="$(find /tmp/kotlin-dist -maxdepth 3 -type d -name kotlinc | head -n 1)"; \
    test -n "$KOTLINC_SRC_DIR"; \
    cp -R "$KOTLINC_SRC_DIR"/. /opt/kotlin/; \
    find /opt/kotlin -type f -exec chmod a+rX {} +; \
    rm -rf /tmp/kotlin-compiler.zip /tmp/kotlin-dist

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "app.jar"]