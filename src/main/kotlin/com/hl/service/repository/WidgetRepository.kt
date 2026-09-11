package com.hl.service.repository

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Spring Data repository for [WidgetEntity]; later stories inject this directly. */
interface WidgetRepository : JpaRepository<WidgetEntity, UUID>
