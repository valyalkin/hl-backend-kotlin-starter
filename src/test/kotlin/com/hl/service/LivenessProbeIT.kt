package com.hl.service

import com.hl.service.support.IntegrationTestBase
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
) : IntegrationTestBase() {
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
    fun `readiness probe reports UP`() {
        // Guards the readiness group's db/redis contributors: if the Redis
        // @ServiceConnection wiring (or the Postgres one) were broken, this
        // group would report DOWN instead of UP.
        client
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("\$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `non-exposed actuator endpoint returns 404`() {
        // Guards the actuator exposure boundary: only `health,info,prometheus`
        // are web-exposed (Story 3.1), so neither `metrics` nor `env` must
        // resolve.
        client
            .get()
            .uri("/actuator/metrics")
            .exchange()
            .expectStatus()
            .isNotFound
        client
            .get()
            .uri("/actuator/env")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
