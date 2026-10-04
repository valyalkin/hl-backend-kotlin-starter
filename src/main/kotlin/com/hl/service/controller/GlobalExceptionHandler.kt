package com.hl.service.controller

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.error.SystemException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Sole producer of error response bodies (AD-11). Renders every exception it
 * sees -- the three `AppException` subtypes below, `RedisConnectionFailureException`
 * (mapped to the same shape as `SystemException`), plus any unanticipated
 * exception -- as an RFC 7807 `application/problem+json` body with `code`,
 * `traceId`, and `details` extension members.
 *
 * Extends [ResponseEntityExceptionHandler] so MVC framework-raised
 * exceptions (malformed body, non-UUID path segment, unsupported media type,
 * unknown path, etc.) get the same `code`/`traceId` treatment: the
 * superclass's own handlers still choose the status and `ProblemDetail`, and
 * [handleExceptionInternal] adds the extension members. Their `code` is
 * derived from the status: 404 `NOT_FOUND`, other 4xx `BUSINESS_ERROR`,
 * 5xx `SYSTEM_ERROR` (the AD-11 type-level codes); validation failures keep
 * `VALIDATION_ERROR` via [handleMethodArgumentNotValid].
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(
        ex: BusinessException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = respond(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", ex, ex.details, request)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(
        ex: NotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex, ex.details, request)

    @ExceptionHandler(SystemException::class)
    fun handleSystem(
        ex: SystemException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = respondAsSystemFailure(ex, request, ex.details)

    // Not caught by handleUnexpected below despite these Redis exceptions
    // not being AppException subtypes: Spring's ExceptionHandlerMethodResolver
    // picks the most specific declared exception type for the thrown exception
    // (ExceptionDepthComparator), independent of where either method is declared
    // in this class, so this handler always wins over handleUnexpected's
    // `Exception::class` for these exception types.

    /**
     * A concurrent update to the same row lost the optimistic-lock race
     * (`WidgetEntity.version`): 409 with the `BUSINESS_ERROR` code, retryable
     * by the client re-reading and re-submitting.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleConcurrentUpdate(
        ex: ObjectOptimisticLockingFailureException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        respond(
            HttpStatus.CONFLICT,
            "BUSINESS_ERROR",
            IllegalStateException("The resource was modified concurrently; re-read it and retry"),
            emptyMap(),
            request,
        )

    @ExceptionHandler(
        RedisConnectionFailureException::class,
        RedisSystemException::class,
        QueryTimeoutException::class,
        SerializationException::class,
    )
    fun handleRedisFailure(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<Any> = respondAsSystemFailure(ex, request)

    /**
     * Shared by [handleSystem] and [handleRedisFailure]: a Redis
     * failure (connection, timeout, serialization) is a system failure, not just the `SystemException`
     * class itself (epic's documented "propagates Redis failures as
     * SystemException" contract, Story 2.8, AD-13), so both render identically
     * -- same 500/SYSTEM_ERROR shape, same server-side error log correlated by
     * traceId -- via this one implementation, so they cannot silently drift
     * apart.
     */
    private fun respondAsSystemFailure(
        ex: Throwable,
        request: HttpServletRequest,
        details: Map<String, Any?> = emptyMap(),
    ): ResponseEntity<Any> {
        val traceId = currentTraceId()
        // The exception's message and details are echoed to the client as-is
        // (no scrubbed variant); this log line is the server-side record of the
        // same failure, correlated by the same traceId.
        log.error("System failure (traceId=$traceId): ${ex.message}", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_ERROR", ex, details, request, traceId)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val traceId = currentTraceId()
        // An unanticipated exception is as much a server-side failure as a
        // SystemException; log it the same way so it leaves a trace too.
        log.error("Unexpected failure (traceId=$traceId): ${ex.message}", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", ex, emptyMap(), request, traceId)
    }

    /**
     * Renders a [MethodArgumentNotValidException] (a `@Valid`-annotated
     * request body that failed Bean Validation) in the same Problem Detail
     * shape as the five handlers above, plus an `errors` array of
     * `{field, code, message}` -- one entry per violated field. Validation is
     * orthogonal to the three [com.hl.service.error.AppException] subtypes
     * (architecture memlog decision): `VALIDATION_ERROR` is a distinct
     * top-level `code`, and each field entry's own `code` is the violated
     * constraint's simple name (e.g. `NotBlank`), never one of the four
     * exception-type codes.
     *
     * `FieldError.getCode()` returns the *last* entry of Spring's resolved
     * message-codes array (`DefaultMessageCodesResolver`'s least-specific
     * fallback), which for a Bean Validation violation is the bare constraint
     * annotation name -- not the field-qualified variant. No extra parsing
     * needed.
     */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val errors =
            ex.bindingResult.fieldErrors.map {
                FieldValidationError(it.field, it.code ?: "INVALID", it.defaultMessage ?: "")
            }
        return respond(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            ex,
            emptyMap(),
            (request as ServletWebRequest).request,
            errors = errors,
        )
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
        val problem = response?.body as? ProblemDetail ?: return response
        if (problem.properties?.containsKey("code") != true) {
            problem.setProperty("code", frameworkErrorCode(statusCode))
            problem.setProperty("traceId", currentTraceId())
        }
        return response
    }

    private fun frameworkErrorCode(status: HttpStatusCode): String =
        when {
            status.value() == HttpStatus.NOT_FOUND.value() -> "NOT_FOUND"
            status.is4xxClientError -> "BUSINESS_ERROR"
            else -> "SYSTEM_ERROR"
        }

    private fun respond(
        status: HttpStatus,
        code: String,
        ex: Throwable,
        details: Map<String, Any?>,
        request: HttpServletRequest,
        traceId: String = currentTraceId(),
        errors: List<FieldValidationError>? = null,
    ): ResponseEntity<Any> {
        // A null message (e.g. a bare `IllegalStateException()`) must never
        // surface as an empty, undiagnostic `detail`; fall back to the
        // exception's simple class name.
        val detail = ex.message ?: ex.javaClass.simpleName
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.type = URI.create("about:blank")
        // A request path outside java.net.URI's grammar must not turn error
        // rendering itself into an unhandled 500; omit `instance` instead.
        problem.instance = runCatching { URI.create(request.requestURI) }.getOrNull()
        problem.setProperty("code", code)
        problem.setProperty("traceId", traceId)
        if (details.isNotEmpty()) {
            problem.setProperty("details", details)
        }
        if (errors != null) {
            problem.setProperty("errors", errors)
        }
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private fun currentTraceId(): String = MDC.get("traceId") ?: ""

    private data class FieldValidationError(
        val field: String,
        val code: String,
        val message: String,
    )

    companion object {
        // Named `log`, not `logger`: `ResponseEntityExceptionHandler`'s
        // superclass already declares a protected `logger` field, and a
        // same-named Kotlin property here would silently shadow it in a way
        // that trips a Kotlin/Java interop bug (KT-56386).
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
