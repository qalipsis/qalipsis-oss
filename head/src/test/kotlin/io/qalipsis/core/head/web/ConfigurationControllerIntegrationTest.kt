package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DisabledSecurityConfiguration
import io.qalipsis.core.head.model.SecurityConfiguration
import io.qalipsis.core.head.model.SecurityStrategy
import io.qalipsis.core.head.security.auth0.Auth0SecurityConfiguration
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ConfigurationControllerIntegrationTest {

    @Nested
    @MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
    @PropertySource(
        Property(name = "micronaut.server.log-handled-exceptions", value = "true")
    )
    inner class DisabledConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve the disabled security configuration`() {
            // when
            val securityConfigurationRequest = HttpRequest.GET<Unit>("/security")
            val response =
                httpClient.toBlocking().exchange(securityConfigurationRequest, SecurityConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(DisabledSecurityConfiguration::class).all {
                    prop(SecurityConfiguration::strategy).isEqualTo(SecurityStrategy.DISABLED)
                }
            }
        }
    }


    @Nested
    @MicronautTest(
        environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "auth0"],
        propertySources = ["classpath:application-auth0-test.yml"]
    )
    @PropertySource(
        Property(name = "micronaut.server.log-handled-exceptions", value = "true")
    )
    inner class Auth0ConfigurationControllerIntegrationTest {

        @Inject
        @field:Client("/configuration")
        private lateinit var httpClient: HttpClient

        @Test
        fun `should successfully retrieve the disabled security configuration`() {
            // when
            val securityConfigurationRequest = HttpRequest.GET<Unit>("/security")
            val response =
                httpClient.toBlocking().exchange(securityConfigurationRequest, SecurityConfiguration::class.java)

            // then
            assertThat(response).all {
                transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
                transform("body") { it.body() }.isNotNull().isInstanceOf(Auth0SecurityConfiguration::class).all {
                    prop(Auth0SecurityConfiguration::strategy).isEqualTo(SecurityStrategy.AUTH0)
                    prop(Auth0SecurityConfiguration::clientId).isEqualTo("z8ejRvrflQfQzJkrjCR0PN1zwH79zU1n")
                    prop(Auth0SecurityConfiguration::authorizationUrl).isEqualTo("https://qalipsis-dev.eu.auth0.com/authorize")
                    prop(Auth0SecurityConfiguration::revocationUrl).isEqualTo("https://qalipsis-dev.eu.auth0.com/revoke")
                    prop(Auth0SecurityConfiguration::scopes).containsOnly(
                        "openid", "profile", "https://dev.qalipsis.io/roles", "https://dev.qalipsis.io/tenants"
                    )
                    prop(Auth0SecurityConfiguration::domain).isEqualTo("qalipsis-dev.eu.auth0.com")
                }
            }
        }
    }
}