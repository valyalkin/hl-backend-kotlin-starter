package com.hl.service.model

import java.time.Instant
import java.util.UUID

/**
 * Immutable domain representation of a widget.
 *
 * The database never generates identifiers (AD-8): callers construct a
 * [Widget] with [id] already set -- defaulting to a fresh
 * [UUID.randomUUID] -- before any persistence call. This type carries no
 * persistence annotations and must never depend on anything in the
 * `repository` package.
 */
data class Widget(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
