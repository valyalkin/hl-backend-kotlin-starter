package com.hl.service.support

import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.ServerSocket

/**
 * Extracted from `StructuredLoggingIT` so `StructuredLoggingLocalProfileIT`
 * can reuse it without a hidden cross-file dependency on another test
 * class's internals (Story 3.3 review).
 *
 * `@Primary`-overrides the `DataRedisConnectionDetails` bean
 * `IntegrationTestBase`'s `@ServiceConnection` Redis container otherwise
 * contributes, for the importing test class's own Spring context only, so
 * Spring Data Redis connects to a closed local port instead of the shared
 * container. This makes `WidgetService.findById`'s `@Cacheable(sync = true)`
 * lookup fail with a `RedisConnectionFailureException` before any
 * repository/Postgres call -- any UUID, including one that doesn't exist,
 * exercises the failure path -- while every other Integration Test's shared
 * context and shared Redis container stay untouched (same reasoning as
 * `RedisDownIT`, which instead stops the shared container -- not an option
 * here, since these two tests must not disturb it for every later
 * Integration Test in the suite).
 */
@TestConfiguration
class BrokenRedisConnectionConfig {
    @Bean
    @Primary
    fun redisConnectionDetails(): DataRedisConnectionDetails =
        object : DataRedisConnectionDetails {
            // A port nothing is listening on: opened then immediately closed
            // so the OS hands back a real, momentarily-free ephemeral port
            // rather than a guessed literal -- any connection attempt to it
            // fails deterministically with "connection refused". Resolved
            // once (by lazy), not on every call, so every caller sees the
            // same port.
            private val resolvedStandalone by lazy {
                DataRedisConnectionDetails.Standalone.of("localhost", ServerSocket(0).use { it.localPort })
            }

            override fun getStandalone(): DataRedisConnectionDetails.Standalone = resolvedStandalone
        }
}
