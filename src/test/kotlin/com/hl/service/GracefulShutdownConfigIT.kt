package com.hl.service

import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/**
 * Proves Story 3.4's first I/O matrix row: the runtime configuration itself
 * -- `server.shutdown` (`src/main/resources/application.yaml`) and
 * `spring.lifecycle.timeout-per-shutdown-phase` -- resolves to `graceful`
 * and the documented 30s default, independent of [GracefulShutdownIT]'s
 * behavioral proof that a request actually drains correctly.
 *
 * Styled like `OtlpTracingNoOpIT`'s
 * `management tracing sampling probability binds to its documented default`
 * test: a plain `@Value`-bound property assertion on the normal, shared
 * default-profile context -- no custom `@TestExecutionListeners` or
 * `@DirtiesContext`, since this test never closes the context itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("integration")
class GracefulShutdownConfigIT(
    @Value("\${server.shutdown}") val shutdownMode: String,
    @Value("\${spring.lifecycle.timeout-per-shutdown-phase}") val shutdownTimeout: Duration,
) : IntegrationTestBase() {
    @Test
    fun `runtime configuration exposes graceful shutdown with its documented 30s default phase timeout`() {
        assertThat(shutdownMode).isEqualTo("graceful")
        assertThat(shutdownTimeout).isEqualTo(Duration.ofSeconds(30))
    }
}
