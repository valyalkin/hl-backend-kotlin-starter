import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spotless)
}

group = "com.hl"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(25)

    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        allWarningsAsErrors = true
    }
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("src/**/*.kt")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Renders /actuator/prometheus in Prometheus text format from the metrics
    // Actuator already collects (Story 3.1, AD-17). BOM-managed, no version
    // literal (AD-22).
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Bridges Micrometer's tracing API onto the OpenTelemetry SDK and adds the
    // OTLP/HTTP span exporter, both driven purely by `management.*` properties
    // (Story 3.2) -- no `@Configuration`/`SpanExporter` code. BOM-managed, no
    // version literal (AD-22).
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    // Boot 4 split spring-boot-autoconfigure into per-module artifacts; the
    // OTLP tracing auto-configuration (including the `OtlpHttpSpanExporter`
    // bean, created only once `management.opentelemetry.tracing.export.otlp.endpoint`
    // is set) lives in this dedicated module, mirroring the Flyway split
    // below. BOM-managed, no version literal (AD-22).
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Data JPA reflects on WidgetEntity's constructor via kotlin-reflect
    // because it is a Kotlin class; without it, repository bean creation fails
    // at runtime with NoClassDefFoundError: kotlin/reflect/full/KClasses. Story
    // 2.1 never exercised this path (test scope excluded JPA autoconfiguration
    // entirely); Story 2.2's real Postgres-backed context startup is what
    // surfaces it. Version aligned to the Kotlin Gradle plugin automatically,
    // no literal needed.
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    // Boot 4 split spring-boot-autoconfigure into per-module artifacts; Flyway's
    // auto-configuration now lives in this dedicated module and is not pulled in by
    // flyway-core alone. BOM-managed, no version literal (AD-22).
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Enables @Valid/Bean Validation processing (Story 2.6). BOM-managed, no
    // version literal (AD-22).
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Enables @EnableCaching/RedisCacheManager autoconfiguration for the
    // cache-aside read path (Story 2.7). BOM-managed, no version literal
    // (AD-22).
    implementation("org.springframework.boot:spring-boot-starter-cache")
    // Serves /v3/api-docs on every profile and /swagger-ui/** (gated by
    // springdoc.swagger-ui.enabled) via auto-configuration alone -- no
    // hand-written OpenApiCustomizer/GroupedOpenApi bean (Story 2.10).
    // Version from the catalog (springdocOpenapi = 3.1.1), no literal here
    // (AD-22-style single edit point).
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Shared JVM-wide Testcontainers base class (Story 2.2): @ServiceConnection
    // wiring plus the Postgres container. Both are BOM-managed, no version
    // literal (AD-22).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Testcontainers 2.x (pulled in transitively via the Spring Boot BOM's
    // nested testcontainers-bom import) renamed this module from the legacy
    // `org.testcontainers:postgresql` to `testcontainers-postgresql`.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    // `@Testcontainers`/`@Container` JUnit 5 extension (Story 2.8): only
    // `RedisDownIT`'s own, class-scoped Postgres+Redis pair uses this --
    // `IntegrationTestBase`'s JVM-shared containers are started/stopped by
    // hand instead (AD-21). BOM-managed, no version literal (AD-22).
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<BootRun>("bootRun") {
    // Default to the `local` profile so a bare `./gradlew bootRun` (Compose stack
    // up) connects to Postgres/Redis via application-local.yaml with no extra
    // setup (Story 1.7). An explicit SPRING_PROFILES_ACTIVE from the caller's
    // shell still wins over this default. Read via providers.environmentVariable
    // (not System.getenv) so this is a tracked configuration-cache input.
    val callerProfile = providers.environmentVariable("SPRING_PROFILES_ACTIVE")
    if (!callerProfile.isPresent) {
        environment("SPRING_PROFILES_ACTIVE", "local")
    }
}
