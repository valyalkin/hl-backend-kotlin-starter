package com.hl.service

import com.hl.service.dto.WidgetRequest
import com.hl.service.support.IntegrationTestBase
import com.jayway.jsonpath.JsonPath
import com.sun.net.httpserver.HttpServer
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Proves Story 3.2's "endpoint configured" half of the contract. See
 * [OtlpTracingNoOpIT]'s class doc for the pair's overall design.
 *
 * With the endpoint pointed at a same-JVM stub HTTP receiver (plain
 * `com.sun.net.httpserver.HttpServer`, no new test dependency), a request
 * that also exercises the widget's cache-aside read path must result in an
 * OTLP/HTTP POST of span data to that receiver.
 *
 * Extends the shared [IntegrationTestBase] (AD-21); additionally registers
 * the stub receiver's port via `@DynamicPropertySource` so the property is
 * present before the OTLP auto-configuration's `@ConditionalOnProperty`
 * check runs at context startup -- this deliberately gives it a different
 * Spring context (and thus a real `OtlpHttpSpanExporter` bean) than every
 * other Integration Test's shared, cached, endpoint-unset context.
 *
 * Styled like `PrometheusMetricsIT`/`WidgetHttpToStoreIT`: real HTTP via
 * `@AutoConfigureRestTestClient`'s `RestTestClient` against a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class OtlpTracingExportIT(
    @Autowired val client: RestTestClient,
    @Autowired val tracerProvider: SdkTracerProvider,
) : IntegrationTestBase() {
    @Test
    fun `configured OTLP endpoint receives exported spans for a request and its DB+cache calls`() {
        receivedRequests.clear()
        val name = "otlp-export-${System.nanoTime()}"

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
        // First read: cache miss, reaches WidgetRepository/Postgres, then
        // populates Redis (Story 2.7's cache-aside path) -- exercises both
        // outbound calls the AC asks for in one request.
        client
            .get()
            .uri("/api/v1/widgets/$id")
            .exchange()
            .expectStatus()
            .isOk()

        // Forces the BatchSpanProcessor to export immediately instead of
        // waiting for its periodic schedule delay -- keeps this
        // deterministic rather than sleeping for an arbitrary duration.
        tracerProvider.forceFlush().join(10, TimeUnit.SECONDS)

        assertThat(receivedRequests)
            .describedAs("POST requests received by the stub OTLP/HTTP receiver")
            .isNotEmpty()
        val body = receivedRequests.first()
        assertThat(body).isNotEmpty()
        // OTLP/HTTP's protobuf wire format writes string fields as raw UTF-8
        // bytes with no further encoding, so span/attribute values -- e.g.
        // the widget resource's route -- are found by a plain substring
        // search over the raw request body.
        val bodyAsLatin1 = String(body, StandardCharsets.ISO_8859_1)
        assertThat(bodyAsLatin1)
            .describedAs("exported span data must include the inbound HTTP request")
            .contains("/api/v1/widgets")
        // Lettuce's own Micrometer Tracing integration (auto-wired by Spring
        // Boot's Redis auto-configuration once a Tracer bean exists, no
        // application code) is what actually produces the "outbound cache
        // call" span the AC asks for; confirmed by inspecting a captured
        // payload during implementation, which carried `db.system=redis`
        // command spans (GET/SET/CLIENT/HELLO) alongside the HTTP span.
        // Asserting "SET" too (the cache-aside populate command on this
        // cache-miss read, per Story 2.7) ties this to an actual Redis data
        // command rather than merely the connection handshake (CLIENT/HELLO
        // also carry `db.system=redis` but never "SET"), and the app makes
        // no other outbound call whose payload could contain that token.
        assertThat(bodyAsLatin1)
            .describedAs("exported span data must include an outbound Redis cache call")
            .contains("db.system")
            .contains("redis")
            .contains("SET")
    }

    companion object {
        private val receivedRequests = CopyOnWriteArrayList<ByteArray>()

        // Started once at class-load time (before Spring's @DynamicPropertySource
        // method below runs, which in turn runs before context startup) so
        // the OTLP auto-configuration's @ConditionalOnProperty check sees a
        // real, already-listening endpoint. Never stopped: reaped with the
        // JVM, same lifecycle as IntegrationTestBase's Testcontainers.
        private val stubReceiver: HttpServer =
            HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
                createContext("/v1/traces") { exchange ->
                    receivedRequests += exchange.requestBody.readBytes()
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun otlpTracingEndpoint(registry: DynamicPropertyRegistry) {
            registry.add("management.opentelemetry.tracing.export.otlp.endpoint") {
                "http://localhost:${stubReceiver.address.port}/v1/traces"
            }
        }
    }
}
