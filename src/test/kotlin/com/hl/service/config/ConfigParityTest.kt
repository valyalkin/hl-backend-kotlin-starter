package com.hl.service.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.FileSystemResource

/**
 * `src/test/resources/application.yaml` fully shadows the main one on the test
 * classpath, so no Integration Test ever reads the production file. This test
 * reads both files directly and requires that every key the test file mirrors
 * has the same value in production, and that the keys production relies on for
 * observability and shutdown behavior exist there at all -- a typo or deletion
 * in the main file alone would otherwise leave `./gradlew build` green.
 */
class ConfigParityTest {
    private fun load(path: String): Map<String, Any> {
        val source =
            YamlPropertySourceLoader()
                .load(path, FileSystemResource(path))
                .single() as EnumerablePropertySource<*>
        return source.propertyNames.associateWith { source.getProperty(it)!! }
    }

    private val main = load("src/main/resources/application.yaml")
    private val test = load("src/test/resources/application.yaml")

    @Test
    fun `every key mirrored in the test file has the same value in the main file`() {
        val mirrored = test.keys.filter { it in main }
        assertThat(mirrored).isNotEmpty()
        mirrored.forEach { key ->
            assertThat(main[key]).describedAs("main vs test value of $key").isEqualTo(test[key])
        }
    }

    @Test
    fun `production-critical keys exist in the main file`() {
        assertThat(main.keys).contains(
            "logging.structured.format.console",
            "server.shutdown",
            "management.endpoints.web.exposure.include",
            "management.endpoint.health.group.readiness.include",
            "spring.cache.cache-names",
            "spring.lifecycle.timeout-per-shutdown-phase",
            "app.auth.enabled",
            "server.port",
        )
        assertThat(main["logging.structured.format.console"]).isEqualTo("ecs")
    }

    @Test
    fun `every key the test file declares also exists in the main file`() {
        // Anything only the test file knows about is configuration production
        // would silently lack, unless it is deliberately test-only.
        val testOnly = setOf("spring.datasource.hikari.maximum-pool-size")
        val onlyInTest = test.keys.filter { it !in main && it !in testOnly }
        assertThat(onlyInTest).describedAs("keys declared only in the test application.yaml").isEmpty()
    }
}
