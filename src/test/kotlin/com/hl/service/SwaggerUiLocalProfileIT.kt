package com.hl.service

import com.hl.service.support.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Proves `springdoc.swagger-ui.enabled: true` under the `local` profile
 * (`application-local.yaml`) re-enables Swagger UI's auto-configured
 * `/swagger-ui/index.html` route (Story 2.10), unlike the default profile
 * covered by `OpenApiIT`.
 *
 * Styled like `OpenApiIT`: real HTTP via `@AutoConfigureRestTestClient`'s
 * `RestTestClient` against a random port, extending the shared
 * [IntegrationTestBase] (AD-21).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("local")
@Tag("integration")
class SwaggerUiLocalProfileIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    @Test
    fun `GET swagger-ui index returns 200 under the local profile`() {
        client
            .get()
            .uri("/swagger-ui/index.html")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    fun `GET v3 api-docs returns 200 under the local profile`() {
        client
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
    }
}
