package com.hl.service.dto

import com.hl.service.model.Widget
import java.time.Instant
import java.util.UUID

/**
 * Wire representation of a widget returned to clients. The domain [Widget]
 * itself is never serialized directly (AD-1); this is the sole mapping point.
 */
data class WidgetResponse(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(widget: Widget): WidgetResponse =
            WidgetResponse(
                id = widget.id,
                name = widget.name,
                createdAt = widget.createdAt,
                updatedAt = widget.updatedAt,
            )
    }
}
