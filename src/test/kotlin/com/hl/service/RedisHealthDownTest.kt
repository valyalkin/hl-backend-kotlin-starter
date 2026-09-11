package com.hl.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Proves the readiness-gating mechanism (Story 1.6, AD-13/AD-17): with Redis
 * auto-configuration active and nothing listening on the configured host/port,
 * the `redisHealthContributor` bean must report `DOWN` rather than the context
 * failing to start or the contributor silently reporting `UP`. Lettuce connects
 * lazily, so context startup itself succeeds even though nothing is reachable —
 * this is what lets `application.yaml`'s readiness group turn `DOWN` at request
 * time instead of failing the process outright.
 *
 * Deliberately NOT a `@SpringBootTest` — mirrors `DatasourceFailFastTest`'s
 * isolation (no Tomcat, no Spring context wiring beyond the three autoconfig
 * classes under test). No `@Tag("integration")`.
 */
class RedisHealthDownTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataRedisAutoConfiguration::class.java,
                    DataRedisHealthContributorAutoConfiguration::class.java,
                    HealthContributorRegistryAutoConfiguration::class.java,
                ),
            ).withPropertyValues(
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
            )

    @Test
    fun `redis health contributor reports DOWN when nothing is listening`() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            val contributor = context.getBean("redisHealthContributor", HealthIndicator::class.java)
            val health = contributor.health()
            assertThat(health).isNotNull()
            assertThat(health!!.status).isEqualTo(Status.DOWN)
        }
    }
}
