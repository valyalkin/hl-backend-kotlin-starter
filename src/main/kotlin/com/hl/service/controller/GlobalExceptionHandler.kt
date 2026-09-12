package com.hl.service.controller

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.error.SystemException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
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
    ): ResponseEntity<ProblemDetail> = respond(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", ex, ex.details, request)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(
        ex: NotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> = respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex, ex.details, request)

    @ExceptionHandler(SystemException::class)
    fun handleSystem(
        ex: SystemException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetail> {
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
    ): ResponseEntity<ProblemDetail> {
        val traceId = currentTraceId()
        // An unanticipated exception is as much a server-side failure as a
        // SystemException; log it the same way so it leaves a trace too.
        log.error("Unexpected failure (traceId=$traceId): ${ex.message}", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", ex, emptyMap(), request, traceId)
    }

    private fun respond(
        status: HttpStatus,
        code: String,
        ex: Throwable,
        details: Map<String, Any?>,
        request: HttpServletRequest,
        traceId: String = currentTraceId(),
    ): ResponseEntity<ProblemDetail> {
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
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }

    private fun currentTraceId(): String = MDC.get("traceId") ?: ""

    companion object {
        // Named `log`, not `logger`: `ResponseEntityExceptionHandler`'s
        // superclass already declares a protected `logger` field, and a
        // same-named Kotlin property here would silently shadow it in a way
        // that trips a Kotlin/Java interop bug (KT-56386).
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
