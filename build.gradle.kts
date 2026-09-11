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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    // Boot 4 split spring-boot-autoconfigure into per-module artifacts; Flyway's
    // auto-configuration now lives in this dedicated module and is not pulled in by
    // flyway-core alone. BOM-managed, no version literal (AD-22).
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
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
