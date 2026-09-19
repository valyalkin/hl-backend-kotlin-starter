package com.hl.service.support

import com.hl.service.repository.WidgetEntity
import com.hl.service.repository.WidgetRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch

/**
 * Test-only `@Primary` override of the [WidgetRepository] bean, used by
 * `GracefulShutdownIT` (Story 3.4) to hold a real `GET
 * /api/v1/widgets/{id}` request in flight long enough to call
 * `ApplicationContext.close()` against it while the request is still being
 * served -- the same request-in-flight scenario a real SIGTERM's graceful
 * drain must handle.
 *
 * [SlowWidgetRepository] wraps the real, Spring Data-generated
 * [WidgetRepository] bean via Kotlin interface delegation (`by delegate`) --
 * the same interface-override technique `FakeWidgetRepository` already uses
 * for unit tests, applied here to a real repository instead of an in-memory
 * fake. Every method except `findById` passes straight through unmodified.
 * `@Primary` on the wrapping `@Bean` method makes Spring inject this wrapper
 * everywhere [WidgetRepository] is autowired, for the importing test class's
 * own Spring context only (same `@Primary`-bean-scoped-to-one-test-context
 * technique as [BrokenRedisConnectionConfig], Story 3.3 precedent). The
 * `delegate` constructor parameter above resolves to the real,
 * Spring-Data-generated bean: Spring excludes a bean still under
 * construction from its own autowiring candidate list, so this `@Primary`
 * bean is never wired into itself.
 *
 * [findByIdEntered] counts down the instant `findById` is actually invoked
 * (i.e. once the controller -> service -> repository call chain reaches
 * Postgres), so a test can wait for the request to be genuinely in flight
 * server-side instead of guessing with a fixed `Thread.sleep` on the test
 * thread -- avoiding a source of flakiness under real CI timing variance.
 */
@TestConfiguration
class SlowWidgetRepositoryConfig {
    val findByIdEntered = CountDownLatch(1)

    @Bean
    @Primary
    fun slowWidgetRepository(delegate: WidgetRepository): WidgetRepository = SlowWidgetRepository(delegate, findByIdEntered)
}

/**
 * Sleeps for [sleepMillis] before delegating `findById`, so the caller
 * observes a request that is genuinely still being handled -- long enough
 * (a few hundred ms) to reliably call `context.close()` against it, short
 * enough to keep the test fast.
 */
private class SlowWidgetRepository(
    private val delegate: WidgetRepository,
    private val findByIdEntered: CountDownLatch,
    private val sleepMillis: Long = 1000,
) : WidgetRepository by delegate {
    override fun findById(id: UUID): Optional<WidgetEntity> {
        findByIdEntered.countDown()
        Thread.sleep(sleepMillis)
        return delegate.findById(id)
    }
}
