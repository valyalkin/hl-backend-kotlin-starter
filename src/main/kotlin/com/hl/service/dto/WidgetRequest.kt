package com.hl.service.dto

import jakarta.validation.constraints.NotBlank

/**
 * Wire representation of a widget create/update body.
 */
data class WidgetRequest(
    @field:NotBlank
    val name: String,
)
