package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.security.authentication.Authentication
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.web.AuthenticatedController.CallResult
import io.qalipsis.core.head.web.annotations.Tenant
import io.qalipsis.test.mockk.WithMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test

/**
 * This tests validates that endpoints using [Tenant] and [Authentication] are providing the
 * defaults values when the security is disabled.
 *
 * It uses the endpoints from [io.qalipsis.core.head.web.AuthenticatedController].
 *
 * @author Eric Jessé
 */
@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
@PropertySource(
    Property(name = "micronaut.server.log-handled-exceptions", value = "true")
)
internal class NoWebSecurityIntegrationTest {

    @Inject
    @field:Client("/")
    private lateinit var httpClient: HttpClient

    @Test
    fun `should accept the request when the endpoint is not secured but requires tenant and authentication`() {
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/unsecure")
        request = request.header("X-Tenant", "my-tenant")

        val response = httpClient.toBlocking().exchange(request, CallResult::class.java)

        assertThat(response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(CallResult::tenant).isEqualTo(Defaults.TENANT)
                prop(CallResult::name).isEqualTo(Defaults.USER)
                prop(CallResult::roles).containsAll(
                    *RoleName.values().asSequence().flatMap { it.permissions }.toSet().toTypedArray()
                )
            }
        }
    }

    @Test
    fun `should accept the request when the endpoint is secured and requires tenant and authentication`() {
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/secure")
        request = request.header("X-Tenant", "my-tenant")

        val response = httpClient.toBlocking().exchange(request, CallResult::class.java)

        assertThat(response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(CallResult::tenant).isEqualTo(Defaults.TENANT)
                prop(CallResult::name).isEqualTo(Defaults.USER)
                prop(CallResult::roles).containsAll(
                    *RoleName.values().asSequence().flatMap { it.permissions }.toSet().toTypedArray()
                )
            }
        }
    }
}