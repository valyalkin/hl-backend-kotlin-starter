package com.hl.service.service

import com.hl.service.repository.WidgetRepository
import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.annotation.Transactional

/**
 * Proves the cache-aside wiring from Story 2.7 against real Redis --
 * JDK-serialized round trip through the actual Testcontainers-backed
 * `RedisCacheManager` -- not just the annotation proxy that
 * `WidgetServiceCacheTest` exercises with an in-memory
 * `ConcurrentMapCacheManager` (Story 2.8).
 *
 * Extends the shared [IntegrationTestBase] (AD-21): reuses the JVM-wide
 * Postgres and Redis containers as-is, never stopping or restarting them.
 * [widgetRepository] is the real Spring Data JPA bean wrapped in a Mockito
 * spy (`@MockitoSpyBean`), so datastore calls still really happen -- only the
 * *count* of calls is observed, to prove whether the cache or the repository
 * served a given read.
 */
@SpringBootTest
@Tag("integration")
@Transactional
class WidgetServiceCacheIT(
    @Autowired val widgetService: WidgetService,
) : IntegrationTestBase() {
    @MockitoSpyBean
    lateinit var widgetRepository: WidgetRepository

    @Test
    fun `second read for the same id is served from Redis and does not reach the repository again`() {
        val created = widgetService.create("gadget")

        val first = widgetService.findById(created.id)
        val second = widgetService.findById(created.id)

        assertThat(first).isEqualTo(created)
        assertThat(second).isEqualTo(created)
        verify(widgetRepository, times(1)).findById(created.id)
    }

    @Test
    fun `update evicts the Redis entry so a subsequent read reaches the repository and returns the new name`() {
        val created = widgetService.create("gadget")
        widgetService.findById(created.id)
        clearInvocations(widgetRepository)

        widgetService.update(created.id, "new-name")
        // update() itself calls widgetRepository.findById directly (an
        // uncached repository call, not the cached service method) as part of
        // its own logic; one invocation here is expected and is not evidence
        // of the cache failing to evict.
        verify(widgetRepository, times(1)).findById(created.id)
        clearInvocations(widgetRepository)

        val afterUpdate = widgetService.findById(created.id)

        assertThat(afterUpdate.name).isEqualTo("new-name")
        verify(widgetRepository, times(1)).findById(created.id)
    }
}
