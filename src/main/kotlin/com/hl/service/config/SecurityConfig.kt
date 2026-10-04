package com.hl.service.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * The Auth Seam (Story 5.4, AD-18): one property, `app.auth.enabled`
 * (default `false`), selects exactly one of two [SecurityFilterChain] beans.
 *
 * Disabled installs an **explicit** permit-all chain, so having
 * `spring-security` on the classpath never silently secures anything with
 * Boot's generated password. Enabled makes the service an OAuth2 resource
 * server: a valid JWT is required everywhere except the health probes
 * (`/actuator/health` and its sub-paths), including everything under
 * `/api`, `/v3/api-docs` and every other actuator endpoint. Issuer, audience and JWKS come from Boot's own
 * `spring.security.oauth2.resourceserver.jwt.*` properties -- no custom
 * decoder code.
 *
 * Enabled without an `issuer-uri` fails context startup; it can never
 * degrade to allow-all.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    @ConditionalOnProperty(name = ["app.auth.enabled"], havingValue = "false", matchIfMissing = true)
    fun permitAllFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()

    @Bean
    @ConditionalOnProperty(name = ["app.auth.enabled"], havingValue = "true")
    fun jwtFilterChain(
        http: HttpSecurity,
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") issuerUri: String,
    ): SecurityFilterChain {
        check(issuerUri.isNotBlank()) {
            "app.auth.enabled=true requires spring.security.oauth2.resourceserver.jwt.issuer-uri " +
                "(SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI); refusing to start without it"
        }
        return http
            .csrf(AbstractHttpConfigurer<*, *>::disable)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
            .build()
    }
}
