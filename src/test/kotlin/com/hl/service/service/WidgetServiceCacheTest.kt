package com.hl.service.service

import com.hl.service.error.NotFoundException
import com.hl.service.repository.WidgetEntity
import com.hl.service.repository.WidgetRepository
import com.hl.service.support.FakeWidgetRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Optional
import java.util.UUID

/**
 * Proves the cache-aside annotation wiring on [WidgetService] (Story 2.7)
 * under a real Spring AOP caching proxy, styled like `RedisHealthDownTest`'s
 * `ApplicationContextRunner` usage.
 *
 * Uses an in-memory [ConcurrentMapCacheManager], not a real Redis, because
 * only the *annotation wiring* (proxy applies, key matches, eviction fires)
 * is under test here -- `@EnableCaching` plus a `CacheManager` bean is enough
 * for that. Real Redis behavior (TTL expiry, the down-Redis failure mode) is
 * Story 2.8's dedicated Testcontainers-backed Integration Test, kept out of
 * this hermetic Unit Test (NFR-1).
 */
class WidgetServiceCacheTest {
    /**
     * Wraps the shared [FakeWidgetRepository], counting calls to `findById`
     * so tests can assert whether the cache -- not the repository -- served
     * a given read. Every other method is left to the delegate as-is (the
     * fake itself is reused unmodified, per the spec).
     */
    private class CountingWidgetRepository(
        private val delegate: FakeWidgetRepository = FakeWidgetRepository(),
    ) : WidgetRepository by delegate {
        var findByIdCallCount: Int = 0
            private set

        override fun findById(id: UUID): Optional<WidgetEntity> {
            findByIdCallCount++
            return delegate.findById(id)
        }
    }

    @Configuration
    @EnableCaching
    private class CacheTestConfig {
        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager("widgets")

        @Bean
        fun countingWidgetRepository(): CountingWidgetRepository = CountingWidgetRepository()

        @Bean
        fun widgetService(countingWidgetRepository: CountingWidgetRepository): WidgetService = WidgetService(countingWidgetRepository)
    }

    private val contextRunner = ApplicationContextRunner().withUserConfiguration(CacheTestConfig::class.java)

    @Test
    fun `second findById for the same id is served from the cache and does not reach the repository again`() {
        contextRunner.run { context ->
            val widgetService = context.getBean(WidgetService::class.java)
            val repository = context.getBean(CountingWidgetRepository::class.java)
            val created = widgetService.create("gadget")

            widgetService.findById(created.id)
            widgetService.findById(created.id)

            assertThat(repository.findByIdCallCount).isEqualTo(1)
        }
    }

    @Test
    fun `update evicts the cached entry so a subsequent read returns the new name`() {
        contextRunner.run { context ->
            val widgetService = context.getBean(WidgetService::class.java)
            val repository = context.getBean(CountingWidgetRepository::class.java)
            val created = widgetService.create("gadget")
            widgetService.findById(created.id)

            widgetService.update(created.id, "new")
            // update() itself calls widgetRepository.findById internally (a
            // direct repository call, not the cached service method), so the
            // counter already moved by the time the cache is checked again --
            // capture it here rather than asserting a specific total.
            val countAfterUpdate = repository.findByIdCallCount
            val afterUpdate = widgetService.findById(created.id)

            assertThat(afterUpdate.name).isEqualTo("new")
            assertThat(repository.findByIdCallCount)
                .describedAs("the evicted cache must let the third read reach the repository again")
                .isGreaterThan(countAfterUpdate)
        }
    }

    @Test
    fun `evicting one id leaves another id's cached entry untouched`() {
        contextRunner.run { context ->
            val widgetService = context.getBean(WidgetService::class.java)
            val repository = context.getBean(CountingWidgetRepository::class.java)
            val first = widgetService.create("first")
            val second = widgetService.create("second")
            widgetService.findById(first.id)
            widgetService.findById(second.id)

            widgetService.update(first.id, "updated")
            // update() itself calls widgetRepository.findById internally, so
            // capture the count right after it rather than asserting a total.
            val countAfterUpdate = repository.findByIdCallCount

            widgetService.findById(second.id)
            assertThat(repository.findByIdCallCount)
                .describedAs("second id's cache entry must survive evicting only the first id's key")
                .isEqualTo(countAfterUpdate)

            widgetService.findById(first.id)
            assertThat(repository.findByIdCallCount)
                .describedAs("first id's cache entry must have been evicted by its own key")
                .isGreaterThan(countAfterUpdate)
        }
    }

    @Test
    fun `delete evicts the cached entry so a subsequent read throws NotFoundException`() {
        contextRunner.run { context ->
            val widgetService = context.getBean(WidgetService::class.java)
            val repository = context.getBean(CountingWidgetRepository::class.java)
            val created = widgetService.create("gadget")
            widgetService.findById(created.id)

            widgetService.delete(created.id)

            assertThatThrownBy { widgetService.findById(created.id) }
                .isInstanceOf(NotFoundException::class.java)
            assertThat(repository.findByIdCallCount).isEqualTo(2)
        }
    }
}
