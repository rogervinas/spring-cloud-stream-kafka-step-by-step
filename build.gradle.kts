import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED

plugins {
  id("org.springframework.boot") version "3.4.5"
  id("io.spring.dependency-management") version "1.1.7"
  kotlin("jvm") version "2.1.20"
  kotlin("plugin.spring") version "2.1.20"
}

group = "com.rogervinas"
version = "0.0.1-SNAPSHOT"

repositories {
  mavenCentral()
}

val springCloudVersion = "2024.0.1"
val testContainersVersion = "1.21.0"

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.cloud:spring-cloud-starter-stream-kafka")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
  testImplementation("org.testcontainers:testcontainers:$testContainersVersion")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
  testImplementation("org.awaitility:awaitility:4.3.0")
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events(PASSED, SKIPPED, FAILED)
    exceptionFormat = FULL
    showExceptions = true
    showCauses = true
    showStackTraces = true
  }
}
