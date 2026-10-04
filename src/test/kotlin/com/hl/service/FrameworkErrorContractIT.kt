package com.hl.service

import com.hl.service.support.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * Framework-raised errors on the real widgets API carry the same `code` and
 * `traceId` members as every other error (the deferred 2.5/2.6 gap), including
 * a body that omits the required `name` entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class FrameworkErrorContractIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    @Test
    fun `non-UUID path segment returns a problem body with code and traceId`() {
        client
            .get()
            .uri("/api/v1/widgets/not-a-uuid")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("\$.code")
            .isEqualTo("BUSINESS_ERROR")
            .jsonPath("\$.traceId")
            .isNotEmpty()
    }

    @Test
    fun `body omitting name returns a problem body with code and traceId`() {
        client
            .post()
            .uri("/api/v1/widgets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("\$.code")
            .exists()
            .jsonPath("\$.traceId")
            .isNotEmpty()
    }

    @Test
    fun `malformed JSON returns a problem body with code and traceId`() {
        client
            .post()
            .uri("/api/v1/widgets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{not json")
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody()
            .jsonPath("\$.code")
            .isEqualTo("BUSINESS_ERROR")
            .jsonPath("\$.traceId")
            .isNotEmpty()
    }

    @Test
    fun `unknown path returns a NOT_FOUND problem body`() {
        client
            .get()
            .uri("/api/v1/nothing-here")
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectBody()
            .jsonPath("\$.code")
            .isEqualTo("NOT_FOUND")
    }
}
