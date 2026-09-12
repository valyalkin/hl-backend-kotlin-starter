package com.hl.service.controller

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.error.SystemException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Sole producer of error response bodies (AD-11). Renders every exception it
 * sees -- the three `AppException` subtypes below, plus any unanticipated
 * exception -- as an RFC 7807 `application/problem+json` body with `code`,
 * `traceId`, and `details` extension members.
 *
 * Extends [ResponseEntityExceptionHandler] rather than a bare
 * `@RestControllerAdvice` class so a future story can override
 * `handleExceptionInternal` to give MVC framework-raised exceptions
 * (malformed body, unsupported media type, etc.) the same `code`/`traceId`/
 * `details` treatment as the four handlers below. That override does not
 * exist yet: today, a framework exception is handled by the superclass's
 * own more-specific inherited handlers and comes back as a bare
 * `ProblemDetail` with none of these extension members. Only the base-class
 * choice is in place; only the four `@ExceptionHandler` methods below --
 * which take priority over any inherited handling for their exact exception
 * types -- add `code`/`traceId`/`details`.
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
    ): ResponseEntity<Any> {
        val traceId = currentTraceId()
        // SystemException's message and details are echoed to the client as-is
        // (no scrubbed variant); this log line is the server-side record of the
        // same failure, correlated by the same traceId.
        log.error("System failure (traceId=$traceId): ${ex.message}", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM_ERROR", ex, ex.details, request, traceId)
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
     * shape as the four handlers above, plus an `errors` array of
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
        problem.instance = URI.create(request.requestURI)
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
