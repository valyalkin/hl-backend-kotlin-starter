package com.hl.service.controller

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.error.SystemException
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
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
}
