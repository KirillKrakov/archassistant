import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.5"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Database
    runtimeOnly("org.postgresql:postgresql:42.6.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // ArchUnit
    implementation("com.tngtech.archunit:archunit:1.4.2")

    // Kotlin compiler (для компиляции сгенерированного кода)
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")

    // Jackson Kotlin module — для корректной сериализации data class
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // YAML support
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")

    // Spring AI (через BOM)
    // Импорт BOM для управления версиями всех компонентов Spring AI
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M4"))
    // Стартеры без явной версии — версия подхватится из BOM
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
    // Для локальных моделей (опционально):
    // implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

    // SpringDoc документация
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Поддержка Java 8 date/time в Jackson (для LocalDateTime, Instant, etc.)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")

    // Structured logging (Logstash encoder)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("com.h2database:h2:2.2.224")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}