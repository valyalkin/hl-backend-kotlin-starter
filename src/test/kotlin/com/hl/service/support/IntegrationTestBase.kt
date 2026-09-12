package com.hl.service.support

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

/**
 * Shared base for every `@SpringBootTest`-based Integration Test in the
 * module. Owns one Postgres and one Redis Testcontainer, both started once
 * per JVM and never stopped explicitly -- Ryuk reaps them when the JVM exits
 * (AD-21). Extending this class is the entire contract: Integration Tests add
 * no infrastructure of their own.
 *
 * Deliberately NOT annotated `@Testcontainers`/`@Container`: that JUnit
 * extension stops `static` containers after the last test in the *class*,
 * not the JVM, which would break the JVM-shared singleton these containers
 * are meant to be. Starting them by hand in the companion `init` block, with
 * only `@ServiceConnection` on the fields, is what lets Spring wire them
 * automatically while JUnit never tries to tear them down.
 *
 * No dedicated Testcontainers Redis module exists as a Spring Boot
 * BOM-managed artifact, so Redis is a plain `GenericContainer` wired via
 * `@ServiceConnection(name = "redis")` instead.
 */
abstract class IntegrationTestBase {
    companion object {
        @JvmStatic
        @ServiceConnection
        val postgresContainer: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(readImageTag("POSTGRES_IMAGE")))

        @JvmStatic
        @ServiceConnection(name = "redis")
        val redisContainer: GenericContainer<Nothing> =
            GenericContainer<Nothing>(DockerImageName.parse(readImageTag("REDIS_IMAGE")))
                .withExposedPorts(6379)

        init {
            postgresContainer.start()
            redisContainer.start()
        }

        /**
         * Reads a `KEY=value` line from the root `.env` file -- the same file
         * the Compose stack and the README already treat as the single
         * source of truth for image versions (Story 1.4). Walks up from the
         * working directory rather than assuming it equals the repo root, so
         * this keeps working regardless of which directory the test JVM is
         * launched from.
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
