package com.hl.service.config

import com.hl.service.support.IntegrationTestBase
import com.hl.service.support.TestIssuer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * With `app.auth.enabled=true` the service is an OAuth2 resource server
 * validating JWTs against a throwaway local issuer: health stays open,
 * everything else needs a token with the right signature, issuer and
 * audience (Story 5.4, AD-18).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
// The suite shares one Postgres whose connection limit the cached contexts
// already come close to; this class is the only one with a distinct property
// set, so close its context (and its Hikari pool) when it finishes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthSeamEnabledIT(
    @Autowired val client: RestTestClient,
) : IntegrationTestBase() {
    companion object {
        private val testIssuer = TestIssuer()

        @JvmStatic
        @DynamicPropertySource
        fun authProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.auth.enabled") { "true" }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { testIssuer.issuer }
            registry.add("spring.security.oauth2.resourceserver.jwt.audiences") { "hl-api" }
        }

        @JvmStatic
        @AfterAll
        fun stopIssuer() = testIssuer.close()
    }

    @Test
    fun `health stays open without a token`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk()
        client
            .get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus()
            .isOk()
    }

    @Test
    fun `api openapi and other actuator endpoints require a token`() {
        listOf("/api/v1/widgets", "/v3/api-docs", "/actuator/prometheus").forEach { path ->
            client
                .get()
                .uri(path)
                .exchange()
                .expectStatus()
                .isUnauthorized()
        }
    }

    @Test
    fun `a valid token is accepted`() {
        listOf("/api/v1/widgets", "/v3/api-docs", "/actuator/prometheus").forEach { path ->
            client
                .get()
                .uri(path)
                .headers { it.setBearerAuth(testIssuer.token(audience = "hl-api")) }
                .exchange()
                .expectStatus()
                .isOk()
        }
    }

    @Test
    fun `a token for another audience is rejected`() {
        client
            .get()
            .uri("/api/v1/widgets")
            .headers { it.setBearerAuth(testIssuer.token(audience = "someone-else")) }
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    fun `a token from another issuer is rejected`() {
        client
            .get()
            .uri("/api/v1/widgets")
            .headers { it.setBearerAuth(testIssuer.token(audience = "hl-api", issuerClaim = "http://evil.example")) }
            .exchange()
            .expectStatus()
            .isUnauthorized()
    }

    @Test
    fun `a token signed with the wrong key is rejected`() {
        client
            .get()
            .uri("/api/v1/widgets")
            .headers {
                it.setBearerAuth(testIssuer.token(audience = "hl-api", signingKey = testIssuer.foreignKey()))
            }.exchange()
            .expectStatus()
            .isUnauthorized()
    }
}
