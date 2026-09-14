package com.hl.service.controller

import com.hl.service.service.WidgetService
import com.hl.service.support.FakeWidgetRepository
import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

/**
 * End-to-end unit test of [WidgetController] over a real
 * `WidgetController(WidgetService(FakeWidgetRepository()))` plus
 * [GlobalExceptionHandler], standalone `MockMvc` -- no Spring context (styled
 * like `GlobalExceptionHandlerTest`). [PageableHandlerMethodArgumentResolver]
 * is registered explicitly since standalone setup skips the Spring Data Web
 * auto-configuration that normally supplies it.
 */
class WidgetControllerTest {
    private val widgetService = WidgetService(FakeWidgetRepository())

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(WidgetController(widgetService), GlobalExceptionHandler())
            .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
            .build()

    @Test
    fun `create returns 201 with a Location header and the created WidgetResponse`() {
        mockMvc
            .post("/api/v1/widgets") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"gadget"}"""
            }.andExpect {
                status { isCreated() }
                header { exists("Location") }
                jsonPath("$.name") { value("gadget") }
                jsonPath("$.id") { exists() }
            }
    }

    @Test
    fun `create with a blank name returns 400 Problem Detail with a NotBlank field error`() {
        mockMvc
            .post("/api/v1/widgets") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":""}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].code") { value("NotBlank") }
                jsonPath("$.errors[0].message") { exists() }
            }
    }

    @Test
    fun `create with a whitespace-only name returns 400 Problem Detail with a NotBlank field error`() {
        mockMvc
            .post("/api/v1/widgets") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"   "}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].code") { value("NotBlank") }
                jsonPath("$.errors[0].message") { exists() }
            }
    }

    @Test
    fun `create Location header points at the created widget's id`() {
        val result =
            mockMvc
                .post("/api/v1/widgets") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name":"gadget"}"""
                }.andReturn()

        val location = result.response.getHeader("Location")
        val id = JsonPath.read<String>(result.response.contentAsString, "$.id")

        assertThat(location).isEqualTo("http://localhost/api/v1/widgets/$id")
    }

    @Test
    fun `read returns 200 with the matching WidgetResponse for a known id`() {
        val created = widgetService.create("gadget")

        mockMvc
            .get("/api/v1/widgets/${created.id}")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(created.id.toString()) }
                jsonPath("$.name") { value("gadget") }
            }
    }

    @Test
    fun `read returns 404 Problem Detail for an unknown id`() {
        val id = UUID.randomUUID()

        mockMvc
            .get("/api/v1/widgets/$id")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `update returns 200 with the updated WidgetResponse for a known id`() {
        val created = widgetService.create("gadget")

        mockMvc
            .put("/api/v1/widgets/${created.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"new"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(created.id.toString()) }
                jsonPath("$.name") { value("new") }
            }
    }

    @Test
    fun `update with a blank name returns 400 Problem Detail with a NotBlank field error`() {
        val created = widgetService.create("gadget")

        mockMvc
            .put("/api/v1/widgets/${created.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":""}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].code") { value("NotBlank") }
                jsonPath("$.errors[0].message") { exists() }
            }
    }

    @Test
    fun `update with a whitespace-only name returns 400 Problem Detail with a NotBlank field error`() {
        val created = widgetService.create("gadget")

        mockMvc
            .put("/api/v1/widgets/${created.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"   "}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("VALIDATION_ERROR") }
                jsonPath("$.errors.length()") { value(1) }
                jsonPath("$.errors[0].field") { value("name") }
                jsonPath("$.errors[0].code") { value("NotBlank") }
                jsonPath("$.errors[0].message") { exists() }
            }
    }

    @Test
    fun `create with a name that already belongs to another widget returns 400 Problem Detail with BUSINESS_ERROR`() {
        widgetService.create("gadget")

        mockMvc
            .post("/api/v1/widgets") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"gadget"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
            }
    }

    @Test
    fun `update to a name already used by a different widget returns 400 Problem Detail with BUSINESS_ERROR`() {
        widgetService.create("gadget")
        val other = widgetService.create("widget")

        mockMvc
            .put("/api/v1/widgets/${other.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"gadget"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("BUSINESS_ERROR") }
            }
    }

    @Test
    fun `update to the widget's own current name returns 200, no conflict`() {
        val created = widgetService.create("gadget")

        mockMvc
            .put("/api/v1/widgets/${created.id}") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"gadget"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("gadget") }
            }
    }

    @Test
    fun `update returns 404 Problem Detail for an unknown id`() {
        val id = UUID.randomUUID()

        mockMvc
            .put("/api/v1/widgets/$id") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"new"}"""
            }.andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `delete returns 204 with an empty body for a known id`() {
        val created = widgetService.create("gadget")

        mockMvc
            .delete("/api/v1/widgets/${created.id}")
            .andExpect {
                status { isNoContent() }
                content { string("") }
            }
    }

    @Test
    fun `delete returns 404 Problem Detail for an unknown id`() {
        val id = UUID.randomUUID()

        mockMvc
            .delete("/api/v1/widgets/$id")
            .andExpect {
                status { isNotFound() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
    }

    @Test
    fun `list returns 200 with the PageResponse envelope`() {
        widgetService.create("first")
        widgetService.create("second")

        mockMvc
            .get("/api/v1/widgets?page=0&size=10")
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.page") { value(0) }
                jsonPath("$.size") { value(10) }
                jsonPath("$.totalElements") { value(2) }
                jsonPath("$.totalPages") { value(1) }
            }
    }

    @Test
    fun `list on an empty repository returns an empty items array`() {
        mockMvc
            .get("/api/v1/widgets")
            .andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(0) }
                jsonPath("$.totalElements") { value(0) }
            }
    }
}
