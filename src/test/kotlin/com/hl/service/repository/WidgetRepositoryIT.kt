package com.hl.service.repository

import com.hl.service.model.Widget
import com.hl.service.support.IntegrationTestBase
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Proves the persistence path end to end: Testcontainers starts Postgres,
 * Flyway migrates `V1__create_widgets.sql`, and a write through
 * [WidgetRepository] round-trips back to an equal [Widget] (Story 2.2).
 *
 * `@Transactional` here (not on [IntegrationTestBase]) rolls back the test's
 * own writes so the shared, JVM-wide Postgres container stays clean between
 * tests without needing a fresh container per test.
 */
@SpringBootTest
@Tag("integration")
@Transactional
class WidgetRepositoryIT(
    @Autowired val widgetRepository: WidgetRepository,
    @Autowired val entityManager: EntityManager,
) : IntegrationTestBase() {
    @Test
    fun `writes then reads a widget through the repository`() {
        val widget =
            Widget(
                id = UUID.randomUUID(),
                name = "gadget",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )

        widgetRepository.saveAndFlush(WidgetEntity.fromDomain(widget))
        // Detach the persistence context so findById below issues a real
        // SELECT against Postgres instead of returning the first-level
        // cache's managed instance.
        entityManager.clear()

        val found = widgetRepository.findById(widget.id).orElseThrow().toDomain()

        assertThat(found).isEqualTo(widget)
    }
}
