package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
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
import io.qalipsis.core.head.model.SecurityConfiguration
import io.qalipsis.core.head.model.SecurityStrategy
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
                transform("body") { it.body() }.isNotNull().all {
                    prop(SecurityConfiguration::strategy).isEqualTo(SecurityStrategy.DISABLED)
                }
            }
        }
    }
}