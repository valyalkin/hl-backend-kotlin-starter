package com.hl.service.service

import com.hl.service.error.NotFoundException
import com.hl.service.model.Widget
import com.hl.service.repository.WidgetEntity
import com.hl.service.repository.WidgetRepository
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Application logic for widgets: maps between the domain [Widget] and the
 * persistence [WidgetEntity], delegating storage to [WidgetRepository].
 *
 * Only ever returns [Widget] -- never [WidgetEntity], never a DTO -- so
 * callers (a future REST controller) stay decoupled from the persistence
 * layer (AD-1).
 */
@Service
class WidgetService(
    private val widgetRepository: WidgetRepository,
) {
    @Transactional
    fun create(name: String): Widget {
        val now = Instant.now()
        val widget =
            Widget(
                name = name,
                createdAt = now,
                updatedAt = now,
            )
        return widgetRepository.save(WidgetEntity.fromDomain(widget)).toDomain()
    }

    fun findById(id: UUID): Widget =
        widgetRepository
            .findById(id)
            .orElseThrow { NotFoundException("Widget $id not found") }
            .toDomain()

    @Transactional
    fun update(
        id: UUID,
        name: String,
    ): Widget {
        val entity =
            widgetRepository
                .findById(id)
                .orElseThrow { NotFoundException("Widget $id not found") }
        entity.name = name
        entity.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        return widgetRepository.save(entity).toDomain()
    }

    @Transactional
    fun delete(id: UUID) {
        if (!widgetRepository.existsById(id)) {
            throw NotFoundException("Widget $id not found")
        }
        try {
            widgetRepository.deleteById(id)
        } catch (e: EmptyResultDataAccessException) {
            throw NotFoundException("Widget $id not found")
        }
    }

    fun list(pageable: Pageable): Page<Widget> = widgetRepository.findAll(pageable).map { it.toDomain() }
}
