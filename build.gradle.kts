plugins {
    id("java")
    id("org.springframework.boot") version "3.5.6"

    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.spring") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.jpa") version "2.2.20"
}

group = "ru.practice"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/main/kotlin")
        }
        test {
            kotlin.srcDir("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    // REST API
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Работа с БД через JPA / Hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Валидация входных данных
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.3"))
    // Миграции БД
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Документация REST API / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    // Тестирование
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-core")

    // Интеграционные тесты с настоящей PostgreSQL в контейнере
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
tasks.register("printTestcontainersDetails") {
    doLast {
        val config = configurations.getByName("testRuntimeClasspath")

        println("\n--- RESOLUTION STRATEGY ---")
        println("Forced: ${config.resolutionStrategy.forcedModules}")

        println("\n--- DEPENDENCY CONSTRAINTS ---")
        config.allDependencyConstraints.forEach {
            println("$it | version: ${it.version}")
        }

        println("\n--- RESOLVED TESTCONTAINERS ---")
        config.resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.group == "org.testcontainers" }
            .forEach {
                println("${it.moduleVersion.id}")
            }
    }
}