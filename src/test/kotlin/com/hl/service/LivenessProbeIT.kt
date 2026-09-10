package com.hl.service

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.client.RestTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class LivenessProbeIT(
    @Autowired val client: RestTestClient,
) {
    @Test
    fun `liveness probe reports UP`() {
        client
            .get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("\$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `non-exposed actuator endpoint returns 404`() {
        // Guards the "no actuator exposure config was added" boundary: only `health`
        // is web-exposed by default, so `metrics` must not resolve.
        client
            .get()
            .uri("/actuator/metrics")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
