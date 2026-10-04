package com.hl.service.config

import com.hl.service.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AbstractPointcutAdvisor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.interceptor.BeanFactoryCacheOperationSourceAdvisor
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor

/**
 * The cache advisor must wrap the transaction advisor so `@CacheEvict` on a
 * `@Transactional` method fires after the commit (a lower order value is the
 * outer advisor).
 */
@SpringBootTest
@Tag("integration")
class CacheTransactionOrderIT(
    @Autowired val cacheAdvisor: BeanFactoryCacheOperationSourceAdvisor,
    @Autowired val transactionAdvisor: BeanFactoryTransactionAttributeSourceAdvisor,
) : IntegrationTestBase() {
    @Test
    fun `cache advisor is outside the transaction advisor`() {
        assertThat((cacheAdvisor as AbstractPointcutAdvisor).order)
            .isLessThan((transactionAdvisor as AbstractPointcutAdvisor).order)
    }
}
