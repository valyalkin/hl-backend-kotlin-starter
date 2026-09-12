package com.hl.service.error

/**
 * Sealed family of exceptions the [com.hl.service.controller.GlobalExceptionHandler]
 * renders into RFC 7807 `application/problem+json` bodies (AD-11).
 *
 * Every subtype shares exactly one constructor shape -- `message` plus an
 * optional `details` map -- and carries no `code` of its own: the handler
 * alone assigns the fixed `code` literal per type. There is no
 * `ErrorCode`/`ErrorKind` catalogue.
 */
sealed class AppException(
    message: String,
    val details: Map<String, Any?> = emptyMap(),
) : RuntimeException(message)

/** Renders as HTTP 400 with `code=BUSINESS_ERROR`. */
class BusinessException(
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : AppException(message, details)

/** Renders as HTTP 404 with `code=NOT_FOUND`. */
class NotFoundException(
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : AppException(message, details)

/**
 * Renders as HTTP 500 with `code=SYSTEM_ERROR`. Unlike a generic 500, its
 * message and details are echoed to the client as-is -- no scrubbed variant
 * -- and the handler also logs it server-side at ERROR level with the same
 * trace id.
 */
class SystemException(
    message: String,
    details: Map<String, Any?> = emptyMap(),
) : AppException(message, details)
