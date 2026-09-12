package com.hl.service.service

import com.hl.service.error.NotFoundException
import com.hl.service.repository.WidgetEntity
import com.hl.service.repository.WidgetRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * Pure unit test for [WidgetService], styled like `WidgetEntityTest` and
 * `GlobalExceptionHandlerTest`: no Spring context, no database. Storage is a
 * hand-written in-memory fake [WidgetRepository] backed by a
 * `LinkedHashMap`, implementing only the methods [WidgetService] actually
 * calls.
 */
class WidgetServiceTest {
    /**
     * Implements only `save`, `findById`, `existsById`, `deleteById`, and
     * `findAll(Pageable)` -- the subset [WidgetService] calls. Every other
     * `JpaRepository` method throws [NotImplementedError] since nothing
     * under test invokes it.
     */
    private class FakeWidgetRepository : WidgetRepository {
        private val store = LinkedHashMap<UUID, WidgetEntity>()

        override fun <S : WidgetEntity> save(entity: S): S {
            store[entity.id] = entity
            return entity
        }

        override fun findById(id: UUID): java.util.Optional<WidgetEntity> = java.util.Optional.ofNullable(store[id])

        override fun existsById(id: UUID): Boolean = store.containsKey(id)

        override fun deleteById(id: UUID) {
            store.remove(id)
        }

        // Ignores Pageable.sort -- always returns insertion order; a fake-only
        // limitation, since nothing under test exercises sorting.
        override fun findAll(pageable: Pageable): Page<WidgetEntity> {
            val all = store.values.toList()
            val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(all.size)
            val end = (start + pageable.pageSize).coerceAtMost(all.size)
            return PageImpl(all.subList(start, end), pageable, all.size.toLong())
        }

        override fun <S : WidgetEntity> saveAll(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()

        override fun flush(): Unit = throw NotImplementedError()

        override fun <S : WidgetEntity> saveAndFlush(entity: S): S = throw NotImplementedError()

        override fun <S : WidgetEntity> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = throw NotImplementedError()

        override fun deleteAllInBatch(entities: MutableIterable<WidgetEntity>): Unit = throw NotImplementedError()

        override fun deleteAllByIdInBatch(ids: MutableIterable<UUID>): Unit = throw NotImplementedError()

        override fun deleteAllInBatch(): Unit = throw NotImplementedError()

        @Deprecated("Deprecated in Java")
        override fun getOne(id: UUID): WidgetEntity = throw NotImplementedError()

        @Deprecated("Deprecated in Java")
        override fun getById(id: UUID): WidgetEntity = throw NotImplementedError()

        override fun getReferenceById(id: UUID): WidgetEntity = throw NotImplementedError()

        override fun <S : WidgetEntity> findAll(example: org.springframework.data.domain.Example<S>): MutableList<S> =
            throw NotImplementedError()

        override fun <S : WidgetEntity> findAll(
            example: org.springframework.data.domain.Example<S>,
            sort: org.springframework.data.domain.Sort,
        ): MutableList<S> = throw NotImplementedError()

        override fun <S : WidgetEntity> findAll(
            example: org.springframework.data.domain.Example<S>,
            pageable: Pageable,
        ): Page<S> = throw NotImplementedError()

        override fun <S : WidgetEntity> findOne(example: org.springframework.data.domain.Example<S>): java.util.Optional<S> =
            throw NotImplementedError()

        override fun <S : WidgetEntity> count(example: org.springframework.data.domain.Example<S>): Long = throw NotImplementedError()

        override fun <S : WidgetEntity> exists(example: org.springframework.data.domain.Example<S>): Boolean = throw NotImplementedError()

        override fun <S : WidgetEntity, R> findBy(
            example: org.springframework.data.domain.Example<S>,
            queryFunction: java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>,
        ): R = throw NotImplementedError()

        override fun findAll(): MutableList<WidgetEntity> = throw NotImplementedError()

        override fun findAll(sort: org.springframework.data.domain.Sort): MutableList<WidgetEntity> = throw NotImplementedError()

        override fun findAllById(ids: MutableIterable<UUID>): MutableList<WidgetEntity> = throw NotImplementedError()

        override fun count(): Long = throw NotImplementedError()

        override fun delete(entity: WidgetEntity): Unit = throw NotImplementedError()

        override fun deleteAllById(ids: MutableIterable<UUID>): Unit = throw NotImplementedError()

        override fun deleteAll(entities: MutableIterable<WidgetEntity>): Unit = throw NotImplementedError()

        override fun deleteAll(): Unit = throw NotImplementedError()
    }

    private fun service() = WidgetService(FakeWidgetRepository())

    @Test
    fun `create saves an entity with a fresh id and matching timestamps and returns the domain widget`() {
        val widget = service().create("gadget")

        assertThat(widget.name).isEqualTo("gadget")
        assertThat(widget.createdAt).isEqualTo(widget.updatedAt)
    }

    @Test
    fun `findById returns the widget matching the stored entity`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        val found = widgetService.findById(created.id)

        assertThat(found).isEqualTo(created)
    }

    @Test
    fun `findById throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().findById(id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `update replaces name, refreshes updatedAt, and leaves id and createdAt unchanged`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        val updated = widgetService.update(created.id, "new")

        assertThat(updated.id).isEqualTo(created.id)
        assertThat(updated.name).isEqualTo("new")
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(updated.updatedAt).isAfter(created.updatedAt)
    }

    @Test
    fun `update throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().update(id, "new") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `delete removes the entity from the repository`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        widgetService.delete(created.id)

        assertThatThrownBy { widgetService.findById(created.id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `delete throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().delete(id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `list returns a Page of widgets mapped from the repository`() {
        val widgetService = service()
        val first = widgetService.create("first")
        val second = widgetService.create("second")

        val page = widgetService.list(PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(first, second)
    }

    @Test
    fun `list on an empty repository returns an empty Page`() {
        val page = service().list(PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(0)
        assertThat(page.content).isEmpty()
    }

    @Test
    fun `list returns the correct second-page slice`() {
        val widgetService = service()
        widgetService.create("first")
        val second = widgetService.create("second")

        val page = widgetService.list(PageRequest.of(1, 1))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(second)
    }
}
