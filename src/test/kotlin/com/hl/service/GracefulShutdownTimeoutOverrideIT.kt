package com.hl.service

import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Duration

/**
 * Proves Story 3.4's second I/O matrix row: `spring.lifecycle.timeout-per-shutdown-phase`
 * (`src/main/resources/application.yaml`) resolves to the
 * `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` env var when set, not the
 * checked-in 30s default -- same `@DynamicPropertySource` technique
 * `OtlpTracingExportIT` uses to give a scenario its own Spring context,
 * distinct from every default-profile Integration Test's shared, cached one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
class GracefulShutdownTimeoutOverrideIT(
    @Value("\${spring.lifecycle.timeout-per-shutdown-phase}") val shutdownTimeout: Duration,
) : IntegrationTestBase() {
    @Test
    fun `phase timeout is overridable via SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE`() {
        assertThat(shutdownTimeout).isEqualTo(Duration.ofSeconds(5))
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun overrideShutdownTimeout(registry: DynamicPropertyRegistry) {
            registry.add("SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE") { "5s" }
        }
    }
}
