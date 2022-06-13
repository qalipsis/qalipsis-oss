package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.model.Profile
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.TenantManagement
import io.qalipsis.core.head.security.User
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE, "jwt"])
internal class ProfileControllerIntegrationTest {

    @Inject
    @field:Client("/users")
    private lateinit var httpClient: HttpClient

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @RelaxedMockK
    private lateinit var userManagement: UserManagement

    @RelaxedMockK
    private lateinit var tenantManagement: TenantManagement

    @MockBean(UserManagement::class)
    internal fun userManagement() = userManagement

    @MockBean(TenantManagement::class)
    internal fun tenantManagement() = tenantManagement

    @Test
    fun `should retrieve the user's profile`() {
        // given
        val tenants = listOf(
            Tenant("tenant-1", "The first tenant", Instant.now()),
            Tenant("tenant-2", "The second tenant", Instant.now()),
        )
        coEvery { tenantManagement.findAll(any()) } returns tenants
        val user = User(
            tenant = Defaults.TENANT,
            username = "my-user",
            version = Instant.now(),
            creation = Instant.now(),
            displayName = "just-test",
            email = "foo+111@bar.com",
            roles = emptyList()
        )
        coEvery { userManagement.get(Defaults.TENANT, "my-user") } returns user

        // when
        val response = httpClient.toBlocking().exchange(
            HttpRequest.GET<Unit>("/profile")
                .bearerAuth(
                    jwtGenerator.generateValidToken(
                        name = "my-user",
                        roles = listOf(Permissions.READ_SCENARIO, Permissions.DELETE_USER),
                        "tenants" to setOf("tenant-1", "tenant-2")
                    )
                ),
            Profile::class.java
        )

        // then
        coVerifyOnce {
            userManagement.get(Defaults.TENANT, "my-user")
            tenantManagement.findAll(listOf("tenant-1", "tenant-2"))
        }
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull().all {
                prop(Profile::user).isDataClassEqualTo(user)
                prop(Profile::tenants).containsExactlyInAnyOrder(*tenants.toTypedArray())
            }
        }
    }

    @Test
    fun `should not retrieve the user's details when unauthenticated`() {
        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                HttpRequest.GET<Unit>("/profile"),
                Profile::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should retrieve the user's permissions`() {
        // when
        val response = httpClient.toBlocking().exchange(
            HttpRequest.GET<Unit>("/permissions")
                .header("X-Tenant", "my-tenant")
                .bearerAuth(
                    jwtGenerator.generateValidToken(
                        "my-user",
                        listOf(Permissions.READ_SCENARIO, Permissions.DELETE_USER, Permissions.AUTHENTICATED)
                    )
                ),
            Argument.setOf(String::class.java)
        )

        // then
        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isNotNull()
                .containsExactlyInAnyOrder(
                    Permissions.READ_SCENARIO,
                    Permissions.DELETE_USER,
                    Permissions.AUTHENTICATED
                )
        }
    }

    @Test
    fun `should not retrieve the user's permissions when unauthenticated`() {
        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                HttpRequest.GET<Unit>("/permissions").header("X-Tenant", "my-tenant"),
                Argument.setOf(String::class.java)
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}