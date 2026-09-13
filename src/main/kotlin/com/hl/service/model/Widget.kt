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
 *
 * Implements [java.io.Serializable] because `RedisCacheConfiguration
 * .defaultCacheConfig()`'s default value serializer is JDK
 * `RedisSerializer.java()` (Story 2.7); [id]/[createdAt]/[updatedAt] are
 * already `Serializable`. Declares an explicit `serialVersionUID` so it does
 * not shift on unrelated changes (a new field, a toolchain bump) and break
 * deserialization of entries already sitting in the cache.
 */
data class Widget(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
