package io.qalipsis.core.head.web

import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
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
import io.qalipsis.core.campaigns.ScenarioSummary
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreationRequest
import io.qalipsis.core.head.security.Permissions
import io.qalipsis.core.head.security.TenantManagement
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD, "jwt"])
@PropertySource(Property(name = "micronaut.server.log-handled-exceptions", value = "true"))
internal class TenantControllerIntegrationTest {

    @Inject
    @field:Client("/tenants")
    private lateinit var httpClient: HttpClient

    @Inject
    private lateinit var jwtGenerator: JwtGenerator

    @RelaxedMockK
    private lateinit var tenantManagement: TenantManagement

    @MockBean(TenantManagement::class)
    fun tenantManagement() = tenantManagement

    @Test
    fun `should create a tenant`() {
        // given
        val createdTenant = Tenant(
            displayName = "ACME Inc",
            reference = "foo",
            version = Instant.now()
        )
        coEvery { tenantManagement.create(any()) } returns createdTenant

        val tenantCreationRequest = TenantCreationRequest("foo", "ACME Inc")
        val createTenantRequest: HttpRequest<*> =
            HttpRequest.POST("/", tenantCreationRequest)
                .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.WRITE_TENANT)))

        // when
        val response = httpClient.toBlocking().exchange(
            createTenantRequest,
            Tenant::class.java
        )

        // then

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdTenant)
        }
        coVerifyOnce {
            tenantManagement.create(tenantCreationRequest)
        }
    }

    @Test
    fun `should deny an invalid request`() {
        // given
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/", TenantCreationRequest(null, ""))
            .bearerAuth(jwtGenerator.generateValidToken("my-user", listOf(Permissions.WRITE_TENANT)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                createTenantRequest,
                Tenant::class.java
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.BAD_REQUEST)
        coVerifyNever { tenantManagement.create(any()) }
    }

    @Test
    fun `should deny creating the tenant when the permission is missing`() {
        // given
        val getAllScenariosRequest = HttpRequest.GET<List<ScenarioSummary>>("/scenarios")
            .header("X-Tenant", "my-tenant")
            .bearerAuth(jwtGenerator.generateValidToken("the-admin"))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                getAllScenariosRequest,
                Argument.listOf(ScenarioSummary::class.java)
            )
        }

        // then
        assertThat(response).transform("statusCode") { it.status }.isEqualTo(HttpStatus.FORBIDDEN)
        coVerifyNever { tenantManagement.create(any()) }
    }

}