package com.hl.service.config

import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * With the seam off (the default), exactly one explicit permit-all chain is
 * installed and nothing -- API, OpenAPI, actuator -- asks for credentials
 * (Story 5.4, AD-18).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class AuthSeamDisabledIT(
    @Autowired val client: RestTestClient,
    @Autowired val chains: List<SecurityFilterChain>,
) : IntegrationTestBase() {
    @Test
    fun `exactly one filter chain is installed by default`() {
        assertThat(chains).hasSize(1)
    }

    @Test
    fun `every endpoint answers without a token`() {
        listOf("/api/v1/widgets", "/v3/api-docs", "/actuator/health", "/actuator/prometheus").forEach { path ->
            client
                .get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isOk()
        }
    }
}
