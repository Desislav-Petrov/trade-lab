import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

val jwtVersion = "0.12.6"
val springBootAdminVersion = "4.1.1"
val kotestVersion = "5.9.1"
val kotestSpringVersion = "1.3.0"
val mockitoKotlinVersion = "5.4.0"
val swaggerVersion = "2.2.28"

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.13.0"
    id("com.google.cloud.tools.jib") version "3.4.0"
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

// ── Jib Docker configuration ───────────────────────────────────────────────────
val imageVersion = project.findProperty("imageVersion")?.toString() ?: "latest"

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    to {
        image = "ghcr.io/desislav-petrov/trade-lab-backend"
        tags = setOf(imageVersion, "latest")
    }
    container {
        jvmFlags = listOf(
            "-Xmx512m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200"
        )
        ports = listOf("8080")
        labels = mapOf(
            "org.opencontainers.image.version" to imageVersion,
            "org.opencontainers.image.revision" to (System.getenv("VCS_REF") ?: "unknown"),
            "org.opencontainers.image.created" to System.currentTimeMillis().toString()
        )
    }
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

val generateStocktradingApi = tasks.register<GenerateTask>("generateStocktradingApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/stocktrading-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/stocktrading")
    apiPackage.set("org.dpp.tradelab.stocktrading.generated.api")
    modelPackage.set("org.dpp.tradelab.stocktrading.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}

val generatePortfolioApi = tasks.register<GenerateTask>("generatePortfolioApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/portfolio-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/portfolio")
    apiPackage.set("org.dpp.tradelab.portfolio.generated.api")
    modelPackage.set("org.dpp.tradelab.portfolio.generated.model")
    configOptions.set(mapOf(
        "useSpringBoot3" to "true",
        "delegatePattern" to "true",
        "serializationLibrary" to "jackson",
        "enumPropertyNaming" to "UPPERCASE",
        "gradleBuildFile" to "false",
        "exceptionHandler" to "false"
    ))
}

val generateFinnhubApi = tasks.register<GenerateTask>("generateFinnhubApi") {
    generatorName.set("kotlin")
    inputSpec.set("${rootProject.projectDir}/../../services/contract/finnhub-openapi.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/finnhub")
    apiPackage.set("org.dpp.tradelab.marketdata.generated.finnhub.api")
    modelPackage.set("org.dpp.tradelab.marketdata.generated.finnhub.model")
    library.set("jvm-spring-restclient")
    configOptions.set(mapOf(
        "serializationLibrary" to "jackson",
        "gradleBuildFile" to "false",
        "enumPropertyNaming" to "UPPERCASE",
        "useSpringBoot3" to "true"
    ))
}

// Wire generated sources into the compile classpath
// Exclude the server-side scaffolding (HomeController, Application, SpringDocConfiguration)
// that the kotlin-spring generator always emits at the org.openapitools package root.
// Also exclude the Finnhub client infrastructure and api — FinnhubPriceFeedAdapter
// uses Spring RestClient directly; only the QuoteResponse model is needed.
sourceSets {
    main {
        kotlin {
            srcDir("${layout.buildDirectory.get()}/generated/user/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/ledger/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/marketdata/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/stocktrading/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/portfolio/src/main/kotlin")
            srcDir("${layout.buildDirectory.get()}/generated/finnhub/src/main/kotlin")
            exclude("org/openapitools/**")
            exclude("org/dpp/tradelab/marketdata/generated/finnhub/api/**")
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateUserApi, generateLedgerApi, generateMarketdataApi, generateStocktradingApi, generatePortfolioApi, generateFinnhubApi)
}

// ── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.swagger.core.v3:swagger-annotations:$swaggerVersion")
    implementation("io.jsonwebtoken:jjwt-api:$jwtVersion")
    implementation("de.codecentric:spring-boot-admin-starter-server:$springBootAdminVersion")
    implementation("de.codecentric:spring-boot-admin-starter-client:$springBootAdminVersion")
    runtimeOnly("com.h2database:h2")
    // PostgreSQL driver — used when the 'prod' profile is active (application-prod.yml).
    // Version managed by the Spring Boot BOM.
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jwtVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:$kotestSpringVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
