package io.qalipsis.core.head.web


import assertk.all
import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
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
import io.qalipsis.core.head.admin.TenantManagement
import io.qalipsis.core.head.model.Tenant
import io.qalipsis.core.head.model.TenantCreation
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

@WithMockk
@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.VOLATILE, ExecutionEnvironments.SINGLE_HEAD])
internal class TenantControllerIntegrationTest {

    @Inject
    @field:Client("/api/admin/tenants")
    lateinit var httpClient: HttpClient

    @RelaxedMockK
    private lateinit var tenantManagement: TenantManagement

    @MockBean(TenantManagement::class)
    fun tenantManagement() = tenantManagement

    @Test
    fun `should return the tenant`() {
        // given
        val createdTenant = Tenant(
            displayName = "ACME Inc",
            reference = "foo",
            version = Instant.now()
        )
        coEvery { tenantManagement.saveTenant(any()) } returns createdTenant

        val tenantCreation = TenantCreation("foo")
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/", tenantCreation)

        // when
        val response = httpClient.toBlocking().exchange(
            createTenantRequest,
            Tenant::class.java
        )

        // then
        coVerifyOnce {
            tenantManagement.saveTenant(withArg {
                assertThat(it).isDataClassEqualTo(tenantCreation)
            })
        }

        assertThat(response).all {
            transform("statusCode") { it.status }.isEqualTo(HttpStatus.OK)
            transform("body") { it.body() }.isDataClassEqualTo(createdTenant)
        }
    }

    @Test
    fun `should return 400 when saving with a too long display name`() {
        // given
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/", TenantCreation("a".repeat(201)))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                createTenantRequest,
                Tenant::class.java
            )
        }

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

    @Test
    fun `should return 400 when saving with an empty display name`() {
        // given
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/", TenantCreation(""))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                createTenantRequest,
                Tenant::class.java
            )
        }

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

    @Test
    fun `should return 400 when saving with a blank display name`() {
        // given
        val createTenantRequest: HttpRequest<*> = HttpRequest.POST("/", TenantCreation("   "))

        // when
        val response = assertThrows<HttpClientResponseException> {
            httpClient.toBlocking().exchange(
                createTenantRequest,
                Tenant::class.java
            )
        }

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
    }

}

