package com.hl.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hl.service.dto.WidgetRequest
import com.hl.service.support.IntegrationTestBase
import com.hl.service.support.SlowWidgetRepositoryConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.bean.override.BeanOverrideTestExecutionListener
import org.springframework.test.context.event.ApplicationEventsTestExecutionListener
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener
import org.springframework.test.context.support.DirtiesContextTestExecutionListener
import org.springframework.test.context.transaction.TransactionalTestExecutionListener
import org.springframework.test.context.web.ServletTestExecutionListener
import org.springframework.test.web.servlet.client.RestTestClient
import java.io.IOException
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Proves Story 3.4's graceful-shutdown contract (`server.shutdown: graceful`,
 * `src/main/resources/application.yaml`, mirrored in
 * `src/test/resources/application.yaml`, AD-17) against the real lifecycle
 * event a SIGTERM triggers on the embedded server -- `ApplicationContext.close()`
 * -- without reproducing an actual OS signal (that end-to-end timing check is
 * Epic 4's documented local Kubernetes-contract check, not this test's job).
 *
 * [SlowWidgetRepositoryConfig] holds a `GET /api/v1/widgets/{id}` request in
 * flight on a background thread by sleeping inside `findById`; the main test
 * thread waits on its `findByIdEntered` latch so it starts closing the
 * context only once the request has genuinely reached the repository, not
 * after a guessed fixed delay. `context.close()` itself is what runs the
 * graceful-shutdown `SmartLifecycle` phase -- it blocks the calling thread
 * until every in-flight request finishes (or the phase timeout elapses), so
 * it too runs on its own background thread here, letting the main thread
 * probe for connection rejection while the close is still in progress.
 *
 * Confirmed empirically during implementation (Design Notes): once
 * `context.close()` begins, a new raw `java.net.Socket` connection attempt
 * fails immediately (connection refused) -- the embedded server stops
 * accepting new connections up front, well before the in-flight request's
 * sleep elapses -- while the in-flight request itself still completes
 * normally with its real response body.
 *
 * `@TestExecutionListeners` replaces the default listener list, dropping
 * exactly `MicrometerObservationRegistryTestExecutionListener` (package-private,
 * cannot even be named here), `CommonCachesTestExecutionListener`,
 * `EventPublishingTestExecutionListener`, `MockitoResetTestExecutionListener`,
 * `MockMvcPrintOnlyOnFailureTestExecutionListener`, and
 * `WebDriverTestExecutionListener` -- every one of the latter five, in some
 * teardown phase, unconditionally calls `TestContext.getApplicationContext()`
 * to publish a lifecycle event or reset state, which throws once this test
 * has already `close()`d the context itself (confirmed empirically: Spring
 * Framework 7's `DefaultContextCache.get()` also tries to `restart()` any
 * cached-but-not-running context on every later lookup, which fails for the
 * same reason). `@DirtiesContext` below still evicts the closed context from
 * the cache afterward via `DirtiesContextTestExecutionListener`, which --
 * unlike the dropped listeners -- only calls
 * `TestContext.markApplicationContextDirty(...)`, never
 * `getApplicationContext()`, so it stays safe to keep. None of the dropped
 * listeners matter for this test: no `@MockitoBean`/Mockito annotation, no
 * MockMvc, no WebDriver, no Micrometer `TestObservationRegistry`, and this
 * class doesn't otherwise rely on `ApplicationEvents`-publishing feedback for
 * its own assertions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(SlowWidgetRepositoryConfig::class)
