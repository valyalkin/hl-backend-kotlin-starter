package com.hl.service.controller

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.error.SystemException
import org.junit.jupiter.api.Test
import org.springframework.dao.QueryTimeoutException
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * End-to-end unit test of [GlobalExceptionHandler]'s rendering logic. No
 * Spring context is booted (NFR-1's hermetic-test rule): a minimal test-only
 * `@RestController` -- one endpoint per exception kind -- is wired directly
 * into a standalone `MockMvc` alongside the handler under test, matching the
 * existing unit-test style (`WidgetEntityTest`).
 */
class GlobalExceptionHandlerTest {
    @RestController
    private class TestController {
        @GetMapping("/test/business")
        fun business(): Nothing = throw BusinessException("bad input")

        @GetMapping("/test/not-found")
        fun notFound(): Nothing = throw NotFoundException("widget missing")

        @GetMapping("/test/system")
        fun system(): Nothing = throw SystemException("db down", mapOf("cause" to "timeout"))

        @GetMapping("/test/unexpected")
        fun unexpected(): Nothing = throw IllegalStateException("boom")

        @GetMapping("/test/conflict")
        fun conflict(): Nothing = throw ObjectOptimisticLockingFailureException("Widget", java.util.UUID.randomUUID())

        @GetMapping("/test/redis-system")
        fun redisSystem(): Nothing = throw RedisSystemException("redis broke", RuntimeException("x"))

        @GetMapping("/test/redis-timeout")
        fun redisTimeout(): Nothing = throw QueryTimeoutException("slow")

        @GetMapping("/test/redis-serialization")
        fun redisSerialization(): Nothing = throw SerializationException("cannot serialize")

        @GetMapping("/test/by-id/{id}")
        fun byId(
            @PathVariable id: java.util.UUID,
        ): String = id.toString()

        @PostMapping("/test/body")
        fun body(
            @RequestBody body: Map<String, String>,
        ): String = body.toString()
    }

    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(TestController(), GlobalExceptionHandler()).build()

    @Test
    fun `renders a BusinessException as 400 with code BUSINESS_ERROR`() {
        mockMvc
            .get("/test/business")
            .andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.type") { value("about:blank") }
                jsonPath("$.instance") { value("/test/business") }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
                jsonPath("$.detail") { value("bad input") }
                jsonPath("$.traceId") { value("") }
                jsonPath("$.details") { doesNotExist() }
            }
    }

    @Test
    fun `renders a NotFoundException as 404 with code NOT_FOUND`() {
        mockMvc
            .get("/test/not-found")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("NOT_FOUND") }
                jsonPath("$.detail") { value("widget missing") }
                jsonPath("$.details") { doesNotExist() }
            }
    }

    @Test
    fun `renders a SystemException as 500 with code SYSTEM_ERROR and echoes details`() {
        mockMvc
            .get("/test/system")
            .andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("SYSTEM_ERROR") }
                jsonPath("$.detail") { value("db down") }
                jsonPath("$.details.cause") { value("timeout") }
            }
    }

    @Test
    fun `renders an unanticipated exception as 500 with code UNEXPECTED_ERROR`() {
        mockMvc
            .get("/test/unexpected")
            .andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("UNEXPECTED_ERROR") }
                jsonPath("$.detail") { value("boom") }
                jsonPath("$.details") { doesNotExist() }
            }
    }

    @Test
    fun `renders other Redis failures as 500 SYSTEM_ERROR`() {
        listOf("/test/redis-system", "/test/redis-timeout", "/test/redis-serialization").forEach { path ->
            mockMvc
                .get(path)
                .andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.code") { value("SYSTEM_ERROR") }
                }
        }
    }

    @Test
    fun `framework errors carry code and traceId too`() {
        // Non-UUID path segment: 400.
        mockMvc
            .get("/test/by-id/not-a-uuid")
            .andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
                jsonPath("$.traceId") { value("") }
            }
        // Malformed JSON body: 400.
        mockMvc
            .post("/test/body") {
                contentType = MediaType.APPLICATION_JSON
                content = "{not json"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
                jsonPath("$.traceId") { value("") }
            }
        // Missing Content-Type: 415.
        mockMvc
            .post("/test/body") { content = "{}" }
            .andExpect {
                status { isUnsupportedMediaType() }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
            }
        // Wrong method: 405.
        mockMvc
            .post("/test/by-id/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isMethodNotAllowed() }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
            }
    }

    @Test
    fun `a request path outside URI grammar still renders a problem body without instance`() {
        // Called directly: MockMvc never produces such a path, Tomcat rejects it first.
        val request =
            org.springframework.mock.web.MockHttpServletRequest("GET", "/test/bad path").apply {
                requestURI = "/test/bad path"
            }
        val ex = BusinessException("bad input")
        val response = GlobalExceptionHandler().handleBusiness(ex, request)
        org.assertj.core.api.Assertions
            .assertThat(response.statusCode.value())
            .isEqualTo(400)
        org.assertj.core.api.Assertions
            .assertThat((response.body as org.springframework.http.ProblemDetail).instance)
            .isNull()
    }

    @Test
    fun `renders a lost optimistic-lock race as 409 with a retry message`() {
        mockMvc
            .get("/test/conflict")
            .andExpect {
                status { isConflict() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
                jsonPath("$.detail") { value("The resource was modified concurrently; re-read it and retry") }
            }
    }
}
