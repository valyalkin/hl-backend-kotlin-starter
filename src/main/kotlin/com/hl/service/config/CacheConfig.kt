package com.hl.service.config

import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration

/**
 * Activates Spring's caching abstraction (`@Cacheable`/`@CacheEvict`) so
 * [com.hl.service.service.WidgetService]'s cache-aside annotations take
 * effect (Story 2.7). Boot's autoconfiguration supplies the `RedisCacheManager`
 * bean from `spring.cache.*`/`spring.data.redis.*` -- no custom `CacheManager`
 * here, per the story's "no manual cache orchestration" constraint.
 */
@Configuration
@EnableCaching
class CacheConfig
