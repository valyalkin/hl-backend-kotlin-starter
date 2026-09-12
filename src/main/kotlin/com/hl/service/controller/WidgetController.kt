package com.hl.service.controller

import com.hl.service.dto.PageResponse
import com.hl.service.dto.WidgetRequest
import com.hl.service.dto.WidgetResponse
import com.hl.service.service.WidgetService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

/**
 * Exposes [WidgetService]'s five operations over HTTP. Only
 * [WidgetRequest]/[WidgetResponse]/[PageResponse] ever appear in a method
 * signature here -- the domain `Widget` type never does (AD-1).
 *
 * A missing id on read/update/delete is not checked here: [WidgetService]
 * throws `NotFoundException`, which propagates to [GlobalExceptionHandler]
 * unchanged.
 */
@RestController
@RequestMapping("/api/v1/widgets")
class WidgetController(
    private val widgetService: WidgetService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: WidgetRequest,
    ): ResponseEntity<WidgetResponse> {
        val widget = widgetService.create(request.name)
        val location =
            ServletUriComponentsBuilder
                .fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(widget.id)
                .toUri()
        return ResponseEntity.created(location).body(WidgetResponse.from(widget))
    }

    @GetMapping("/{id}")
    fun read(
        @PathVariable id: UUID,
    ): WidgetResponse = WidgetResponse.from(widgetService.findById(id))

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: WidgetRequest,
    ): WidgetResponse = WidgetResponse.from(widgetService.update(id, request.name))

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        widgetService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun list(pageable: Pageable): PageResponse<WidgetResponse> = PageResponse.from(widgetService.list(pageable), WidgetResponse::from)
}
