package com.hl.service.repository

import com.hl.service.model.Widget
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * JPA mapping for the `widgets` table. Kept separate from [Widget] so the
 * domain layer never depends on persistence types (AD-1).
 *
 * The build has no `kotlin-jpa` (all-open) plugin, so every constructor
 * parameter carries a default and the constructor is `@JvmOverloads`: this
 * gives Hibernate the no-arg constructor JPA requires while still letting
 * callers use the primary constructor directly. Properties are `var` so
 * Hibernate's field-access strategy can populate them by reflection.
 *
 * [version] is the optimistic-lock column: `null` for a new entity (so Spring
 * Data persists it rather than merging), then managed by Hibernate. It stays
 * on the entity and is not exposed through [Widget] or the API; the guarantee
 * is that two concurrent read-modify-write updates cannot both succeed.
 */
@Entity
@Table(name = "widgets")
class WidgetEntity
    @JvmOverloads
    constructor(
        @Id
        @Column(name = "id", nullable = false, updatable = false)
        var id: UUID = UUID.randomUUID(),
        @Column(name = "name", nullable = false)
        var name: String = "",
        @Column(name = "created_at", nullable = false, updatable = false)
        var createdAt: Instant = Instant.now(),
        @Column(name = "updated_at", nullable = false)
        var updatedAt: Instant = Instant.now(),
        @Version
        @Column(name = "version", nullable = false)
        var version: Long? = null,
    ) {
        fun toDomain(): Widget =
            Widget(
                id = id,
                name = name,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )

        companion object {
            // Postgres timestamptz has microsecond precision; Instant has
            // nanosecond precision. Truncate here so the value we persist
            // already equals what a real round-trip through the database
            // would return.
            fun fromDomain(widget: Widget): WidgetEntity =
                WidgetEntity(
                    id = widget.id,
                    name = widget.name,
                    createdAt = widget.createdAt.truncatedTo(ChronoUnit.MICROS),
                    updatedAt = widget.updatedAt.truncatedTo(ChronoUnit.MICROS),
                )
        }
    }
