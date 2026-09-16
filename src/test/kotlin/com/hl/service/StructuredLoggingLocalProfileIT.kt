package com.hl.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hl.service.support.BrokenRedisConnectionConfig
import com.hl.service.support.IntegrationTestBase
import com.hl.service.support.TeeOutputStream
import com.hl.service.support.consoleAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Proves Story 3.3's `local`-profile exception to [StructuredLoggingIT]:
 * `application-local.yaml` overrides `logging.structured.format.console`
 * back to an empty value, so console output stays Boot's default
 * human-readable pattern instead of single-line ECS JSON.
 *
 * Same request trigger ([BrokenRedisConnectionConfig]) and console-capture
 * technique ([consoleAppender]/[TeeOutputStream], `support` package) as
 * [StructuredLoggingIT] -- see their docs for why -- but under
 * `@ActiveProfiles("local")` (own, separately cached Spring context) and
 * asserting the opposite: none of the captured lines parse as JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(BrokenRedisConnectionConfig::class)
@ActiveProfiles("local")
@Tag("integration")
class StructuredLoggingLocalProfileIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    private val objectMapper = ObjectMapper()

    @Test
    fun `a request that logs under the local profile stays human-readable, not JSON`() {
        val consoleAppender = consoleAppender()
        val originalStream = consoleAppender.outputStream
        val captured = ByteArrayOutputStream()

        consoleAppender.outputStream = TeeOutputStream(originalStream, captured)
        try {
            client
                .get()
                .uri("/api/v1/widgets/${UUID.randomUUID()}")
                .exchange()
                .expectStatus()
                .isEqualTo(500)
        } finally {
            consoleAppender.outputStream = originalStream
        }

        val capturedLines =
            captured
                .toString(StandardCharsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
        assertThat(capturedLines)
            .describedAs("stdout lines captured during the request")
            .isNotEmpty()

        val jsonLines = capturedLines.filter { line -> runCatching { objectMapper.readTree(line) }.isSuccess }
        assertThat(jsonLines)
            .describedAs(
                "captured lines that parse as JSON -- expected none under the local profile: %s",
                jsonLines,
            ).isEmpty()
    }
}
