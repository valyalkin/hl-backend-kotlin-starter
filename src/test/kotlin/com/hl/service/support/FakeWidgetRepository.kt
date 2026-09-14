package com.hl.service.support

import com.hl.service.repository.WidgetEntity
import com.hl.service.repository.WidgetRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * In-memory fake [WidgetRepository] backed by a `LinkedHashMap`, implementing
 * only `save`, `findById`, `existsById`, `deleteById`, `findAll(Pageable)`,
 * `existsByNameIgnoreCase`, and `existsByNameIgnoreCaseAndIdNot` -- the subset
 * [com.hl.service.service.WidgetService] actually calls. Every other
 * `JpaRepository` method throws [NotImplementedError] since nothing under
 * test invokes it.
 *
 * Extracted from `WidgetServiceTest` so `WidgetControllerTest` can reuse it
 * without duplicating ~90 lines of `JpaRepository` stubs.
 */
class FakeWidgetRepository : WidgetRepository {
    private val store = LinkedHashMap<UUID, WidgetEntity>()

    override fun <S : WidgetEntity> save(entity: S): S {
        store[entity.id] = entity
        return entity
    }

    override fun findById(id: UUID): java.util.Optional<WidgetEntity> = java.util.Optional.ofNullable(store[id])

    override fun existsById(id: UUID): Boolean = store.containsKey(id)

    override fun existsByNameIgnoreCase(name: String): Boolean = store.values.any { it.name.equals(name, ignoreCase = true) }

    override fun existsByNameIgnoreCaseAndIdNot(
        name: String,
        id: UUID,
    ): Boolean = store.values.any { it.name.equals(name, ignoreCase = true) && it.id != id }

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
