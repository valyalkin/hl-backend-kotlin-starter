package com.hl.service

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
 * Proves springdoc's auto-configuration serves `/v3/api-docs` on every
 * profile from existing Spring request-mapping metadata alone -- no
 * hand-written `OpenApiCustomizer`/`GroupedOpenApi` bean or `@Operation`
 * annotations (Story 2.10) -- and that Swagger UI itself stays off on the
 * default (non-`local`) profile via `springdoc.swagger-ui.enabled: false`.
 *
 * Styled like `WidgetHttpToStoreIT`/`RedisDownIT`: real HTTP via
 * `@AutoConfigureRestTestClient`'s `RestTestClient` against a random port.
 * Extends the shared [IntegrationTestBase] (AD-21) since nothing here needs
 * dedicated containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class OpenApiIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    @Test
    fun `GET v3 api-docs returns 200 with widgets path reflected on the default profile`() {
        val result =
            client
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .returnResult()

        val body = result.responseBody?.let { String(it) } ?: error("empty /v3/api-docs body")
        val paths = JsonPath.read<Map<String, Any>>(body, "$.paths")
        assertThat(paths).containsKey("/api/v1/widgets")
    }

    @Test
    fun `GET swagger-ui index returns 404 on the default profile`() {
        client
            .get()
            .uri("/swagger-ui/index.html")
            .exchange()
            .expectStatus()
            .isNotFound()
    }
}
