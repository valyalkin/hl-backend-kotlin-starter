package com.hl.service.dto

import org.springframework.data.domain.Page

/**
 * Generic pagination envelope (AD-10) -- built once, reused by every future
 * resource. Carries no widget-specific (or any resource-specific) fields.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T : Any, R> from(
            page: Page<T>,
            mapper: (T) -> R,
        ): PageResponse<R> =
            PageResponse(
                items = page.content.map(mapper),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
    }
}
