package com.hl.service.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.security.web.SecurityFilterChain

/**
 * Enabling the seam without an issuer must stop the context from starting
 * rather than fall back to allow-all (Story 5.4, AD-18). Runs without
 * containers: only the security configuration is loaded.
 */
class AuthSeamFailSafeTest {
    private val runner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServletWebSecurityAutoConfiguration::class.java))
            .withUserConfiguration(SecurityConfig::class.java)

    @Test
    fun `enabled without an issuer-uri fails startup`() {
        runner.withPropertyValues("app.auth.enabled=true").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasStackTraceContaining("issuer-uri")
        }
    }

    @Test
    fun `disabled by default installs only the permit-all chain`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context.getBeansOfType(SecurityFilterChain::class.java)).containsOnlyKeys("permitAllFilterChain")
        }
    }
}
