package com.hl.service

import com.hl.service.dto.WidgetRequest
import com.hl.service.support.IntegrationTestBase
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * Proves Story 3.2's no-op-by-default half of the contract -- OTLP trace
 * export driven entirely by
 * `management.opentelemetry.tracing.export.otlp.endpoint` /
 * `management.tracing.sampling.probability`, with zero application code.
 * See [OtlpTracingExportIT] for the complementary "endpoint configured" half.
 *
 * On the checked-in default profile (endpoint unset), serving a request must
 * not produce a single exporter-related WARNING/SEVERE log line. The
 * OpenTelemetry Java SDK's own exporter/sender internals log via
 * `java.util.logging`, not SLF4J/Logback (confirmed by inspecting
 * `OkHttpHttpSender`), so this attaches a JUL `Handler` rather than a Logback
 * `ListAppender` -- a Logback-only assertion would pass trivially even if the
 * exporter *did* try and fail to connect.
 *
 * Extends the shared [IntegrationTestBase] (AD-21).
 *
 * Styled like `PrometheusMetricsIT`/`WidgetHttpToStoreIT`: real HTTP via
 * `@AutoConfigureRestTestClient`'s `RestTestClient` against a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class OtlpTracingNoOpIT(
    @Autowired val client: RestTestClient,
    @Value("\${management.tracing.sampling.probability}") val samplingProbability: Double,
) : IntegrationTestBase() {
    private val capturedRecords = CopyOnWriteArrayList<LogRecord>()

    private val handler =
        object : Handler() {
            override fun publish(record: LogRecord) {
                if (record.level.intValue() >= Level.WARNING.intValue()) {
                    capturedRecords += record
                }
            }

            override fun flush() {}

            override fun close() {}
        }

    @BeforeEach
    fun attachJulHandler() {
        Logger.getLogger("").addHandler(handler)
    }

    @AfterEach
    fun detachJulHandler() {
        Logger.getLogger("").removeHandler(handler)
    }

    @Test
    fun `management tracing sampling probability binds to its documented default of 1_0`() {
        assertThat(samplingProbability).isEqualTo(1.0)
    }

    @Test
    fun `serving a request with no OTLP endpoint configured logs no exporter WARNING or SEVERE records`() {
        val name = "otlp-noop-${System.nanoTime()}"
        val createResult =
            client
                .post()
                .uri("/api/v1/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(WidgetRequest(name))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult()
        val createBody = createResult.responseBody?.let { String(it) } ?: error("empty POST /api/v1/widgets body")
        val id = JsonPath.read<String>(createBody, "$.id")
        client
            .get()
            .uri("/api/v1/widgets/$id")
            .exchange()
            .expectStatus()
            .isOk()

        val exporterRelated =
            capturedRecords.filter {
                // Scoped to OpenTelemetry's own loggers (e.g.
                // `io.opentelemetry.exporter.sender.okhttp.internal.OkHttpHttpSender`),
                // not a broad keyword match against message text: generic
                // terms like "span" or "export" could otherwise
                // coincidentally appear in an unrelated WARNING/SEVERE
                // record from some other JUL-based component and fail this
                // test for reasons unconnected to trace export.
                (it.loggerName ?: "").startsWith("io.opentelemetry")
            }
        assertThat(exporterRelated)
            .describedAs(
                "WARNING/SEVERE java.util.logging records mentioning tracing export: %s",
                exporterRelated.map { "${it.loggerName}: ${it.message}" },
            ).isEmpty()
    }
}
