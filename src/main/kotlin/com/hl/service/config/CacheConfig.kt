package com.hl.service.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Activates Spring's caching abstraction (`@Cacheable`/`@CacheEvict`) so
 * [com.hl.service.service.WidgetService]'s cache-aside annotations take
 * effect (Story 2.7). Boot's autoconfiguration supplies the `RedisCacheManager`
 * bean from `spring.cache.*`/`spring.data.redis.*` -- no custom `CacheManager`
 * here, per the story's "no manual cache orchestration" constraint.
 */
@Configuration
// Run the cache advisor *outside* the transaction advisor (a lower order value
// is higher precedence; `@EnableTransactionManagement` defaults to
// LOWEST_PRECEDENCE). That makes a `@CacheEvict` on a `@Transactional` method
// fire after the commit, so a concurrent reader cannot repopulate the cache
// with pre-commit data between eviction and commit.
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
class CacheConfig
