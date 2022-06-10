package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.OpenIdSecurityConfiguration
import io.qalipsis.core.head.model.SecurityConfiguration
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(
    Property(name = "micronaut.server.log-handled-exceptions", value = "true"),
    Property(name = "micronaut.security.enabled", value = "true"),
    Property(name = "micronaut.security.token.jwt.enabled", value = "true")
)
internal class OpenIdConfigurationControllerIntegrationTest {

    @Inject
    @field:Client("/configuration")
    lateinit var httpClient: HttpClient

    @Inject
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should successfully retrieve the openId security configuration`() {
        // when
        val securityConfigurationRequest = HttpRequest.GET<SecurityConfiguration>("/security")

        val response = httpClient.toBlocking().exchange(
            securityConfigurationRequest,
            OpenIdSecurityConfiguration::class.java
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.all {
                prop(SecurityConfiguration::strategyName).isEqualTo("openId")
                prop(SecurityConfiguration::logoutEndpoint).isEqualTo("https://qalipsis-dev.eu.auth0.com/v2/logout")
                prop(SecurityConfiguration::loginEndpoint).isEqualTo("https://qalipsis-dev.eu.auth0.com/v2/login")
            }
        }
    }
}