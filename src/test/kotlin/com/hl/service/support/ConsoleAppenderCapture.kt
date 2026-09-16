package com.hl.service.support

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream

/**
 * Extracted from `StructuredLoggingIT` so `StructuredLoggingLocalProfileIT`
 * can reuse the same "swap the CONSOLE appender's stream" technique without
 * a hidden cross-file dependency on another test class's internals (Story
 * 3.3 review).
 *
 * Captures the real bytes Logback's "CONSOLE" appender writes -- swapping
 * its `OutputStream` for the duration of one request, then restoring it --
 * rather than redirecting `System.out` or attaching a `ListAppender`: Boot's
 * `ConsoleAppender` resolves `System.out` once, at application startup (well
 * before any test runs), so a test-time `System.setOut` would never reach
 * it, and a `ListAppender` would see the raw `ILoggingEvent`, not what
 * `logging.structured.format.console` actually encodes to the console. This
 * exercises the identical encoder path a real deployment's stdout does.
 *
 * Assumes sequential test execution (this module configures no parallel
 * test execution): mutating this JVM-wide singleton's `outputStream` field
 * is not safe against a concurrent writer on another thread.
 */
@Suppress("UNCHECKED_CAST")
fun consoleAppender(): OutputStreamAppender<ILoggingEvent> {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
    val appender = rootLogger.getAppender("CONSOLE")
    check(appender is OutputStreamAppender<*>) {
        "expected the root logger's \"CONSOLE\" appender to be an OutputStreamAppender, was: $appender"
    }
    return appender as OutputStreamAppender<ILoggingEvent>
}

/** Forwards every byte to both streams so real console output is untouched. */
class TeeOutputStream(
    private val first: OutputStream,
    private val second: OutputStream,
) : OutputStream() {
    override fun write(b: Int) {
        first.write(b)
        second.write(b)
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        first.write(b, off, len)
        second.write(b, off, len)
    }

    override fun flush() {
        first.flush()
        second.flush()
    }
}
