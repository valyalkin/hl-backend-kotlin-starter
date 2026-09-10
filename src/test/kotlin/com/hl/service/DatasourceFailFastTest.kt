package com.hl.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Proves the "fail loud, no degraded mode" contract (Story AC-3, AD-15). The
 * mechanism `application.yaml` relies on is an unresolved `${SPRING_DATASOURCE_URL}`
 * placeholder: with the datasource auto-configuration active and that env var
 * unset, context startup must abort with a message that names the missing
 * placeholder rather than silently starting without persistence.
 *
 * Assumes `SPRING_DATASOURCE_URL` is not set in the ambient environment (it is not
 * in the build). Deliberately NOT a `@SpringBootTest` — it needs no Tomcat and no
 * database, and is therefore unaffected by the test-scope auto-configuration
 * excludes in `src/test/resources/application.yaml`. No `@Tag("integration")`.
 */
class DatasourceFailFastTest {
    private val contextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration::class.java))
            .withPropertyValues("spring.datasource.url=\${SPRING_DATASOURCE_URL}")

    @Test
    fun `startup fails when the SPRING_DATASOURCE_URL placeholder cannot be resolved`() {
        contextRunner.run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure)
                .isNotNull()
                .hasStackTraceContaining("SPRING_DATASOURCE_URL")
        }
    }
}
