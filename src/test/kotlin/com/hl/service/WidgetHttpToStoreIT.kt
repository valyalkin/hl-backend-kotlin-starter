package com.hl.service

import com.hl.service.dto.WidgetRequest
import com.hl.service.repository.WidgetRepository
import com.hl.service.support.IntegrationTestBase
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.UUID

/**
 * The missing "HTTP-to-store" Integration Test (Story 2.9): drives a real
 * HTTP request all the way through the controller, service, Postgres (via
 * [WidgetRepository]), and the Redis-backed cache, and asserts all three --
 * unlike `WidgetRepositoryIT` (Postgres only, repository called directly) and
 * `WidgetServiceCacheIT` (Redis only, service called directly rather than
 * over HTTP).
 *
 * Styled like `RedisDownIT`: real HTTP via `@AutoConfigureRestTestClient`'s
 * `RestTestClient` against a random port. Extends the shared
 * [IntegrationTestBase] (AD-21) rather than standing up its own containers --
 * nothing here needs to stop/restart either container mid-test.
 *
 * [widgetRepository] is a Mockito spy (`@MockitoSpyBean`) on the real
 * repository bean, matching `WidgetServiceCacheIT`'s technique: the datastore
 * call still really happens, only the *count* of calls is observed, to prove
 * the second read is served from Redis rather than reaching Postgres again.
 *
 * The created widget's name is randomized per run: `create`'s duplicate-name
 * rule (this story) makes widget names globally unique, and this test's HTTP
 * call runs on a different thread than the JUnit test method, so -- unlike
 * `WidgetRepositoryIT`/`WidgetServiceCacheIT`'s `@Transactional` rollback --
 * the row it writes to the shared, JVM-wide Postgres container is not rolled
 * back after the test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class WidgetHttpToStoreIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    @MockitoSpyBean
    lateinit var widgetRepository: WidgetRepository

    @Test
    fun `POST creates a widget that round-trips through Postgres and is served from Redis on the next read`() {
        val name = "gadget-${UUID.randomUUID()}"

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
                .jsonPath("$.name")
                .isEqualTo(name)
                .jsonPath("$.id")
                .exists()
                .returnResult()

        val id = UUID.fromString(JsonPath.read<String>(String(createResult.responseBody!!), "$.id"))

        // Asserts the persisted Postgres row directly through the repository,
        // independent of whatever the cache later reports.
        val persisted = widgetRepository.findById(id).orElseThrow()
        assertThat(persisted.name).isEqualTo(name)

        // `create` itself calls `existsByName`/`save`, not `findById`; clear
        // those invocations so the counts below reflect only the two reads.
        clearInvocations(widgetRepository)

        client
            .get()
            .uri("/api/v1/widgets/$id")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo(name)
        verify(widgetRepository, times(1)).findById(id)

        client
            .get()
            .uri("/api/v1/widgets/$id")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.name")
            .isEqualTo(name)
        // Still exactly one invocation total: the second read was served from
        // Redis, not by reaching the repository again.
        verify(widgetRepository, times(1)).findById(id)

        // This test's write is not wrapped in a rolled-back transaction (see
        // class doc), so it must clean up its own row explicitly -- unlike
        // every sibling Integration Test -- to keep the shared, JVM-wide
        // Postgres container clean for later tests.
        widgetRepository.deleteById(id)
    }
}
