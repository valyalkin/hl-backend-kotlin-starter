package com.hl.service.repository

import com.hl.service.model.Widget
import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Two overlapping read-modify-write updates of the same widget: the second to
 * commit must fail with an optimistic-lock error instead of silently
 * overwriting the first (`WidgetEntity.version`). Not `@Transactional` itself,
 * because the point is two separate, really-committed transactions; the row
 * is deleted at the end.
 */
@SpringBootTest
@Tag("integration")
class WidgetOptimisticLockIT(
    @Autowired val repository: WidgetRepository,
    @Autowired val transactionManager: PlatformTransactionManager,
) : IntegrationTestBase() {
    private val tx = TransactionTemplate(transactionManager)

    @Test
    fun `second concurrent update of the same widget is rejected`() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        tx.executeWithoutResult {
            repository.saveAndFlush(
                WidgetEntity.fromDomain(Widget(id = id, name = "lock-${UUID.randomUUID()}", createdAt = now, updatedAt = now)),
            )
        }
        try {
            // T1 reads the widget, then T2 updates and commits underneath it.
            val staleName = "first-${UUID.randomUUID()}"
            val staleEntity = tx.execute { repository.findById(id).orElseThrow() }!!
            assertThat(staleEntity.version).isEqualTo(0L)

            tx.executeWithoutResult {
                val fresh = repository.findById(id).orElseThrow()
                fresh.name = "second-${UUID.randomUUID()}"
                repository.saveAndFlush(fresh)
            }

            // T1 now writes its stale copy: version 0 no longer matches.
            assertThatThrownBy {
                tx.executeWithoutResult {
                    staleEntity.name = staleName
                    repository.saveAndFlush(staleEntity)
                }
            }.isInstanceOf(ObjectOptimisticLockingFailureException::class.java)

            assertThat(tx.execute { repository.findById(id).orElseThrow().version }).isEqualTo(1L)
        } finally {
            repository.deleteById(id)
        }
    }
}
