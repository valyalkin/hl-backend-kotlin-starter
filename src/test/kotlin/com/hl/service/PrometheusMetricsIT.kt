package com.hl.service

import com.hl.service.dto.WidgetRequest
import com.hl.service.support.IntegrationTestBase
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Proves `/actuator/prometheus` is exposed and renders Prometheus-format
 * text covering JVM, HTTP server, datasource, and cache metric families
 * (Story 3.1, AD-17) from the `micrometer-registry-prometheus` dependency
 * and widened `management.endpoints.web.exposure.include` alone -- no
 * `@Configuration` class or hand-written `MeterBinder`.
 *
 * Styled like `LivenessProbeIT`/`OpenApiIT`: real HTTP via
 * `@AutoConfigureRestTestClient`'s `RestTestClient` against a random port.
 * Extends the shared [IntegrationTestBase] (AD-21) since nothing here needs
 * dedicated containers.
 *
 * The cache metric family only appears because the "widgets" cache is
 * pre-declared via `spring.cache.cache-names: widgets` in `application.yaml`
 * -- Micrometer's cache-metrics binder only sees caches that already exist
 * on the `RedisCacheManager` at startup, not ones created lazily on first
 * access, so this test's own create/read below would not be enough on its
 * own to make `cache_gets_total` appear if that property were removed. The
 * create/read is still exercised so the cache actually has non-zero
 * get/hit/miss counters to assert on, rather than a metric family that
 * exists but is permanently empty.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class PrometheusMetricsIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    @Test
    fun `GET actuator prometheus returns 200 with JVM, HTTP server, datasource, and cache metric families`() {
        // Warms the "widgets" cache-aside read path (Story 2.7) so the
        // cache_* metric family -- present only because of the cache-names
        // pre-declaration in application.yaml -- has non-zero counters to
        // assert on, rather than existing but empty.
        val name = "prometheus-metrics-${System.nanoTime()}"
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

        val result =
            client
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.valueOf("text/plain"))
                .expectBody()
                .returnResult()

        val body = result.responseBody?.let { String(it) } ?: error("empty /actuator/prometheus body")
        assertThat(body).contains("jvm_memory_used_bytes")
        assertThat(body).contains("http_server_requests_seconds_count")
        assertThat(body).contains("hikaricp_connections_active")
        assertThat(body).contains("cache_gets_total")
    }

    @Test
    fun `GET actuator info returns 200`() {
        client
            .get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    fun `GET actuator health returns 200 unchanged by the widened exposure`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("\$.status")
            .isEqualTo("UP")
    }
}
