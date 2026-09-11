package com.hl.service.repository

import com.hl.service.model.Widget
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure unit test for the hand-written mapping (Story 2.1 acceptance:
 * `WidgetEntity.fromDomain(widget).toDomain()` must equal the original
 * `Widget` on all fields). No Spring context, no database.
 */
class WidgetEntityTest {
    @Test
    fun `round-trips through fromDomain and toDomain without losing fields`() {
        val widget =
            Widget(
                id = UUID.randomUUID(),
                name = "gadget",
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            )

        val roundTripped = WidgetEntity.fromDomain(widget).toDomain()

        assertThat(roundTripped).isEqualTo(widget)
    }
}
