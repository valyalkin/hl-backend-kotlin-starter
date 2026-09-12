package com.hl.service

import com.hl.service.support.IntegrationTestBase
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Tag("integration")
class StarterApplicationIT : IntegrationTestBase() {
    @Test
    fun contextLoads() {
    }
}
