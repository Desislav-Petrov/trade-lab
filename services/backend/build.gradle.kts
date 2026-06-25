import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.13.0"
}

group = "org.dpp.tradelab"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

repositories {
    mavenCentral()
}

// ── OpenAPI generation ───────────────────────────────────────────────────────
// One task per domain. Add a new task block for each new domain YAML.

val generateUserApi = tasks.register<GenerateTask>("generateUserApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/user-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/user")
    apiPackage.set("org.dpp.tradelab.user.generated.api")
    modelPackage.set("org.dpp.tradelab.user.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}

val generateLedgerApi = tasks.register<GenerateTask>("generateLedgerApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/ledger-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/ledger")
    apiPackage.set("org.dpp.tradelab.ledger.generated.api")
    modelPackage.set("org.dpp.tradelab.ledger.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}

val generateMarketdataApi = tasks.register<GenerateTask>("generateMarketdataApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/marketdata-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/marketdata")
    apiPackage.set("org.dpp.tradelab.marketdata.generated.api")
    modelPackage.set("org.dpp.tradelab.marketdata.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}

// Wire generated sources into the compile classpath
// Exclude the org.openapitools scaffolding that the generator always emits
sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get()}/generated/user/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/ledger/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/marketdata/src/main/kotlin")
            exclude("org/openapitools/**")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateUserApi, generateLedgerApi, generateMarketdataApi)
}

// ── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.28")
    // Spring Boot Admin version must mirror Spring Boot major.minor (4.1.x → SB 4.1.x)
    implementation("de.codecentric:spring-boot-admin-starter-server:4.1.1")
    implementation("de.codecentric:spring-boot-admin-starter-client:4.1.1")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
