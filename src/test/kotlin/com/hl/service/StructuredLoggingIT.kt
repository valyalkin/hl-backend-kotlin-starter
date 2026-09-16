package com.hl.service

import com.fasterxml.jackson.databind.JsonNode
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
import org.springframework.test.web.servlet.client.RestTestClient
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Proves Story 3.3's structured JSON logging contract for the default
 * (non-local) profile: `logging.structured.format.console: ecs`
 * (`src/main/resources/application.yaml`, mirrored in
 * `src/test/resources/application.yaml`, AD-21) turns every stdout log line
 * into single-line ECS JSON, and a line emitted while a request's span is
 * active also carries that span's trace id and span id -- sourced
 * automatically from the MDC entries Story 3.2's `Slf4JEventListener`
 * already populates, with no MDC wiring added by this story.
 *
 * Triggers `GlobalExceptionHandler`'s SYSTEM_ERROR log path (line 93) the
 * same way `RedisDownIT` does -- a real HTTP request that fails because
 * Redis is unreachable -- but without stopping `IntegrationTestBase`'s
 * JVM-shared Redis Testcontainer (`RedisDownIT`'s own class doc explains why
 * that shared singleton must never be stopped: every later Integration Test
 * in the suite would be left unable to reconnect). [BrokenRedisConnectionConfig]
 * instead `@Primary`-overrides the `DataRedisConnectionDetails` bean, for
 * this class's own, separately cached Spring context only -- see its own
 * doc for why a plain `spring.data.redis.port` property override has no
 * effect here.
 *
 * Console capture is [consoleAppender]/[TeeOutputStream] (`support` package)
 * -- see their doc for why this, not `System.out` redirection or a
 * `ListAppender`, is what actually observes `logging.structured.format.console`'s
 * real encoded output.
 *
 * Field names/locations (`@timestamp`, `log.level`, `log.logger`, `message`,
 * top-level `traceId`/`spanId`) are pinned exactly as observed by inspecting
 * real `ecs`-formatted output during implementation (Design Notes):
 * `ElasticCommonSchemaStructuredLogFormatter` spreads every MDC entry --
 * including Story 3.2's `traceId`/`spanId` -- as its own top-level JSON
 * field, while `level`/`logger` are nested under a `log` object. `traceId`
 * is a 32-character lowercase hex OTel trace id, `spanId` a 16-character
 * lowercase hex OTel span id -- confirmed against real captured output
 * (`docker compose up`, `./gradlew bootRun`, a forced Redis-down 500).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(BrokenRedisConnectionConfig::class)
@Tag("integration")
class StructuredLoggingIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    // A plain instance, not an injected bean: this app's context has no
    // general-purpose ObjectMapper bean to autowire (Spring MVC's own
    // request/response Jackson integration doesn't expose one), and parsing
    // captured console bytes is a test-local concern independent of the
    // application's own (de)serialization config.
    private val objectMapper = ObjectMapper()

    @Test
    fun `a request that logs during an active span emits single-line ECS JSON carrying trace and span id`() {
        val consoleAppender = consoleAppender()
        val originalStream = consoleAppender.outputStream
        val captured = ByteArrayOutputStream()

        val responseBody: String
        consoleAppender.outputStream = TeeOutputStream(originalStream, captured)
        try {
            responseBody =
                client
                    .get()
                    .uri("/api/v1/widgets/${UUID.randomUUID()}")
                    .exchange()
                    .expectStatus()
                    .isEqualTo(500)
                    .expectBody()
                    .returnResult()
                    .responseBody
                    ?.let { String(it) }
                    ?: error("empty GET /api/v1/widgets/{id} body")
        } finally {
            consoleAppender.outputStream = originalStream
        }

        val expectedTraceId = objectMapper.readTree(responseBody).path("traceId").asText()
        assertThat(expectedTraceId)
            .describedAs("traceId on the 500 response body")
            .isNotBlank()

        val jsonLines =
            captured
                .toString(StandardCharsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching { objectMapper.readTree(line) }.getOrNull() }
                .toList()
        assertThat(jsonLines)
            .describedAs("stdout lines captured during the request, parsed as single-line JSON")
            .isNotEmpty()
        jsonLines.forEach { line -> assertHasTimestampLevelLoggerAndMessage(line) }

        val correlatedLines = jsonLines.filter { it.path("traceId").asText() == expectedTraceId }
        assertThat(correlatedLines)
            .describedAs("JSON lines carrying this request's traceId (%s)", expectedTraceId)
            .isNotEmpty()
        correlatedLines.forEach { line ->
            // A 16-character lowercase hex OTel span id, not just any
            // non-blank value: a formatter regression that emitted a
            // constant placeholder for every line would still satisfy a
            // bare isNotBlank() check, but not this shape (and not the
            // 32-character traceId's own shape, so the two can never
            // collide).
            assertThat(line.path("spanId").asText())
                .describedAs("spanId on %s", line)
                .matches("[0-9a-f]{16}")
        }
    }

    private fun assertHasTimestampLevelLoggerAndMessage(line: JsonNode) {
        assertThat(line.has("@timestamp")).describedAs("@timestamp on %s", line).isTrue()
        assertThat(line.path("log").path("level").asText())
            .describedAs("log.level on %s", line)
            .isNotBlank()
        assertThat(line.path("log").path("logger").asText())
            .describedAs("log.logger on %s", line)
            .isNotBlank()
        assertThat(line.has("message")).describedAs("message on %s", line).isTrue()
    }
}
