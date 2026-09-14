package com.hl.service.repository

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [WidgetEntity]; later stories inject this directly. */
interface WidgetRepository : JpaRepository<WidgetEntity, UUID> {
    /**
     * `true` if any widget already has this name, case-insensitively; used by
     * `create`'s duplicate-name rule.
     */
    fun existsByNameIgnoreCase(name: String): Boolean

    /**
     * `true` if a widget *other than* [id] already has this name,
     * case-insensitively; used by `update`'s duplicate-name rule so renaming
     * a widget to its own current name is not flagged as a conflict.
     */
    fun existsByNameIgnoreCaseAndIdNot(
        name: String,
        id: UUID,
    ): Boolean
}
