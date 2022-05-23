package io.qalipsis.core.head.security.auth0

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.web.JwtGenerator
import io.qalipsis.test.io.readResource
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Verifies the security resolution using Auth0, via the test controller [io.qalipsis.core.head.web.AuthenticatedController].
 *
 * @author Eric Jessé
 */
@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "jwt", "auth0", "auth0-test"])
internal class Auth0AuthenticationConverterSecurityRuleIntegrationTest {

    @RelaxedMockK
    private lateinit var userManagement: UserManagement

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @Inject
    @field:Client("/")
    private lateinit var httpClient: HttpClient

    @MockBean(UserManagement::class)
    fun userManagement(): UserManagement = userManagement

    @MockBean(Auth0Operations::class)
    fun auth0Operations(): Auth0Operations = relaxedMockk()

    @Test
    fun `should accept the user token and return the expected details when the endpoint is not secure`() {
        coEvery { userManagement.getUsernameFromIdentityId(any()) } returns "my-user"
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/unsecure")
        request = request.header("X-Tenant", "qalipsis-ci-test-1-JRYhP")
        request = request.bearerAuth(readResource("jwt/auth0/user-token-template-with-local-valid-key.txt"))

        val response = httpClient.toBlocking().exchange(request, CallResult::class.java)

        assertThat(response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(CallResult::tenant).isEqualTo("qalipsis-ci-test-1-JRYhP")
                prop(CallResult::name).isEqualTo("my-user")
                prop(CallResult::roles).containsOnly(*(Permissions.FOR_TESTER + Permissions.FOR_REPORTER).toTypedArray())
            }
        }
        coVerifyOnce { userManagement.getUsernameFromIdentityId("auth0|6276c3e0cc44550069a4dd6a") }
    }

    @Test
    fun `should accept the user token and return the expected details when the user has the expected permission in the tenant`() {
        coEvery { userManagement.getUsernameFromIdentityId(any()) } returns "my-user"
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/secure")
        request = request.header("X-Tenant", "qalipsis-ci-test-1-JRYhP")
        request = request.bearerAuth(readResource("jwt/auth0/user-token-template-with-local-valid-key.txt"))

        val response = httpClient.toBlocking().exchange(request, CallResult::class.java)

        assertThat(response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(CallResult::tenant).isEqualTo("qalipsis-ci-test-1-JRYhP")
                prop(CallResult::name).isEqualTo("my-user")
                prop(CallResult::roles).containsOnly(*(Permissions.FOR_TESTER + Permissions.FOR_REPORTER).toTypedArray())
            }
        }
        coVerifyOnce { userManagement.getUsernameFromIdentityId("auth0|6276c3e0cc44550069a4dd6a") }
    }

    @Test
    fun `should deny the user token when it has not the expected permission in the tenant`() {
        coEvery { userManagement.getUsernameFromIdentityId(any()) } returns "my-user"
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/secure")
        request = request.header("X-Tenant", "another-tenant")
        request = request.bearerAuth(readResource("jwt/auth0/user-token-template-with-local-valid-key.txt"))

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, CallResult::class.java)
        }

        assertThat(response.response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.FORBIDDEN)
        }
        coVerifyOnce { userManagement.getUsernameFromIdentityId("auth0|6276c3e0cc44550069a4dd6a") }
    }

    @Test
    fun `should accept the user token without call to the DB and return the expected details when the user has the expected permission in the tenant`() {
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/secure")
        request = request.header("X-Tenant", "my-tenant")
        request = request.bearerAuth(
            jwtGenerator.generateValidToken(
                "my-user",
                setOf("my-tenant:tester"),
                "nickname" to "my-user"
            )
        )

        val response = httpClient.toBlocking().exchange(request, CallResult::class.java)

        assertThat(response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(CallResult::tenant).isEqualTo("my-tenant")
                prop(CallResult::name).isEqualTo("my-user")
                prop(CallResult::roles).containsOnly(*(Permissions.FOR_TESTER).toTypedArray())
            }
        }
        confirmVerified(userManagement)
    }

    @Test
    fun `should deny the user token without call to the DB and when the permissions are missing in the tenant`() {
        var request: MutableHttpRequest<Unit> = HttpRequest.GET("/secure")
        request = request.header("X-Tenant", "my-tenant")
        request = request.bearerAuth(
            jwtGenerator.generateValidToken(
                "my-user",
                setOf("my-tenant:reporter"),
                "nickname" to "my-user"
            )
        )

        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(request, CallResult::class.java)
        }

        assertThat(response.response).all {
            transform("status") { it.status() }.isEqualTo(HttpStatus.FORBIDDEN)
        }
        confirmVerified(userManagement)
    }

    @Introspected
    data class CallResult(
        val tenant: String,
        val name: String,
        val roles: Collection<String>,
        val attributes: Map<String, Any>
    )
}