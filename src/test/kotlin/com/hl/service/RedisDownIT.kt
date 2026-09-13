package com.hl.service

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.util.UUID

/**
 * Proves the fail-fast, no-fallback contract when Redis is unreachable
 * (AD-13): a read-by-id must surface as a 500 `SystemException`-shaped
 * Problem Detail body, and readiness must report `DOWN` -- never a silent
 * Postgres-served fallback.
 *
 * Deliberately does NOT extend `IntegrationTestBase`: this test stops its own
 * Redis container mid-test, and Testcontainers cannot guarantee the same
 * mapped port after `.stop()`/`.start()` within one JVM run, so stopping the
 * shared, JVM-wide singleton would risk leaving every later Integration Test
 * in the suite unable to reconnect. Also, `@ServiceConnection` discovery
 * walks the whole test class hierarchy, so a subclass declaring its own Redis
 * container alongside `IntegrationTestBase`'s inherited one would register
 * two competing `RedisConnectionDetails` candidates. Instead this class
 * stands up its own, fully independent, JUnit-managed Postgres+Redis pair via
 * `@Testcontainers`/`@Container`, torn down after this class's tests finish,
 * never touching the shared singletons other Integration Tests depend on.
 *
 * A widget does not need to actually exist in Postgres first:
 * `@Cacheable(sync = true)` on `WidgetService.findById` intercepts before the
 * method body runs, so the Redis connection failure surfaces before any
 * repository/Postgres call -- any UUID, including one that doesn't exist,
 * exercises the failure path.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Tag("integration")
class RedisDownIT(
    @Autowired val client: RestTestClient,
) {
    @Test
    fun `read-by-id returns 500 SYSTEM_ERROR while Redis is down`() {
        // Idempotent: safe even if an earlier test method in this class
        // already stopped the container -- JUnit does not guarantee method
        // execution order, and each test must independently ensure Redis is
        // down rather than depend on the other having run first.
        redisContainer.stop()

        val id = UUID.randomUUID()
        client
            .get()
            .uri("/api/v1/widgets/$id")
            .exchange()
            .expectStatus()
            .isEqualTo(500)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("\$.type")
            .isEqualTo("about:blank")
            .jsonPath("\$.instance")
            .isEqualTo("/api/v1/widgets/$id")
            .jsonPath("\$.traceId")
            .exists()
            .jsonPath("\$.code")
            .isEqualTo("SYSTEM_ERROR")
    }

    @Test
    fun `readiness reports DOWN while Redis is down`() {
        // Idempotent for the same reason as the test above.
        redisContainer.stop()

        client
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus()
            .isEqualTo(503)
            .expectBody()
            .jsonPath("\$.status")
            .isEqualTo("DOWN")
    }

    companion object {
        @JvmStatic
        @Container
        @ServiceConnection
        val postgresContainer: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(readImageTag("POSTGRES_IMAGE")))

        @JvmStatic
        @Container
        @ServiceConnection(name = "redis")
        val redisContainer: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse(readImageTag("REDIS_IMAGE")))
                .withExposedPorts(6379)

        /**
         * Duplicated from `IntegrationTestBase` rather than shared: that
         * class's helper is `private`, and this class deliberately stays a
         * fully independent, self-contained Testcontainers pair rather than
         * reaching into the shared base (see class doc). Reads the same root
         * `.env` `KEY=value` line the Compose stack and README already treat
         * as the single source of truth for image versions (Story 1.4).
         */
        private fun readImageTag(key: String): String {
            val envFile =
                generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                    .map { File(it, ".env") }
                    .firstOrNull { it.isFile }
                    ?: error("Could not locate a root .env file above ${System.getProperty("user.dir")}")

            return envFile
                .readLines()
                .map { it.trim() }
                .firstOrNull { it.startsWith("$key=") }
                ?.substringAfter("=")
                ?.trim()
                ?: error("$key not found in ${envFile.absolutePath}")
        }
    }
}
