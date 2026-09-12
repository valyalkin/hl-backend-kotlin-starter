package com.hl.service.dto

/**
 * Wire representation of a widget create/update body.
 *
 * No Bean Validation annotations here yet -- Story 2.6 owns request
 * validation.
 */
data class WidgetRequest(
    val name: String,
)
