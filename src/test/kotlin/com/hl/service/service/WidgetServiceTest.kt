package com.hl.service.service

import com.hl.service.error.BusinessException
import com.hl.service.error.NotFoundException
import com.hl.service.support.FakeWidgetRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.util.UUID

/**
 * Pure unit test for [WidgetService], styled like `WidgetEntityTest` and
 * `GlobalExceptionHandlerTest`: no Spring context, no database. Storage is
 * the shared in-memory [FakeWidgetRepository].
 */
class WidgetServiceTest {
    private fun service() = WidgetService(FakeWidgetRepository())

    @Test
    fun `create saves an entity with a fresh id and matching timestamps and returns the domain widget`() {
        val widget = service().create("gadget")

        assertThat(widget.name).isEqualTo("gadget")
        assertThat(widget.createdAt).isEqualTo(widget.updatedAt)
    }

    @Test
    fun `findById returns the widget matching the stored entity`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        val found = widgetService.findById(created.id)

        assertThat(found).isEqualTo(created)
    }

    @Test
    fun `findById throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().findById(id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `update replaces name, refreshes updatedAt, and leaves id and createdAt unchanged`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        val updated = widgetService.update(created.id, "new")

        assertThat(updated.id).isEqualTo(created.id)
        assertThat(updated.name).isEqualTo("new")
        assertThat(updated.createdAt).isEqualTo(created.createdAt)
        assertThat(updated.updatedAt).isAfter(created.updatedAt)
    }

    @Test
    fun `update throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().update(id, "new") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `create throws BusinessException for a name that already belongs to another widget`() {
        val widgetService = service()
        widgetService.create("gadget")

        assertThatThrownBy { widgetService.create("gadget") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Widget name 'gadget' already exists")
    }

    @Test
    fun `update throws BusinessException for a name that already belongs to another widget`() {
        val widgetService = service()
        widgetService.create("gadget")
        val other = widgetService.create("widget")

        assertThatThrownBy { widgetService.update(other.id, "gadget") }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("Widget name 'gadget' already exists")
    }

    @Test
    fun `update throws NotFoundException, not BusinessException, when the id is missing even though the name also conflicts`() {
        val widgetService = service()
        widgetService.create("gadget")
        val id = UUID.randomUUID()

        assertThatThrownBy { widgetService.update(id, "gadget") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `update allows renaming a widget to its own current name`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        val updated = widgetService.update(created.id, "gadget")

        assertThat(updated.name).isEqualTo("gadget")
    }

    @Test
    fun `delete removes the entity from the repository`() {
        val widgetService = service()
        val created = widgetService.create("gadget")

        widgetService.delete(created.id)

        assertThatThrownBy { widgetService.findById(created.id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `delete throws NotFoundException for a missing id`() {
        val id = UUID.randomUUID()

        assertThatThrownBy { service().delete(id) }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessage("Widget $id not found")
    }

    @Test
    fun `list returns a Page of widgets mapped from the repository`() {
        val widgetService = service()
        val first = widgetService.create("first")
        val second = widgetService.create("second")

        val page = widgetService.list(PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(first, second)
    }

    @Test
    fun `list on an empty repository returns an empty Page`() {
        val page = service().list(PageRequest.of(0, 10))

        assertThat(page.totalElements).isEqualTo(0)
        assertThat(page.content).isEmpty()
    }

    @Test
    fun `list returns the correct second-page slice`() {
        val widgetService = service()
        widgetService.create("first")
        val second = widgetService.create("second")

        val page = widgetService.list(PageRequest.of(1, 1))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content).containsExactly(second)
    }
}