@TestExecutionListeners(
    mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS,
    listeners = [
        ServletTestExecutionListener::class,
        DirtiesContextBeforeModesTestExecutionListener::class,
        ApplicationEventsTestExecutionListener::class,
        BeanOverrideTestExecutionListener::class,
        DependencyInjectionTestExecutionListener::class,
        DirtiesContextTestExecutionListener::class,
        TransactionalTestExecutionListener::class,
        SqlScriptsTestExecutionListener::class,
    ],
)
@DirtiesContext
@Tag("integration")
class GracefulShutdownIT(
    @Autowired val client: RestTestClient,
    @Autowired val context: ConfigurableApplicationContext,
    @Autowired val slowWidgetRepositoryConfig: SlowWidgetRepositoryConfig,
) : IntegrationTestBase() {
    @LocalServerPort
    var port: Int = 0

    private val objectMapper = ObjectMapper()

    @Test
    fun `in-flight request completes and new connections are rejected once the context starts closing`() {
        val name = "gadget-${UUID.randomUUID()}"
        val createResult =
            client
                .post()
                .uri("/api/v1/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .body(WidgetRequest(name))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult()
        val id =
            objectMapper
                .readTree(createResult.responseBody ?: error("empty POST /api/v1/widgets body"))
                .path("id")
                .asText()

        val inFlightResponseBody = AtomicReference<String?>()
        val inFlightStatus = AtomicReference<Int?>()
        val requestThreadFailure = AtomicReference<Throwable?>()
        val requestThread =
            thread(start = true, name = "graceful-shutdown-it-in-flight-request") {
                try {
                    val result =
                        client
                            .get()
                            .uri("/api/v1/widgets/$id")
                            .exchange()
                            .returnResult()
                    inFlightStatus.set(result.status.value())
                    inFlightResponseBody.set(String(result.responseBodyContent))
                } catch (e: Throwable) {
                    requestThreadFailure.set(e)
                }
            }

        assertThat(slowWidgetRepositoryConfig.findByIdEntered.await(5, TimeUnit.SECONDS))
            .describedAs("SlowWidgetRepository.findById entered within 5s")
            .isTrue()

        // context.close() runs the graceful-shutdown phase synchronously on
        // the calling thread -- it does not return until the in-flight
        // request above finishes (or the phase times out) -- so it runs on
        // its own thread here, letting the assertions below probe the
        // server while the close is still in progress.
        val closeThreadFailure = AtomicReference<Throwable?>()
        val closeThread =
            thread(start = true, name = "graceful-shutdown-it-context-close") {
                try {
                    context.close()
                } catch (e: Throwable) {
                    closeThreadFailure.set(e)
                }
            }

        val newConnectionRejectedPromptly =
            pollUntilTrue(timeoutMillis = 5_000) { !canConnect(port) }
        assertThat(newConnectionRejectedPromptly)
            .describedAs("a new connection attempt is rejected once context.close() has started")
            .isTrue()

        closeThread.join(TimeUnit.SECONDS.toMillis(15))
        assertThat(closeThread.isAlive).describedAs("context.close() finished").isFalse()
        assertThat(closeThreadFailure.get()).describedAs("context.close() thread's uncaught exception").isNull()

        requestThread.join(TimeUnit.SECONDS.toMillis(15))
        assertThat(requestThread.isAlive).describedAs("in-flight request thread finished").isFalse()
        assertThat(requestThreadFailure.get()).describedAs("in-flight request thread's uncaught exception").isNull()
        assertThat(inFlightStatus.get()).describedAs("in-flight request's HTTP status").isEqualTo(200)
        assertThat(inFlightResponseBody.get())
            .describedAs("in-flight request's response body")
            .contains(name)
    }

    /**
     * Polled, not asserted as an instantaneous fact: a connection accepted in
     * the narrow race window right as `context.close()` starts is not
     * separately distinguished from one accepted before `close()` began --
     * this test only proves the accept queue has stopped serving new
     * connections *by the time* [pollUntilTrue] observes a rejection, an
     * accepted, bounded race in this test's own design (same spirit as
     * [com.hl.service.support.BrokenRedisConnectionConfig]'s documented
     * ephemeral-port TOCTOU note from Story 3.3).
     */
    private fun canConnect(port: Int): Boolean =
        try {
            Socket().use {
                it.connect(java.net.InetSocketAddress("localhost", port), 200)
                true
            }
        } catch (e: IOException) {
            false
        }

    private fun pollUntilTrue(
        timeoutMillis: Long,
        intervalMillis: Long = 50,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(intervalMillis)
        }
        return condition()
    }
}
