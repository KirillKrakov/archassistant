import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    runtimeOnly("org.postgresql:postgresql:42.6.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("com.tngtech.archunit:archunit:1.4.2")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")

    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M4"))
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

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

tasks.named<BootJar>("bootJar") {
    requiresUnpack("**/kotlin-compiler-embeddable-*.jar")
}